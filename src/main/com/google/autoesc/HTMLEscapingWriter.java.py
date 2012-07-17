#!python

# This generates a java source file by taking each method that has a
# parameters (String s, int off, int end) and generating a copy that
# takes (char[] s, int off, int end).

# Fix emacs syntax highlighting "

src = r"""
// Copyright (C) 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.autoesc;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

import static com.google.autoesc.Context.attr;
import static com.google.autoesc.Context.delim;
import static com.google.autoesc.Context.element;
import static com.google.autoesc.Context.jsCtx;
import static com.google.autoesc.Context.state;
import static com.google.autoesc.Context.urlPart;

/**
 * A writer that receives chunks of trusted template text via
 * {@link HTMLEscapingWriter#writeSafe}, and chunks of untrusted content via
 * {@link HTMLEscapingWriter#write} and escapes the untrusted content
 * according to the context established by the trusted portions to prevent XSS.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@NotThreadSafe
public class HTMLEscapingWriter extends Writer {
  /**
   * underlying is the writer that receives the template output ignoring any
   * additional encoding done by out.
   */
  private Writer underlying;
  /** out receives the template output. */
  private Writer out;
  private EscapingWriter htmlEscapingWriterDqOk, htmlEscapingWriterSqOk;
  /** As defined in {@link Context} */
  private int context;
  /**
   * When processing safe attribute content, we unescape the content so that
   * we can have one state machine that recognizes delimiters in both
   * {@code <script>"quoted string"</script>} and in
   * {@code <a href="&quot;quoted string&quot;">}.
   * This table is used to reverse this unescaping before content is written
   * to out.
   * If {@code null}, no transformation is done.
   */
  private @Nullable ReplacementTable rtable;
  /**
   * HACK: When stripping tags from trusted HTML content that is interpolated
   * into an attribute value, we rerun the HTML scanner, so that we can
   * emit just the non-tag content.
   */
  private boolean isStrippingTags;
  /**
   * True if in soft escaping mode.  @see #isSoft
   */
  private boolean soft;
  /**
   * Used to buffer unsafe content written via write(int).
   */
  private StringBuilder unsafeBuffered = new StringBuilder();

  public HTMLEscapingWriter(Writer out) {
    this.underlying = this.out = out;
    this.context = Context.TEXT;
  }

  /**
   * Closes the underlying writer, and raises an error if the content ends in
   * an inconsistent state -- if a full, valid HTML fragment has not been
   * written.
   */
  @Override
  public void close() throws IOException, TemplateException {
    flush();
    out.close();
    int context = this.context;
    releaseOnClose();
    if (state(context) != Context.State.Text) {
      throw new TemplateException("Incomplete document fragment ended in "
          + Context.toString(context));
    }
  }

  private void releaseOnClose() {
    this.context = -1;
    this.out = this.underlying = null;
    this.rtable = null;
    this.htmlEscapingWriterSqOk = htmlEscapingWriterDqOk = null;
    this.unsafeBuffered = null;
  }

  @Override
  public void flush() throws IOException, TemplateException {
    if (unsafeBuffered.length() != 0) {
      String s = unsafeBuffered.toString();
      unsafeBuffered.setLength(0);
      write(s);
    }
  }

  /**
   * Emits a string of content from a trusted source.  The content may be
   * normalized (all attributes are quoted, comments are elided, and stray
   * {@code '<'} will be escaped to {@code "&lt;"}) but the content will not
   * be converted from one type to another.
   *
   * @param s content from a trusted source like a trusted template author.
   */
  public void writeSafe(String s) throws IOException, TemplateException {
    writeSafe(s, 0, s.length());
  }

  /**
   * Emits a string of content from a trusted source.  The content may be
   * normalized (all attributes are quoted, comments are elided, and stray
   * {@code '<'} will be escaped to {@code "&lt;"}) but the content will not
   * be converted from one type to another.
   *
   * @param s content from a trusted source like a trusted template author.
   * @param off the index into s at which to start.
   * @param end the index into s at which to stop.
   */
  public void writeSafe(String s, int off, int end)
      throws IOException, TemplateException {
    flush();
    while (off < end) {
      int oc = context;
      int noff = writeChunk(s, off, end);
      // Die early on infinite loops.
      assert !(noff < off || (noff == off && state(oc) == state(context))):
          "off=" + off + ", noff=" + noff
          + ", context=" + Context.toString(oc)
          + " -> " + Context.toString(context);
      off = noff;
    }
  }

  /**
   * Emits a value from an untrusted source by encoding it in the context
   * of the {@link #writeSafe safe} strings emitted prior.
   * For example, if the prior safe content is {@code <a onclick="alert(}, then
   * a JavaScript value is expected, but if the prior safe content is
   * {@code <a href="/search?q=}, then a URL query parameter is expected.
   */
  public void write(@Nullable Object o) throws IOException, TemplateException {
    // In code snippets in comments below, $x indicates an unsafe value.
    if ("".equals(o) && ignoreEmptyUnsafe()) { return; }
    flush();
    try {
      writeUnsafe(o, chooseEscaper());
      this.out = this.underlying;
    } catch (Throwable th) {
      // Recovering from a failure to write is problematic since any output
      // buffer could be in an inconsistent state.
      // Prevent reuse of this instance on failure to write.
      releaseOnClose();
      Throwables.propagateIfPossible(th, IOException.class);
    }
  }

  /**
   * write writes the untrusted content s[off:off+len] to the underlying buffer
   * escaping as necessary to preserve the security properties of this class.
   */
  @Override
  public void write(char[] s, int off, int len)
      throws IOException, TemplateException {
    writeUnsafe(s, off, off + len);
  }

  @Override
  public final void write(String s) throws IOException, TemplateException {
    write(s, 0, s.length());
  }

  /**
   * write writes the untrusted content s[off:off+len] to the underlying buffer
   * escaping as necessary to preserve the security properties of this class.
   */
  @Override
  public void write(String s, int off, int len)
      throws IOException, TemplateException {
    writeUnsafe(s, off, off + len);
  }

  private void writeUnsafe(String s, int off, int end)
      throws IOException, TemplateException {
    // In code snippets in comments below, $x indicates an unsafe value.
    if (off == end && ignoreEmptyUnsafe()) { return; }
    flush();
    try {
      writeUnsafe(s, off, end, chooseEscaper());
      this.out = this.underlying;
    } catch (Throwable th) {
      // Recovering from a failure to write is problematic since any output
      // buffer could be in an inconsistent state.
      // Prevent reuse of this instance on failure to write.
      releaseOnClose();
      Throwables.propagateIfPossible(th, IOException.class);
    }
  }

  @Override
  public void write(int i) throws IOException, TemplateException {
    unsafeBuffered.appendCodePoint(i);
    // Flush on chunks.  '/' occurs reasonably frequently with tags and
    // will not appear inside a URL protocol where splitting could cause
    // problems.
    if (unsafeBuffered.length() > 128 && i == '/') { flush(); }
  }

  /**
   * isSoft returns whether this writer attempts to interoperate with systems
   * that HTML escape inputs by default before they reach this writer.
   * It treats unsafe content in HTML text and attribute contexts as partially
   * escaped HTML instead of as plain text.
   * <p>
   * For example, when not soft, interpolating the string
   * {@code "foo&amp <bar>"} into an HTML text content will result in writing
   * {@code "foo&amp;amp &lt;bar&gt;"} but when soft will result in writing
   * {@code "foo&amp &lt;bar&gt;"} -- existing entities are not reencoded.
   *
   * @see #setSoft
   */
  public boolean isSoft() { return soft; }

  /** setSoft sets the interoperability mode used by {@link #isSoft}. */
  public void setSoft(boolean soft) {
    this.soft = soft;
  }

  /** @return a {@link Context}. */
  int getContext() { return context; }

  /**
   * writeUnsafe writes an unsafe value escaping as necessary to ensure that
   * the output is well-formed HTML that obeys the structure preservation,
   * and code execution properties.
   */
  @VisibleForTesting
  void writeUnsafe(@Nullable Object o, Escaper esc)
      throws IOException, TemplateException {
    // Choose an escaper appropriate to the context.
    switch (esc) {
    case ELIDE: return;
    case ESCAPE_CSS: CSS.escapeStrOnto(o, out); break;
    case ESCAPE_HTML: HTML.escapeOnto(o, out); break;
    case ESCAPE_XML: XML.escapeOnto(o, out); break;
    case ESCAPE_HTML_ATTR:
      String safe = ContentType.Markup.derefSafeContent(o);
      if (safe == null) {
        attrValueEscaper().escapeOnto(o, underlying);
      } else {
        out = underlying;
        try {
          stripTags(safe, delim(context));
        } catch (TemplateException ex) {
          // It's OK to truncate the content here since it is plain text and
          // already normalized as an attribute.
        }
      }
      break;
    case ESCAPE_JS_REGEXP: JS.escapeRegexpOnto(o, out); break;
    case ESCAPE_JS_STRING: JS.escapeStrOnto(o, out); break;
    case ESCAPE_JS_VALUE: JS.escapeValueOnto(o, out); break;
    case ESCAPE_CDATA: XML.escapeCDATAOnto(o, out); break;
    case ESCAPE_RCDATA: HTML.escapeRCDATAOnto(o, out); break;
    case ESCAPE_URL: URL.escapeOnto(false, o, out); break;
    case FILTER_CSS_VALUE: CSS.filterValueOnto(o, out); break;
    case FILTER_NAME_ONTO:
      context = HTML.filterNameOnto(o, out, context);
      break;
    case FILTER_CSS_URL:
    case FILTER_URL:
      String s = URL.filterURL(o);
      int i = 0, n = s.length();
      for (int cp; i < n; i += Character.charCount(cp)) {
        cp = s.codePointAt(i);
        if (!Character.isWhitespace(cp)) { break; }
      }
      if (i == n) { return; }
      context = nextURLContext(s, i, n, context);
      if (esc == Escaper.FILTER_CSS_URL) {
        // We conservatively treat <style>background: "$x"</style> as a
        // URL for the purposes of preventing procotol injection
        // (where $x starts with "javascipt:"), but CSS escape the
        // value instead of URL normalizing it.
        // See comments in tCSS for more detail.
        writeUnsafe(s, i, n, Escaper.ESCAPE_CSS);
      } else {
        writeUnsafe(s, i, n, Escaper.NORMALIZE_URL);
      }
      break;
    case NORMALIZE_HTML: HTML.normalizeOnto(o, out); break;
    case NORMALIZE_XML: XML.normalizeOnto(o, out); break;
    case NORMALIZE_URL: URL.escapeOnto(true, o, out); break;
    case ONE_SPACE: out.write(' '); break;
    }
  }

  /**
   * writeUnsafe writes an unsafe value escaping as necessary to ensure that
   * the output is well-formed HTML that obeys the structure preservation,
   * and code execution properties.
   */
  @VisibleForTesting
  void writeUnsafe(String s, int off, int end, Escaper esc)
      throws IOException, TemplateException {
    // Choose an escaper appropriate to the context.
    switch (esc) {
    case ELIDE: return;
    case ESCAPE_CSS: CSS.escapeStrOnto(s, off, end, out); break;
    case ESCAPE_HTML: HTML.escapeOnto(s, off, end, out); break;
    case ESCAPE_HTML_ATTR:
      attrValueEscaper().escapeOnto(s, off, end, underlying);
      break;
    case ESCAPE_XML: XML.escapeOnto(s, off, end, out); break;
    case ESCAPE_JS_REGEXP: JS.escapeRegexpOnto(s, off, end, out); break;
    case ESCAPE_JS_STRING: JS.escapeStrOnto(s, off, end, out); break;
    case ESCAPE_JS_VALUE: JS.escapeValueOnto(s, off, end, out); break;
    case ESCAPE_CDATA: XML.escapeCDATAOnto(s, off, end, out); break;
    case ESCAPE_RCDATA: HTML.escapeOnto(s, off, end, out); break;
    case ESCAPE_URL: URL.escapeOnto(s, off, end, false, out); break;
    case FILTER_CSS_VALUE: CSS.filterValueOnto(s, off, end, out); break;
    case FILTER_NAME_ONTO:
      context = HTML.filterNameOnto(s, off, end, out, context);
      break;
    case FILTER_CSS_URL:
    case FILTER_URL:
      if (!URL.urlPrefixAllowed(s, off, end)) {
        out.write(URL.FILTER_REPLACEMENT_URL);
        context = Context.urlPart(context, Context.URLPart.QueryOrFrag);
        return;
      }
      for (int cp; off < end; off += Character.charCount(cp)) {
        cp = s.codePointAt(off);
        if (!Character.isWhitespace(cp)) { break; }
      }
      if (off == end) { return; }
      context = nextURLContext(s, off, end, context);
      if (esc == Escaper.FILTER_CSS_URL) {
        // We conservatively treat <style>background: "$x"</style> as a
        // URL for the purposes of preventing procotol injection
        // (where $x starts with "javascipt:"), but CSS escape the
        // value instead of URL normalizing it.
        // See comments in tCSS for more detail.
        writeUnsafe(s, off, end, Escaper.ESCAPE_CSS);
      } else {
        writeUnsafe(s, off, end, Escaper.NORMALIZE_URL);
      }
      break;
    case NORMALIZE_HTML: HTML.normalizeOnto(s, off, end, out); break;
    case NORMALIZE_XML: XML.normalizeOnto(s, off, end, out); break;
    case NORMALIZE_URL: URL.escapeOnto(s, off, end, true, out); break;
    case ONE_SPACE: out.write(' '); break;
    }
  }

  private boolean ignoreEmptyUnsafe() {
    // Normally, emitting the empty string should cause no nudge below, but
    // in some contexts, the empty output is important.
    switch (state(context)) {
    case Context.State.AfterName:
      // Do not allow the statement to be pulled left of the inserted semi in
      // "var x = $x \n foo()"
    case Context.State.JS:
      // Do not allow "/$x/" to be interpreted as a line comment //.
    case Context.State.JSRegexp:
      // Do not allow the value in "<input checked $key=$value>" to associate
      // with the a value-less attribte.
    case Context.State.Tag:
      return false;
    default:
      return true;
    }
  }

  /**
   * chooseEscaper sets up any content encoding needed on out, returns an
   * escaper appropriate to the given context and transitions to the context
   * after the given escaper is run.
   * Individual escapers may still effect their own transitions by taking
   * into account details of the value passed.
   */
  private Escaper chooseEscaper() throws IOException, TemplateException {
    context = nudge(context, out);
    // Wrap out to escape attribute content.  This allows us to handle
    // JS/CSS content below the same regardless of whether it's in a <script>
    // or <a onclick="...">.
    switch (delim(context)) {
      case Context.Delim.None: break;
      case Context.Delim.SingleQuote:
        if (htmlEscapingWriterDqOk == null) {
          htmlEscapingWriterDqOk = new EscapingWriter(out, HTML_DQ_OK);
        }
        out = htmlEscapingWriterDqOk;
        break;
      case Context.Delim.DoubleQuote:
      // We insert double quotes around quoteless attributes so treat as
      // double quoted here.
      case Context.Delim.SpaceOrTagEnd:
        if (htmlEscapingWriterSqOk == null) {
          htmlEscapingWriterSqOk = new EscapingWriter(out, HTML_SQ_OK);
        }
        out = this.htmlEscapingWriterSqOk;
        break;
    }
    // Choose an escaper appropriate to the context.
    switch (state(context)) {
      case Context.State.URL:
      case Context.State.CSSDqStr: case Context.State.CSSSqStr:
      case Context.State.CSSDqURL: case Context.State.CSSSqURL:
      case Context.State.CSSURL:
        switch (urlPart(context)) {
          case Context.URLPart.None:
            switch (state(context)) {
              // We conservatively treat <style>background: "$x"</style> as a
              // URL for the purposes of preventing procotol injection
              // (where $x starts with "javascipt:"), but CSS escape the
              // value instead of URL normalizing it.
              // See comments in tCSS for more detail.
              case Context.State.CSSDqStr: case Context.State.CSSSqStr:
                return Escaper.FILTER_CSS_URL;
              default:
                return Escaper.FILTER_URL;
            }
          case Context.URLPart.PreQuery:
            switch (state(context)) {
              case Context.State.CSSDqStr: case Context.State.CSSSqStr:
                return Escaper.ESCAPE_CSS;
              default:
                return Escaper.NORMALIZE_URL;
            }
          case Context.URLPart.QueryOrFrag:
            return Escaper.ESCAPE_URL;
          default:
            throw new AssertionError(Context.toString(context));
        }
      case Context.State.JS:
        // A slash after a value starts a div operator.
        context = jsCtx(context, Context.JSCtx.DivOp);
        return Escaper.ESCAPE_JS_VALUE;
      case Context.State.JSDqStr: case Context.State.JSSqStr:
        return Escaper.ESCAPE_JS_STRING;
      case Context.State.JSRegexp:
        return Escaper.ESCAPE_JS_REGEXP;
      case Context.State.CSS:
        return Escaper.FILTER_CSS_VALUE;
      case Context.State.Text:
        if (soft) {
          return Escaper.NORMALIZE_HTML;
        } else {
          return Escaper.ESCAPE_HTML;
        }
      case Context.State.XML:
        if (soft) {
          return Escaper.NORMALIZE_XML;
        } else {
          return Escaper.ESCAPE_XML;
        }
      case Context.State.CDATA:
        if (element(context) == Context.Element.XML) {
          return Escaper.ESCAPE_CDATA;
        } else {
          // Since we're normalizing content to a text node, treat as
          // RCDATA which correctly strips tags in known-safe content.
          return Escaper.ESCAPE_RCDATA;
        }
      case Context.State.RCDATA:
        return Escaper.ESCAPE_RCDATA;
      case Context.State.Attr:
        return Escaper.ESCAPE_HTML_ATTR;
      case Context.State.AttrName: case Context.State.TagName:
        return Escaper.FILTER_NAME_ONTO;
      case Context.State.JSBlockCmt:
      case Context.State.JSLineCmt:
      case Context.State.CSSBlockCmt:
      case Context.State.CSSLineCmt:
        // Do nothing.  In writeSafe, we elide comment contents, so skip any
        // value that is written into a comment.
        return Escaper.ELIDE;
      case Context.State.MarkupCmt:
        if (element(context) == Context.Element.XML) {
          // Prevent dashes inside a comment from merging into a -- sequence
          // which can legally contribute to the early close of a comment.
          return Escaper.ONE_SPACE;
        }
        // Comments in HTML are elided as in JS or CSS.
        return Escaper.ELIDE;
      default:
        throw new TemplateException(
            "unexpected state " + Context.toString(context));
    }
  }

  private ReplacementTable attrValueEscaper() {
    switch (delim(context)) {
    case Context.Delim.None:
      return soft ? HTML.NORM_REPLACEMENT_TABLE : HTML.REPLACEMENT_TABLE;
    case Context.Delim.SingleQuote:
      return soft ? NORM_HTML_DQ_OK : HTML_DQ_OK;
    default:
      return soft ? NORM_HTML_SQ_OK : HTML_SQ_OK;
    }
  }

  /**
   * Write a chunk of safe string content.
   * This method establishes the calling convention used below where
   * s[off:end] is the portion of the string being processed.
   *
   * @param off the offset into s to start writing.
   * @param end the end into s of the chunk to write.
   * @return the offset of the remaining unprocessed portion.
   */
  private int writeChunk(String s, int off, int end)
      throws IOException, TemplateException {
    if (delim(context) == Context.Delim.None) {
      int i = findSpecialTagEnd(s, off, end);
      if (i != -1) {
        // A special end tag (`</script>`) has been seen and
        // all content preceding it has been consumed.
        while (off != i) {
          off = transition(s, off, i);
        }
        context = Context.TEXT;
        return i;
      }
      return transition(s, off, end);
    }

    // Find the end of the value and set this.rtable so we can reencode the
    // value as necessary.
    int valueEnd = end;  // After any close quote.
    int contentEnd = off;  // At the end of the content but before any quote.
    switch (delim(context)) {
      case Context.Delim.DoubleQuote:
        for (; contentEnd < end; ++contentEnd) {
          if (s.charAt(contentEnd) == '"') {
            valueEnd = contentEnd+1;
            break;
          }
        }
        this.rtable = HTML_SQ_OK;
        break;
      case Context.Delim.SingleQuote:
        for (; contentEnd < end; ++contentEnd) {
          if (s.charAt(contentEnd) == '\'') {
            valueEnd = contentEnd+1;
            break;
          }
        }
        this.rtable = HTML_DQ_OK;
        break;
      case Context.Delim.SpaceOrTagEnd:
        for (; contentEnd < end; ++contentEnd) {
          char ch = s.charAt(contentEnd);
          // By running the below in various browsers
          // var div = document.createElement("DIV");
          // for (var i = 0; i < 0x10000; ++i) {
          //   div.innerHTML = "<span title=x" +
          //       String.fromCharCode(i) + "-bar>";
          //   var span = div.getElementsByTagName("SPAN")[0];
          //   if (span.title.indexOf("bar") < 0)
          //     document.write("<p>U+" + i.toString(16));
          // }
          // we empircally determine that unquoted attributes are closed by
          // '\t', '\n', '\f', '\r', ' ', '>'.
          // For efficiency, we treat '>', ' ', and any control character
          // in [\0..\037] as an unquoted attribute breaker, and let our
          // quote normalization pick up the slack.
          if (ch <= ' ' || ch == '>') {
            valueEnd = contentEnd;
            break;
          }
        }

        // http://www.w3.org/TR/html5/tokenization.html#attribute-value-unquoted-state
        // lists the runes below as error characters.
        // Error out because HTML parsers may differ on whether
        // "<a id= onclick=f("     ends inside id's or onclick's value,
        // "<a class=`foo "        ends inside a value,
        // "<a style=font:'Arial'" needs open-quote fixup.
        // IE treats '`' as a quotation character.
        int i = off;
        for (; i < valueEnd; ++i) {
          char ch = s.charAt(i);
          if (ch == '"' || ch == '\'' || ch == '<' || ch == '=' || ch == '`') {
            throw makeTemplateException(
                s, off, i, valueEnd, ch + " in unquoted attr: ");
          }
        }
        // tTag inserted an open quote so that we don't leave unquoted
        // attributes unquoted.
        this.rtable = HTML_SQ_OK;
        break;
      default:
        throw new IllegalStateException(Context.toString(context));
    }

    {
      String u = HTML.maybeUnescape(s, off, contentEnd);
      if (u != null) {
        int offu = 0;
        int endu = u.length();
        while (offu < endu) {
          offu = transition(u, offu, endu);
        }
        off = contentEnd;
      } else {
        while (off < contentEnd) {
          off = transition(s, off, contentEnd);
        }
      }
    }
    if (contentEnd == end) { return end; }  // Remain inside the attribute.
    rtable = null;
    if (delim(context) == Context.Delim.SpaceOrTagEnd) {
      // Close the quote introduced in tTag.
      out.write('"');
    } else {
      emit(s, off, valueEnd);
    }
    // On exiting an attribute, we discard all state information
    // except the state and element.
    context = Context.State.Tag | element(context);
    return valueEnd;
  }

  private int transition(String s, int off, int end)
      throws IOException, TemplateException {
    switch (state(context)) {
      case Context.State.Text:        return tText(s, off, end);
      case Context.State.TagName:     return tTagName(s, off, end);
      case Context.State.Tag:         return tTag(s, off, end);
      case Context.State.AttrName:    return tAttrName(s, off, end);
      case Context.State.AfterName:   return tAfterName(s, off, end);
      case Context.State.BeforeValue: return tBeforeValue(s, off, end);
      case Context.State.MarkupCmt:   return tMarkupCmt(s, off, end);
      case Context.State.RCDATA:      return tRCDATA(s, off, end);
      case Context.State.CDATA:       return tCDATA(s, off, end);
      case Context.State.Attr:        return tAttr(s, off, end);
      case Context.State.URL:         return tURL(s, off, end);
      case Context.State.JS:          return tJS(s, off, end);
      case Context.State.JSDqStr:     return tJSDelimited(s, off, end);
      case Context.State.JSSqStr:     return tJSDelimited(s, off, end);
      case Context.State.JSRegexp:    return tJSDelimited(s, off, end);
      case Context.State.JSBlockCmt:  return tBlockCmt(s, off, end);
      case Context.State.JSLineCmt:   return tLineCmt(s, off, end);
      case Context.State.CSS:         return tCSS(s, off, end);
      case Context.State.CSSDqStr:    return tCSSStr(s, off, end);
      case Context.State.CSSSqStr:    return tCSSStr(s, off, end);
      case Context.State.CSSDqURL:    return tCSSStr(s, off, end);
      case Context.State.CSSSqURL:    return tCSSStr(s, off, end);
      case Context.State.CSSURL:      return tCSSStr(s, off, end);
      case Context.State.CSSBlockCmt: return tBlockCmt(s, off, end);
      case Context.State.CSSLineCmt:  return tLineCmt(s, off, end);
      case Context.State.XML:         return tXML(s, off, end);
    }
    throw new IllegalStateException(Context.toString(context));
  }

  /** tText is the context transition function for the text state. */
  private int tText(String s, int off, int end) throws IOException {
    while (true) {
      int lt = off;
      while (lt < end && s.charAt(lt) != '<') { ++lt; }
      if (lt+1 >= end) {
        // At end or not found.
        emit(s, off, lt);
        if (lt < end) {
          out.write("&lt;");
        }
        return end;
      }
      char next = s.charAt(lt+1);
      boolean isDoctype = false;
      if (next == '!') {
        if (lt+4 <= end) {
          if (s.charAt(lt+2) == '-' && s.charAt(lt+3) == '-') {
            context = state(context, Context.State.MarkupCmt);
            emit(s, off, lt);  // elide <!--
            return lt+4;
          } else if (CharsUtil.startsWith(s, lt+2, end, "[CDATA[")) {
            context = state(context, Context.State.CDATA);
            emit(s, off, lt);
            return lt+9;  // elide <![CDATA[
          }
          isDoctype = CharsUtil.startsWithIgnoreCase(s, lt+1, end, "!doctype");
        }
      } else if (next == '?') {
        // http://www.w3.org/TR/2000/REC-xml-20001006#sec-prolog-dtd
        // XMLDecl ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
        if (CharsUtil.startsWith(s, lt+2, end, "xml")) {
          // We found an XML version declaration.
          context = Context.XML;
          emit(s, off, lt+5);
          return lt+5;
        }
      } else {
        int tagStart = lt + 1;
        boolean isEndTag = false;
        if (s.charAt(tagStart) == '/') {
          if (tagStart+1 == end) {
            emit(s, off, lt);
            out.write("&lt;/");
            return end;
          }
          isEndTag = true;
          tagStart++;
        }
        int tagEnd = eatTagName(s, tagStart, end);
        if (tagStart != tagEnd) {
          int el = isEndTag
            ? Context.Element.None : classifyTagName(s, tagStart, tagEnd);
          // We have found an HTML tag.
          context = element(state(context, Context.State.TagName), el);
          emit(s, off, isStrippingTags ? lt : tagEnd);
          return tagEnd;
        }
      }
      if (isStrippingTags || !isDoctype) {
        emit(s, off, lt);
        out.write("&lt;");
        off = lt + 1;
      } else {
        emit(s, off, lt + 9);
        off = lt + 9;
        // Switch to XML mode if we saw a DOCTYPE for an XML kind that cannot
        // appear as foreign XML content in an HTML5 document.
        int state = Doctype.classify(s, off, end);
        if (state != Context.State.Text) {
          context = state;
          return off;
        }
      }
    }
  }

  /**
   * stripTags takes a snippet of HTML and writes to out only the text content.
   * For example, {@code "<b>&iexcl;Hi!</b> <script>...</script>"} &rarr;
   * {@code "&iexcl;Hi! "}.
   */
  @VisibleForTesting
  void stripTags(String s, int delim) throws IOException, TemplateException {
    // Strip tags involves parsing HTML and writing to the same output as safe
    // content, so we reuse the context state machine.
    // First, store the current safe content parser state.
    ReplacementTable ortable = rtable;
    ReplacementTable normtable = (delim == Context.Delim.SingleQuote)
        ? NORM_HTML_DQ_OK : NORM_HTML_SQ_OK;
    Writer oout = this.out;
    int ocontext = this.context;
    this.context = Context.TEXT;
    // This flag is used to avoid processing of unnecessary content such as
    // JS and CSS in raw text nodes and attributes.
    this.isStrippingTags = true;

    try {
      int off = 0, end = s.length();
      // Using the transition funcs helps us avoid mangling
      // `<div title="1>2">` or `I <3 Ponies!`.
      while (off < end) {
        if (delim(context) == Context.Delim.None) {
          if (state(context) == Context.State.Text) {
            this.out = oout;
            this.rtable = normtable;
          } else if (state(context) == Context.State.RCDATA) {
            int i = findSpecialTagEnd(s, off, end);
            if (i < 0) { break; }
            if (element(context) == Context.Element.Textarea) {
              int tagStart = s.lastIndexOf("<", i);
              normtable.escapeOnto(s, off, tagStart, oout);
            }
            off = i;
            context = Context.GENERIC_TAG;
          } else {
            this.out = DEV_NULL;
            this.rtable = null;
          }
          off = transition(s, off, end);
        }
      }
    } finally {
      // Restore safe content parser state stored above.
      this.rtable = ortable;
      this.out = oout;
      this.context = ocontext;
      this.isStrippingTags = false;
    }
  }

  /** Returns a Context.Element value corresponding to s[off:end] */
  static int classifyTagName(String s, int off, int end) {
    switch (end - off) {
    case 5:
      if (CharsUtil.startsWithIgnoreCase(s, off, end, "style")) {
        return Context.Element.Style;
      } else if (CharsUtil.startsWithIgnoreCase(s, off, end, "title")) {
        return Context.Element.Title;
      }
      break;
    case 6:
      if (CharsUtil.startsWithIgnoreCase(s, off, end, "script")) {
        return Context.Element.Script;
      }
      break;
    case 8:
      if (CharsUtil.startsWithIgnoreCase(s, off, end, "textarea")) {
        return Context.Element.Textarea;
      }
      break;
    }
    return Context.Element.None;
  }

  /** tTagName is the context transition function for the tag name state. */
  int tTagName(String s, int off, int end) throws IOException {
    int i = eatTagName(s, off, end);
    if (i == end) {
      emit(s, off, end);
      return end;
    }
    emit(s, off, i);
    context = Context.state(context, Context.State.Tag);
    return i;
  }

  /** tTag is the context transition function for the tag state. */
  int tTag(String s, int off, int end) throws IOException, TemplateException {
    // Find the attribute name or tag end.
    int i;
    if (!isStrippingTags) {
      i = eatWhiteSpace(s, off, end);
      if (i == end) {
        emit(s, off, end);
        return end;
      }
    } else {
      char delim = 0;
      attrloop: for (i = off; i < end; ++i) {
        char ch = s.charAt(i);
        switch (ch) {
          case '>':
            if (delim == 0) { break attrloop; }
            break;
          case '"': case '\'':
            if (delim == ch) { delim = 0; }
            else if (delim == 0) { delim = ch; }
            break;
        }
      }
      if (i == end) {
        // Don't bother parsing attribute context when stripping tags, and
        // don't error out on malformed attribute names or values.
        if (delim != 0) {
          context = delim(
              state(context, Context.State.Attr),
              delim == '"' ? Context.Delim.DoubleQuote
                           : Context.Delim.SingleQuote);
        }
        return end;
      }
    }
    if (s.charAt(i) == '>') {
      emit(s, off, i+1);
      int state;
      int element = element(context);
      if (element == Context.Element.None) {
        state = Context.State.Text;
      } else if (element == Context.Element.XML) {
        state = Context.State.XML;
        context = Context.XML;  // Strip element.
      } else {
        state = Context.State.RCDATA;
        // When stripping tags, treat all content as RCDATA to avoid wasting
        // time parsing CSS and JS tokens that are just going to be stripped.
        if (!isStrippingTags) {
          switch (element) {
            case Context.Element.Script: state = Context.State.JS; break;
            case Context.Element.Style:  state = Context.State.CSS; break;
          }
        }
      }
      context = state(context, state);
      return i+1;
    }
    int state = Context.State.Tag;
    int j = eatAttrName(s, i, end);
    if (i == j) {
      throw makeTemplateException(
          s, off, i, end,
          "expected space, attr name, or end of tag, but got ");
    }
    int attr;
    switch (Attr.attrType(s, i, j)) {
      case URL:
        attr = Context.Attr.URL;
        break;
      case CSS:
        attr = Context.Attr.Style;
        break;
      case JS:
        attr = Context.Attr.Script;
        break;
      default:
        attr = Context.Attr.None;
        break;
    }
    if (j == end) {
      state = Context.State.AttrName;
    } else {
      state = Context.State.AfterName;
    }
    emit(s, off, j);
    context = attr(state(context, state), attr);
    return j;
  }

  /** tAttrName is the context transition function for state AttrName. */
  private int tAttrName(String s, int off, int end)
      throws IOException, TemplateException {
    int i = eatAttrName(s, off, end);
    if (i != end) {
      context = state(context, Context.State.AfterName);
    }
    emit(s, off, i);
    return i;
  }

  /** tAfterName is the context transition function for state AfterName. */
  private int tAfterName(String s, int off, int end) throws IOException {
    // Look for the start of the value.
    int i = eatWhiteSpace(s, off, end);
    if (i == end) {
      emit(s, off, end);
      return end;
    } else if (s.charAt(i) != '=') {
      // Occurs due to tag ending '>', and valueless attribute.
      context = state(context, Context.State.Tag);
      emit(s, off, i);
      return i;
    }
    context = state(context, Context.State.BeforeValue);
    // Consume the "=".
    i++;
    emit(s, off, i);
    return i;
  }

  /**
   * Mapping from attribure types to the start states.
   * For example, the value of the {@code style="..."} attribute starts in
   * {@link Context.State#CSS state CSS}.
   */
  private static final int[] ATTR_START_STATES
    = new int[(Context.Attr.MASK >> Context.Attr.SHIFT) + 1];
  static {
    ATTR_START_STATES[Context.Attr.None >> Context.Attr.SHIFT]
       = Context.State.Attr;
    ATTR_START_STATES[Context.Attr.Script >> Context.Attr.SHIFT]
       = Context.State.JS;
    ATTR_START_STATES[Context.Attr.Style >> Context.Attr.SHIFT]
       = Context.State.CSS;
    ATTR_START_STATES[Context.Attr.URL >> Context.Attr.SHIFT]
       = Context.State.URL;
  }

  /**
   * Mapping from attribure types to the start states.
   * For example, the value of the {@code style="..."} attribute starts in
   * {@link Context.State#CSS state CSS}.
   */
  private static final int attrStartState(int attr) {
    return ATTR_START_STATES[attr >> Context.Attr.SHIFT];
  }

  /** tBeforeValue is the context transition function for stateBeforeValue. */
  private int tBeforeValue(String s, int off, int end) throws IOException {
    int i = eatWhiteSpace(s, off, end);
    if (i == end) {
      emit(s, off, end);
      return end;
    }
    // Find the attribute delimiter.
    int delim;
    switch (s.charAt(i)) {
      case '\'':
        delim = Context.Delim.SingleQuote;
        i++;
        break;
      case '"':
        delim = Context.Delim.DoubleQuote;
        i++;
        break;
      default:
        emit(s, off, i);
        out.write('"');
        off = i;
        delim = Context.Delim.SpaceOrTagEnd;
        break;
    }
    context = attr(
        delim(
            state(context, attrStartState(attr(context))),
            delim),
        Context.Attr.None);
    emit(s, off, i);
    return i;
  }

  /** tMarkupCmt is the context transition function for State.MarkupCmt. */
  private int tMarkupCmt(String s, int off, int end) throws IOException {
    boolean isXML = element(context) == Context.Element.XML;
    int i = CharsUtil.findHtmlCommentEnd(s, off, end);
    if (i != -1) {
      if (isXML) {
        emit(s, off, i+3);
        context = Context.XML;
      } else {
        // Do not emit.
        context = Context.TEXT;
      }
      return i+3;
    }
    if (isXML) {
      emit(s, off, end);
    } else {
      // Do not emit.
    }
    return end;
  }

  /**
   * Looks for special tag body ends.
   * The content of {@code <script>...</script>} is not regular HTML content.
   * Instead it is raw textual content that ends when a {@code </script} is
   * seen.  This method finds that end tag.
   * @return the index of the first end tag corresponding to
   *   {@link Context.Element element(context)} in s between off and end
   *   or -1 if not found.
   */
  private int findSpecialTagEnd(String s, int off, int end) {
    int el = element(context);
    if (el != Context.Element.None) {
      for (int i = off; (i = CharsUtil.findEndTag(s, off, end) + 2) != 1;
           off = i) {
        int j = eatTagName(s, i, end);
        if (el == classifyTagName(s, i, j)) {
          return j;
        }
      }
    }
    return -1;
  }

  /**
   * tRCDATA is the context transition function for RCDATA element states.
   */
  private int tRCDATA(String s, int off, int end) throws IOException {
    int i = findSpecialTagEnd(s, off, end);
    rtable = HTML.NORM_REPLACEMENT_TABLE;
    if (i != -1) {
      int tagStart = i;
      while (s.charAt(tagStart) != '<') { --tagStart; }
      emit(s, off, tagStart);
      rtable = null;
      emit(s, tagStart, i);
      context = Context.TEXT;
      return i;
    }
    emit(s, off, end);
    return end;
  }

  /**
   * tCDATA is the context transition function for the inside of a 
   * {@code <![CDATA[[...]]>} section that appears in an HTML or XML document
   * whether inside a foreign XML element or not.
   */
  private int tCDATA(String s, int off, int end) throws IOException {
    boolean isXML = element(context) == Context.Element.XML;
    int pos = off;
    for (int i = off; i < end; ++i) {
      String repl;
      // Normalize HTML Text special characters.
      switch (s.charAt(i)) {
        case '>':
          // End the CDATA section if we see ]]>.
          if (i - 2 >= off && ']' == s.charAt(i-1) && ']' == s.charAt(i-2)) {
            if (isXML) {
              emit(s, pos, i+1);
              context = Context.XML;
            } else {
              emit(s, pos, i-2);
              // Elide the ]]>
              context = Context.TEXT;
            }
            return i+1;
          }
          repl = "&gt;";
          break;
        case '&':
          repl = "&amp;";
          break;
        case '<':
          repl = "&lt;";
          break;
        default: continue;
      }
      if (!isXML) {
        emit(s, pos, i);
        out.write(repl);
        pos = i + 1;
      }
    }
    emit(s, pos, end);
    return end;
  }

  /** tAttr is the context transition function for the attribute state. */
  private int tAttr(String s, int off, int end) throws IOException {
    emit(s, off, end);
    return end;
  }

  /** tURL is the context transition function for the URL state. */
  private int tURL(String s, int off, int end) throws IOException {
    context = nextURLContext(s, off, end, context);
    emit(s, off, end);
    return end;
  }

  private static int nextURLContext(String s, int context) {
    return nextURLContext(s, 0, s.length(), context);
  }

  /** nextURLContext returns the context after the URL chars in s[off:end]. */
  private static int nextURLContext(String s, int off, int end, int context) {
    int i = off;
    while (i < end && s.charAt(i) != '#' && s.charAt(i) != '?') { ++i; }
    if (i < end) {
      context = urlPart(context, Context.URLPart.QueryOrFrag);
    } else if (urlPart(context) == Context.URLPart.None
               && end != eatWhiteSpace(s, off, end)) {
      // HTML5 uses "Valid URL potentially surrounded by spaces" for
      // attrs: http://www.w3.org/TR/html5/index.html#attributes-1
      context = urlPart(context, Context.URLPart.PreQuery);
    }
    return context;
  }

  /** tJS is the context transition function for the JS state. */
  private int tJS(String s, int off, int end)
      throws IOException, TemplateException {
    for (int i = off; i < end; ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case '"': case '\'':
          context = jsCtx(
              state(
                  context,
                  (ch == '"' ? Context.State.JSDqStr : Context.State.JSSqStr)),
              Context.JSCtx.Regexp);
          emit(s, off, i+1);
          return i+1;
        case '/':
          updateJSCtx(s, off, i);
          if (i+1 < end) {
            if (s.charAt(i+1) == '/') {
              context = state(context, Context.State.JSLineCmt);
              emit(s, off, i);
              return i+2;
            } else if (s.charAt(i+1) == '*') {
              context = state(context, Context.State.JSBlockCmt);
              emit(s, off, i);
              return i+2;
            }
          }
          switch (jsCtx(context)) {
            case Context.JSCtx.Regexp:
              context = state(context, Context.State.JSRegexp);
              emit(s, off, i+1);
              return i+1;
            case Context.JSCtx.DivOp:
              context = jsCtx(context, Context.JSCtx.Regexp);
              break;
            default:
              throw makeTemplateException(
                  s, off, i, end, "'/' could start a division or regexp: ");
          }
          break;
      }
    }
    updateJSCtx(s, off, end);
    emit(s, off, end);
    return end;
  }

  /**
   * tJSDelimited is the context transition function for the JS string and
   * regexp states.
   */
  private int tJSDelimited(String s, int off, int end)
      throws IOException, TemplateException {
    boolean inCharset = false;
    while (true) {
      int i = off;
      switch (state(context)) {
        case Context.State.JSDqStr:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '"') { i++; }
          break;
        case Context.State.JSSqStr:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '\'') { i++; }
          break;
        case Context.State.JSRegexp:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '/'
                 && s.charAt(i) != '[' && s.charAt(i) != ']') {
            i++;
          }
          break;
        default:
          throw new IllegalStateException();
      }

      if (i == end) {
        break;
      }
      switch (s.charAt(i)) {
        case '\\':
          i++;
          if (i == end) {
            throw makeTemplateException(
                s, off, i-1, end, "unfinished escape sequence in JS string: ");
          }
          break;
        case '[':
          inCharset = true;
          break;
        case ']':
          inCharset = false;
          break;
        default:
          // end delimiter
          if (!inCharset) {
            context = jsCtx(
                state(context, Context.State.JS),
                Context.JSCtx.DivOp);
            emit(s, off, i + 1);
            return i + 1;
          }
      }
      emit(s, off, i+1);
      off = i + 1;
    }

    if (inCharset) {
      // This can be fixed by making context richer if interpolation
      // into charsets is desired.
      throw makeTemplateException(
          s, off, end, end, "unfinished JS regexp charset: ");
    }
    emit(s, off, end);
    return end;
  }

  /**
   * tBlockCmt is the context transition function for
   * {@code /*comment*}{@code /} states.
   */
  private int tBlockCmt(String s, int off, int end) throws IOException {
    boolean isJS = state(context) == Context.State.JSBlockCmt;
    char replacement = ' ';
    for (int i = off; i < end; ++i) {
      char ch = s.charAt(i);
      if (ch == '*' && i+1 <= end && s.charAt(i+1) == '/') {
        context = state(context, isJS ? Context.State.JS : Context.State.CSS);
        // Do not emit.
        out.write(replacement);
        return i + 2;
      }
      if (isJS) {
        switch (ch) {
          case '\r': case '\n': case '\u2028': case '\u2029':
            replacement = '\n';
        }
      }
    }
    // Do not emit.
    out.write(replacement);
    return end;
  }

  /** tLineCmt is the context transition function for //comment states. */
  private int tLineCmt(String s, int off, int end) {
    int i = off;
    if (state(context) == Context.State.JSLineCmt) {
      for (; i < end; ++i) {
        char ch = s.charAt(i);
        if (ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029') {
          context = state(context, Context.State.JS);
          break;
        }
      }
    } else {
      // Line comments are not part of any published CSS standard but
      // are supported by the 4 major browsers.
      // This defines line comments as
      //     LINECOMMENT ::= "//" [^\n\f\d]*
      // since http://www.w3.org/TR/css3-syntax/#SUBTOK-nl defines
      // newlines:
      //     nl ::= #xA | #xD #xA | #xD | #xC
      for (; i < end; ++i) {
        char ch = s.charAt(i);
        if (ch == '\n' || ch == '\r' || ch == '\f') {
          context = state(context, Context.State.CSS);
          break;
        }
      }
    }
    // Per section 7.4 of EcmaScript 5 : http://es5.github.com/#x7.4
    // "However, the LineTerminator at the end of the line is not
    // considered to be part of the single-line comment; it is
    // recognized separately by the lexical grammar and becomes part
    // of the stream of input elements for the syntactic grammar."
    return i;
  }

  /** tCSS is the context transition function for the CSS state. */
  private int tCSS(String s, int off, int end) throws IOException {
    // CSS quoted strings are almost never used except for:
    // (1) URLs as in background: "/foo.png"
    // (2) Multiword font-names as in font-family: "Times New Roman"
    // (3) List separators in content values as in inline-lists:
    //    <style>
    //    ul.inlineList { list-style: none; padding:0 }
    //    ul.inlineList > li { display: inline }
    //    ul.inlineList > li:before { content: ", " }
    //    ul.inlineList > li:first-child:before { content: "" }
    //    </style>
    //    <ul class=inlineList><li>One<li>Two<li>Three</ul>
    // (4) Attribute value selectors as in a[href="http://example.com/"]
    //
    // We conservatively treat all strings as URLs, but make some
    // allowances to avoid confusion.
    //
    // In (1), our conservative assumption is justified.
    // In (2), valid font names do not contain ':', '?', or '#', so our
    // conservative assumption is fine since we will never transition past
    // Context.URLPart.PreQuery.
    // In (3), our protocol heuristic should not be tripped, and there
    // should not be non-space content after a '?' or '#', so as long as
    // we only %-encode RFC 3986 reserved characters we are ok.
    // In (4), we should URL escape for URL attributes, and for others we
    // have the attribute name available if our conservative assumption
    // proves problematic for real code.

    for (;;) {
      int i = off;
      for (; i < end; ++i) {
        char ch = s.charAt(i);
        if (ch == '(' || ch == '"' || ch == '\'' || ch == '/') { break; }
      }
      if (i == end) {
        emit(s, off, end);
        return end;
      }
      switch (s.charAt(i)) {
        case '(':
          // Look for url to the left.
          int p = i;
          while (p > off && CSS.isCSSSpace(s.charAt(p-1))) { --p; }
          if (p - 3 >= off) {
            char c0 = s.charAt(p-3), c1 = s.charAt(p-2), c2 = s.charAt(p-1);
            if ((c0 == 'u' || c0 == 'U')
                && (c1 == 'r' || c1 == 'R')
                && (c2 == 'l' || c2 == 'L')) {
              int j = i+1;
              while (j < end && CSS.isCSSSpace(s.charAt(j))) { ++j; }
              if (j < end && s.charAt(j) == '"') {
                context = state(context, Context.State.CSSDqURL);
                ++j;
              } else if (j < end && s.charAt(j) == '\'') {
                context = state(context, Context.State.CSSSqURL);
                ++j;
              } else {
                context = state(context, Context.State.CSSURL);
              }
              emit(s, off, j);
              return j;
            }
          }
          break;
        case '/':
          if (i+1 < end) {
            switch (s.charAt(i+1)) {
              case '/':
                context = state(context, Context.State.CSSLineCmt);
                emit(s, off, i);  // Skip comment open.
                return i + 2;
              case '*':
                context = state(context, Context.State.CSSBlockCmt);
                emit(s, off, i);  // Skip comment open.
                return i + 2;
            }
          }
          break;
        case '"':
          context = state(context, Context.State.CSSDqStr);
          emit(s, off, i+1);
          return i+1;
        case '\'':
          context = state(context, Context.State.CSSSqStr);
          emit(s, off, i+1);
          return i+1;
      }
      emit(s, off, i+1);
      off = i+1;
    }
  }

  /**
   * tCSSStr is the context transition function for the CSS string and URL
   * states.
   */
  private int tCSSStr(String s, int off, int end)
      throws IOException, TemplateException {
    for (;;) {
      int i = off;
      switch (state(context)) {
        case Context.State.CSSDqStr: case Context.State.CSSDqURL:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '"') { ++i; }
          break;
        case Context.State.CSSSqStr: case Context.State.CSSSqURL:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '\'') { ++i; }
          break;
        case Context.State.CSSURL:
          // Unquoted URLs end with a newline or close parenthesis.
          // The below includes the wc (whitespace character) and nl.
          while (i < end && s.charAt(i) != '\\'
                 && !CSS.isCSSSpace(s.charAt(i)) && s.charAt(i) != ')') {
            ++i;
          }
          break;
        default:
          throw new AssertionError(Context.toString(context));
      }
      if (i == end) {
        String decoded = CSS.maybeDecodeCSS(s, off, end);
        if (decoded != null) {
          context = nextURLContext(decoded, context);
        } else {
          context = nextURLContext(s, off, end, context);
        }
        emit(s, off, end);
        return end;
      }
      if (s.charAt(i) == '\\') {
        i++;
        if (i == end) {
          throw makeTemplateException(
              s, off, i-1, end, "unfinished escape sequence in CSS string: ");
        }
      } else {
        context = state(context, Context.State.CSS);
        emit(s, off, i+1);
        return i+1;
      }
      String decoded = CSS.maybeDecodeCSS(s, off, i+1);
      if (decoded != null) {
        context = nextURLContext(decoded, context);
      } else {
        context = nextURLContext(s, off, i+1, context);
      }
      emit(s, off, i+1);
      off = i + 1;
    }
  }

  /** tXML is the context transition function for the XML text node state. */
  private int tXML(String s, int off, int end) throws IOException {
    while (true) {
      int lt = off;
      while (lt < end && s.charAt(lt) != '<') { ++lt; }
      if (lt+1 >= end) {
        // At end or not found.
        emit(s, off, lt);
        if (lt < end) {
          out.write("&lt;");
        }
        return end;
      }
      if (lt+4 <= end && s.charAt(lt+1) == '!') {
        if (s.charAt(lt+2) == '-' && s.charAt(lt+3) == '-') {
          context = element(
              state(context, Context.State.MarkupCmt),
              Context.Element.XML);
          emit(s, off, lt+4);
          return lt+4;
        } else if (CharsUtil.startsWith(s, lt+2, end, "[CDATA[")) {
          context = element(
              state(context, Context.State.CDATA),
              Context.Element.XML);
          emit(s, off, lt+9);
          return lt+9;
        }
      }
      int tagStart = lt + 1;
      if (s.charAt(tagStart) == '/') {
        if (tagStart+1 == end) {
          emit(s, off, lt);
          out.write("&lt;/");
          return end;
        }
        tagStart++;
      }
      int tagEnd = eatTagName(s, tagStart, end);
      if (tagStart != tagEnd) {
        // We have found an XML tag.
        context = element(
          state(context, Context.State.TagName), Context.Element.XML);
        emit(s, off, isStrippingTags ? lt : tagEnd);
        return tagEnd;
      }
      emit(s, off, lt + 1);
      off = lt + 1;
    }
  }

  /**
   * eatAttrName returns the largest j such that s[i:j] is an attribute name.
   * It returns an error if s[i:] does not look like it begins with an
   * attribute name, such as encountering a quote mark without a preceding
   * equals sign.
   */
  private int eatAttrName(String s, int off, int end) throws TemplateException {
    for (int j = off; j < end; ++j) {
      switch (s.charAt(j)) {
        case ' ': case '\t': case '\n': case '\f': case '\r': case '=':
        case '>':
          return j;
        case '\'': case '"': case '<':
          // These result in a parse warning in HTML5 and are
          // indicative of serious problems if seen in an attr
          // name in a template.
          throw makeTemplateException(
              s, off, j, end, "" + s.charAt(j) + " in attribute name: ");
        default:
          // No-op.
      }
    }
    return end;
  }

  /**
   * Write s[off:end] to the underlying buffer with any needed transformations.
   */
  private void emit(String s, int off, int end) throws IOException {
    if (rtable != null) {
      rtable.escapeOnto(s, off, end, out);
    } else {
      out.write(s, off, end - off);
    }
  }

  /** asciiAlpha returns whether c is an ASCII letter. */
  private static boolean asciiAlpha(char c) {
    return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
  }

  /** asciiAlphaNum returns whether c is an ASCII letter or digit. */
  private static boolean asciiAlphaNum(char c) {
    return asciiAlpha(c) || ('0' <= c && c <= '9');
  }

  /**
   * eatTagName returns the largest i such that s[off:i] is a tag name and
   * i <= end.
   */
  private static int eatTagName(String s, int off, int end) {
    if (off == end || !asciiAlpha(s.charAt(off))) {
      return off;
    }
    int i = off + 1;
    while (i < end) {
      char ch = s.charAt(i);
      if (asciiAlphaNum(ch)) {
        i++;
        continue;
      }
      // Allow "x-y" or "x:y" but not "x-", "-y", or "x--y".
      if ((ch == ':' || ch == '-') && i+1 < end
          && asciiAlphaNum(s.charAt(i+1))) {
        i += 2;
        continue;
      }
      break;
    }
    return i;
  }

  /** eatWhiteSpace returns the largest i such that s[off:i] is white space. */
  private static int eatWhiteSpace(String s, int off, int end) {
    for (int i = off; i < end; ++i) {
      switch (s.charAt(i)) {
        case ' ': case '\t': case '\n': case '\f': case '\r':
          break;
        default:
          return i;
      }
    }
    return end;
  }

  /**
   * @param pos the index of the problem in s.
   */
  private TemplateException makeTemplateException(
      String s, int off, int pos, int end, String msg) {
    msg = new StringBuilder(msg.length() + end - off + 1)
        .append(msg).append(s, off, pos).append('^')
        .append(s, pos, end).toString();
    return new TemplateException(msg);
  }

  /**
   * @param pos the index of the problem in s.
   */
  private TemplateException makeTemplateException(
      char[] s, int off, int pos, int end, String msg) {
    msg = new StringBuilder(msg.length() + end - off + 1)
        .append(msg).append(s, off, pos - off).append('^')
        .append(s, pos, end - pos).toString();
    return new TemplateException(msg);
  }

  /**
   * Updates context with the JS slash context based on a run of JS tokens in
   * s[off:end].
   */
  private void updateJSCtx(String s, int off, int end) {
    context = jsCtx(context, JS.nextJSCtx(s, off, end, jsCtx(context)));
  }

  /**
   * nudge returns the context that would result from following empty string
   * transitions from the input context.
   * For example, parsing:
   * <pre>
   *     <a href=
   * </pre>
   * will end in context{stateBeforeValue, attrURL}, but parsing one extra rune:
   * <pre>
   *     <a href=x
   * </pre>
   * will end in context{stateURL, delimSpaceOrTagEnd, ...}.
   * There are two transitions that happen when the 'x' is seen:<ul>
   * <li>Transition from a before-value state to a start-of-value state without
   *     consuming any character.
   * <li>Consume 'x' and transition past the first value character.
   * </ul>
   * In this case, nudging produces the context after (1) happens.
   */
  private static int nudge(int c, Writer out) throws IOException {
    switch (state(c)) {
      case Context.State.Tag:
        // In `<foo {{.}}`, the action should emit an attribute.
        return state(c, Context.State.AttrName);
      case Context.State.BeforeValue:
        // Emit an open double quote to match that emitted by tTag when it
        // transitions directly into a SpaceOrTagEnd context.
        out.write('"');
        // In `<foo bar={{.}}`, the action is an undelimited value.
        return attr(delim(state(c, attrStartState(attr(c))),
                          Context.Delim.SpaceOrTagEnd),
                    Context.Attr.None);
      case Context.State.AfterName:
        // In `<foo bar {{.}}`, the action is an attribute name.
        return attr(state(c, Context.State.AttrName), Context.Attr.None);
      default:
        return c;
    }
  }

  /** A writer that wraps another writer to encode written content. */
  private static class EscapingWriter extends FilterWriter {
    private final ReplacementTable rt;
    EscapingWriter(Writer out, ReplacementTable rt) {
      super(out);
      this.rt = rt;
    }
    @Override
    public void write(int cp) throws IOException {
      rt.escapeOnto(cp, this.out);
    }
    @Override
    public void write(String s, int off, int n) throws IOException {
      rt.escapeOnto(s, off, off + n, out);
    }
    @Override
    public void write(char[] s, int off, int n) throws IOException {
      rt.escapeOnto(s, off, off + n, out);
    }
  }

  /**
   * HTML escaping table that allows single quotes for use in double-quote
   * delimited attribute values.
   */
  static final ReplacementTable HTML_SQ_OK
      = new ReplacementTable(HTML.REPLACEMENT_TABLE).add('\'', null);
  /**
   * HTML escaping table that allows double quotes for use in single-quote
   * delimited attribute values.
   */
  static final ReplacementTable HTML_DQ_OK
      = new ReplacementTable(HTML.REPLACEMENT_TABLE).add('"', null);
  /** Like {@link #HTML_SQ_OK} but does not encode {@code '&'}. */
  static final ReplacementTable NORM_HTML_SQ_OK
      = new ReplacementTable(HTML_SQ_OK).add('&', null);
  /** Like {@link #HTML_DQ_OK} but does not encode {@code '&'}. */
  static final ReplacementTable NORM_HTML_DQ_OK
      = new ReplacementTable(HTML_DQ_OK).add('&', null);

  /** Discards all input. */
  static final Writer DEV_NULL = new Writer()  {
    @Override public void close() throws IOException { /* no-op */ }
    @Override public void flush() throws IOException { /* no-op */ }
    @Override public void write(char[] cbuf, int off, int len) { /* no-op */ }
    @Override public void write(String s, int off, int len) { /* no-op */ }
    @Override public void write(int c) { /* no-op */ }
  };

  // Privileged accessors for the memoizing writer.
  void setContext(int context) { this.context = context; }
  Writer getWriter() { return this.out; }
  void replaceWriter(Writer out) { this.out = out; }
}
"""  # Fix emacs syntax highlighting "

import dupe_methods
print dupe_methods.dupe(src)
