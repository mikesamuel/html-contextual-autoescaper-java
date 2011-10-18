package com.google.autoesc;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.autoesc.Context.URLPart;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

/**
 * A writer like-object that receives chunks of trusted template text via
 * {@link HTMLEscapingWriter#writeSafe}, and chunks of untrusted content via
 * {@link HTMLEscapingWriter#write(Object)} and escapes the untrusted content
 * according to the context established by the trusted portions to prevent XSS.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@NotThreadSafe
public class HTMLEscapingWriter {
  private Writer out;
  private Writer htmlEscapingWriterDqOk, htmlEscapingWriterSqOk;
  private Context context;
  private ReplacementTable rtable;
  /**
   * HACK: When stripping tags from trusted HTML content that is interpolated
   * into an attribute value, we rerun the HTML scanner, so that we can
   * emit just the non-tag content.
   */
  private boolean isStrippingTags;

  public HTMLEscapingWriter(Writer out) {
    this.out = out;
    this.context = Context.TEXT;
  }

  Context getContext() { return context; }

  /**
   * Emits a string of content from a trusted source.  The content may be
   * normalized (all attributes are quoted, comments are elided, and stray
   * {@code '<'} will be escaped to {@code "&lt;"}) but the content will not
   * be converted from one type to another.
   *
   * @param s content from a trusted source like a trusted template author.
   */
  public void writeSafe(String s) throws IOException, TemplateException {
    int off = 0;
    int end = s.length();
    while (off < end) {
      Context oc = context;
      int noff = writeChunk(s, off, end);
      if (noff < off || (noff == off && oc.state == context.state)) {
        throw new AssertionError(
            "off=" + off + ", noff=" + noff + ", context=" + oc
            + " -> " + context);
      }
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
    try {
      writeUnsafe(o);
    } catch (Throwable th) {
      // Recovering from a failure to write is problematic since any output
      // buffer could be in an inconsistent state.
      // Prevent reuse of this instance on failure to write.
      this.context = null;
      this.out = null;
      this.rtable = null;
      this.htmlEscapingWriterSqOk = htmlEscapingWriterDqOk = null;
      if (th instanceof RuntimeException) {
        throw (RuntimeException) th;
      } else if (th instanceof Error) {
        throw (Error) th;
      } else if (th instanceof IOException) {
        throw (IOException) th;
      } else if (th instanceof TemplateException) {
        throw (TemplateException) th;
      }
      Throwables.propagate(th);
    }
  }

  /**
   * Closes the underlying writer, and raises an error if the content ends in
   * an inconsistent state -- if a full, valid HTML fragment has not been
   * written.
   */
  public void close() throws IOException, TemplateException {
    out.close();
    if (context.state != Context.State.Text) {
      throw new TemplateException("Incomplete document fragment");
    }
  }

  private void writeUnsafe(@Nullable Object o)
      throws IOException, TemplateException {
    if ("".equals(o)) {
      switch (context.state) {
        case AfterName:
        case JS:
        case JSRegexp:
        case Tag:
          break;
        default:
          return;
      }
    }
    context = nudge(context, out);
    Writer out = this.out;
    switch (context.delim) {
      case None: break;
      case SingleQuote:
        if (htmlEscapingWriterDqOk == null) {
          htmlEscapingWriterDqOk = new EscapingWriter(out, HTML_DQ_OK);
        }
        out = htmlEscapingWriterDqOk;
        break;
      case DoubleQuote:
      // We insert double quotes around quoteless attributes so treat as
      // double quoted here.
      case SpaceOrTagEnd:
        if (htmlEscapingWriterSqOk == null) {
          htmlEscapingWriterSqOk = new EscapingWriter(out, HTML_SQ_OK);
        }
        out = this.htmlEscapingWriterSqOk;
        break;
    }
    switch (context.state) {
      case URL: case CSSDqStr: case CSSSqStr:
      case CSSDqURL: case CSSSqURL: case CSSURL:
        switch (context.urlPart) {
          case None:
            String s = URL.filterURL(o);
            int i = 0;
            for (int n = s.length(), cp; i < n; i += Character.charCount(cp)) {
              cp = s.codePointAt(i);
              if (!Character.isWhitespace(cp)) { break; }
            }
            s = s.substring(i);
            if (s.length() != 0) {
              context = context.withURLPart(URLPart.PreQuery).build();
              switch (context.state) {
                case CSSDqStr: case CSSSqStr:
                  CSS.escapeStrOnto(s, out);
                  break;
                default:
                  URL.escapeOnto(true, s, out);
                  break;
              }
            }
            break;
          case PreQuery:
            switch (context.state) {
              case CSSDqStr: case CSSSqStr:
                CSS.escapeStrOnto(o, out);
                break;
              default:
                URL.escapeOnto(true, o, out);
                break;
            }
            break;
          case QueryOrFrag:
            URL.escapeOnto(false, o, out);
            break;
          default:
            throw new AssertionError(context.urlPart.toString());
        }
        break;
      case JS:
        JS.escapeValueOnto(o, out);
        // A slash after a value starts a div operator.
        context = context.withJSCtx(Context.JSCtx.DivOp).build();
        break;
      case JSDqStr: case JSSqStr:
        JS.escapeStrOnto(o, out);
        break;
      case JSRegexp:
        JS.escapeRegexpOnto(o, out);
        break;
      case CSS:
        CSS.filterValueOnto(o, out);
        break;
      case Text:
        HTML.escapeOnto(o, out);
        break;
      case RCDATA:
        HTML.escapeRCDATAOnto(o, out);
        break;
      case Attr:
        String safe = ContentType.HTML.derefSafeContent(o);
        if (safe == null) {
          ((EscapingWriter) out).rt.escapeOnto(o, this.out);
        } else {
          try {
            stripTags(safe, context.delim);
          } catch (TemplateException ex) {
            // It's OK to truncate the content here since it is plain text and
            // already normalized as an attribute.
          }
        }
        break;
      case AttrName: case Tag:
        context = context.withState(Context.State.AttrName).build();
        HTML.filterNameOnto(o, out);
        break;
      default:
        if (context.state.isComment()) {
          // Do nothing.  In writeSafe, we elide comment contents, so skip any
          // value that is written into a comment.
        } else {
          throw new TemplateException("unexpected state " + context.state);
        }
    }
  }

  private int writeChunk(String s, int off, int end)
      throws IOException, TemplateException {
    if (context.delim == Context.Delim.None) {
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

    // Find the end of the delimiter.
    int valueEnd = end;  // After any close quote.
    int contentEnd = off;  // At the end of the content but before any quote.
    switch (context.delim) {
      case DoubleQuote:
        for (; contentEnd < end; ++contentEnd) {
          if (s.charAt(contentEnd) == '"') {
            valueEnd = contentEnd+1;
            break;
          }
        }
        break;
      case SingleQuote:
        for (; contentEnd < end; ++contentEnd) {
          if (s.charAt(contentEnd) == '\'') {
            valueEnd = contentEnd+1;
            break;
          }
        }
        break;
      case SpaceOrTagEnd:
        for (; contentEnd < end; ++contentEnd) {
          char ch = s.charAt(contentEnd);
          // Determined empirically by running the below in various browsers.
          // var div = document.createElement("DIV");
          // for (var i = 0; i < 0x10000; ++i) {
          //   div.innerHTML = "<span title=x" +
          //       String.fromCharCode(i) + "-bar>";
          //   var span = div.getElementsByTagName("SPAN")[0];
          //   if (span.title.indexOf("bar") < 0)
          //     document.write("<p>U+" + i.toString(16));
          // }
          if (ch == '\t' || ch == '\n' || ch == '\f' || ch == '\r' || ch == ' '
              || ch == '>') {
            valueEnd = contentEnd;
            break;
          }
        }
        break;
      case None:
        throw new AssertionError();
    }
    if (context.delim == Context.Delim.SpaceOrTagEnd) {
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
    }

    // Decode the value so non-HTML rules can easily handle
    //     <button onclick="alert(&quot;Hi!&quot;)">
    // without having to entity decode token boundaries.
    rtable = context.delim == Context.Delim.SingleQuote
        ? HTML_DQ_OK : HTML_SQ_OK;
    {
      String u = HTML.unescapeString(s, off, contentEnd);
      int offu = 0;
      int endu = u.length();
      while (offu < endu) {
        offu = transition(u, offu, endu);
      }
      off = contentEnd;
    }
    if (contentEnd == end) { return end; }  // Remain inside the attribute.
    rtable = null;
    if (context.delim == Context.Delim.SpaceOrTagEnd) {
      // Close the quote introduced in tTag.
      out.write('"');
    } else {
      emit(s, off, valueEnd);
    }
    // On exiting an attribute, we discard all state information
    // except the state and element.
    context = new Context(
        Context.State.Tag, Context.Delim.None,
        Context.URLPart.None, Context.JSCtx.Regexp, Context.Attr.None,
        context.element);
    return valueEnd;
  }

  private int transition(String s, int off, int end)
      throws IOException, TemplateException {
    switch (context.state) {
      case Text:        return tText(s, off, end);
      case Tag:         return tTag(s, off, end);
      case AttrName:    return tAttrName(s, off, end);
      case AfterName:   return tAfterName(s, off, end);
      case BeforeValue: return tBeforeValue(s, off, end);
      case HTMLCmt:     return tHTMLCmt(s, off, end);
      case RCDATA:      return tRCDATA(s, off, end);
      case Attr:        return tAttr(s, off, end);
      case URL:         return tURL(s, off, end);
      case JS:          return tJS(s, off, end);
      case JSDqStr:     return tJSDelimited(s, off, end);
      case JSSqStr:     return tJSDelimited(s, off, end);
      case JSRegexp:    return tJSDelimited(s, off, end);
      case JSBlockCmt:  return tBlockCmt(s, off, end);
      case JSLineCmt:   return tLineCmt(s, off, end);
      case CSS:         return tCSS(s, off, end);
      case CSSDqStr:    return tCSSStr(s, off, end);
      case CSSSqStr:    return tCSSStr(s, off, end);
      case CSSDqURL:    return tCSSStr(s, off, end);
      case CSSSqURL:    return tCSSStr(s, off, end);
      case CSSURL:      return tCSSStr(s, off, end);
      case CSSBlockCmt: return tBlockCmt(s, off, end);
      case CSSLineCmt:  return tLineCmt(s, off, end);
      case Error:       return end;
    }
    throw new IllegalStateException(context.state.toString());
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
      if (lt+4 <= end && s.charAt(lt+1) == '!' && s.charAt(lt+2) == '-'
          && s.charAt(lt+3) == '-') {
        context = context.withState(Context.State.HTMLCmt).build();
        emit(s, off, lt);  // elide <!--
        return lt+4;
      }
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
        Context.Element e = isEndTag
          ? Context.Element.None : classifyTagName(s, tagStart, tagEnd);
        // We've found an HTML tag.
        context = context.withState(Context.State.Tag).withElement(e).build();
        emit(s, off, isStrippingTags ? lt : tagEnd);
        return tagEnd;
      }
      if (isStrippingTags || !s.regionMatches(lt+1, "!DOCTYPE", 0, 8)) {
        emit(s, off, lt);
        out.write("&lt;");
        off = lt + 1;
      } else {
        emit(s, off, lt + 9);
        off = lt + 9;
      }
    }
  }

  @VisibleForTesting
  void stripTags(String s, Context.Delim delim)
      throws IOException, TemplateException {
    ReplacementTable ortable = rtable;
    ReplacementTable normtable = (delim == Context.Delim.SingleQuote)
        ? NORM_HTML_DQ_OK : NORM_HTML_SQ_OK;
    Writer oout = this.out;
    Context ocontext = this.context;
    this.context = Context.TEXT;
    this.isStrippingTags = true;

    try {
      int off = 0, end = s.length();
      // Using the transition funcs helps us avoid mangling
      // `<div title="1>2">` or `I <3 Ponies!`.
      while (off < end) {
        if (context.delim == Context.Delim.None) {
          if (context.state == Context.State.Text) {
            this.out = oout;
            this.rtable = normtable;
          } else if (context.state == Context.State.RCDATA) {
            int i = findSpecialTagEnd(s, off, end);
            if (i < 0) { break; }
            if (context.element == Context.Element.Textarea) {
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
      this.rtable = ortable;
      this.out = oout;
      this.context = ocontext;
      this.isStrippingTags = false;
    }
  }

  private static Context.Element classifyTagName(String s, int off, int end) {
    if (off + 5 > end) { return Context.Element.None; }
    switch (s.charAt(off)) {
      case 's': case 'S':
        if (matchIgnoreCase(s, off+1, end, "cript")) {
          return Context.Element.Script;
        } else if (matchIgnoreCase(s, off+1, end, "tyle")) {
          return Context.Element.Style;
        }
        break;
      case 't': case 'T':
        if (matchIgnoreCase(s, off+1, end, "extarea")) {
          return Context.Element.Textarea;
        } else if (matchIgnoreCase(s, off+1, end, "itle")) {
          return Context.Element.Title;
        }
        break;
    }
    return Context.Element.None;
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
          context = context.withState(Context.State.Attr)
              .withDelim(delim == '"' ? Context.Delim.DoubleQuote
                         : Context.Delim.SingleQuote)
              .build();
        }
        return end;
      }
    }
    if (s.charAt(i) == '>') {
      emit(s, off, i+1);
      Context.State state;
      if (!isStrippingTags) {
        switch (context.element) {
          case Script: state = Context.State.JS; break;
          case Style: state = Context.State.CSS; break;
          case None: state = Context.State.Text; break;
          default: state = Context.State.RCDATA; break;
        }
      } else {
        // When stripping tags, treat all content as RCDATA to avoid wasting
        // time parsing CSS and JS tokens that are just going to be stripped.
        state = context.element == Context.Element.None
            ? Context.State.Text : Context.State.RCDATA;
      }
      context = context.withState(state).build();
      return i+1;
    }
    Context.State state = Context.State.Tag;
    int j = eatAttrName(s, i, end);
    if (i == j) {
      throw makeTemplateException(
          s, off, i, end,
          "expected space, attr name, or end of tag, but got ");
    }
    Context.Attr attr;
    switch (Attr.attrType(s.substring(i, j))) {
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
    context = context.withState(state).withAttr(attr).build();
    return j;
  }

  /** tAttrName is the context transition function for state AttrName. */
  private int tAttrName(String s, int off, int end)
      throws IOException, TemplateException {
    int i = eatAttrName(s, off, end);
    if (i != end) {
      context = context.withState(Context.State.AfterName).build();
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
      context = context.withState(Context.State.Tag).build();
      emit(s, off, i);
      return i;
    }
    context = context.withState(Context.State.BeforeValue).build();
    // Consume the "=".
    i++;
    emit(s, off, i);
    return i;
  }

  private static final EnumMap<Context.Attr, Context.State> ATTR_START_STATES
    = new EnumMap<Context.Attr, Context.State>(Context.Attr.class);
  static {
    ATTR_START_STATES.put(Context.Attr.None, Context.State.Attr);
    ATTR_START_STATES.put(Context.Attr.Script, Context.State.JS);
    ATTR_START_STATES.put(Context.Attr.Style, Context.State.CSS);
    ATTR_START_STATES.put(Context.Attr.URL, Context.State.URL);
  }

  /** tBeforeValue is the context transition function for stateBeforeValue. */
  private int tBeforeValue(String s, int off, int end) throws IOException {
    int i = eatWhiteSpace(s, off, end);
    if (i == end) {
      emit(s, off, end);
      return end;
    }
    // Find the attribute delimiter.
    Context.Delim delim;
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
    context = context.withState(ATTR_START_STATES.get(context.attr))
      .withDelim(delim)
      .withAttr(Context.Attr.None)
      .build();
    emit(s, off, i);
    return i;
  }

  /** tHTMLCmt is the context transition function for stateHTMLCmt. */
  private int tHTMLCmt(String s, int off, int end) {
    int i = s.indexOf("-->", off);
    if (i != -1) {
      // Do not emit.
      context = Context.TEXT;
      return i+3;
    }
    // Do not emit.
    return end;
  }

  /**
   * SPECIAL_TAG_END_MARKERS maps element types to the character sequence that
   * case-insensitively signals the end of the special tag body.
   */
  private static final EnumMap<Context.Element, String> SPECIAL_TAG_END_MARKERS
    = new EnumMap<Context.Element, String>(Context.Element.class);
  static {
    SPECIAL_TAG_END_MARKERS.put(Context.Element.Script,   "</script");
    SPECIAL_TAG_END_MARKERS.put(Context.Element.Style,    "</style");
    SPECIAL_TAG_END_MARKERS.put(Context.Element.Textarea, "</textarea");
    SPECIAL_TAG_END_MARKERS.put(Context.Element.Title,    "</title");
  }

  private int findSpecialTagEnd(String s, int off, int end) {
    if (context.element != Context.Element.None) {
      for (int i = off; (i = s.indexOf("</", off) + 2) != 1; off = i) {
        int j = eatTagName(s, i, end);
        if (context.element == classifyTagName(s, i, j)) {
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
    rtable = NORM_HTML;
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

  /** tAttr is the context transition function for the attribute state. */
  private int tAttr(String s, int off, int end) throws IOException {
    emit(s, off, end);
    return end;
  }

  /** tURL is the context transition function for the URL state. */
  private int tURL(String s, int off, int end) throws IOException {
    context = nextURLContext(context, s, off, end);
    emit(s, off, end);
    return end;
  }

  private static Context nextURLContext(
      Context context, String s, int off, int end) {
    int i = off;
    while (i < end && s.charAt(i) != '#' && s.charAt(i) != '?') { ++i; }
    if (i < end) {
      context = context.withURLPart(Context.URLPart.QueryOrFrag).build();
    } else if (context.urlPart == Context.URLPart.None
               && end != eatWhiteSpace(s, off, end)) {
      // HTML5 uses "Valid URL potentially surrounded by spaces" for
      // attrs: http://www.w3.org/TR/html5/index.html#attributes-1
      context = context.withURLPart(Context.URLPart.PreQuery).build();
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
          context = context
              .withState(ch == '"'
                         ? Context.State.JSDqStr : Context.State.JSSqStr)
              .withJSCtx(Context.JSCtx.Regexp).build();
          emit(s, off, i+1);
          return i+1;
        case '/':
          updateJSCtx(s, off, i);
          if (i+1 < end) {
            if (s.charAt(i+1) == '/') {
              context = context.withState(Context.State.JSLineCmt).build();
              emit(s, off, i);
              return i+2;
            } else if (s.charAt(i+1) == '*') {
              context = context.withState(Context.State.JSBlockCmt).build();
              emit(s, off, i);
              return i+2;
            }
          }
          switch (context.jsCtx) {
            case Regexp:
              context = context.withState(Context.State.JSRegexp).build();
              emit(s, off, i+1);
              return i+1;
            case DivOp:
              context = context.withJSCtx(Context.JSCtx.Regexp).build();
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
      switch (context.state) {
        case JSDqStr:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '"') { i++; }
          break;
        case JSSqStr:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '\'') { i++; }
          break;
        case JSRegexp:
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
            context = context.withState(Context.State.JS)
                .withJSCtx(Context.JSCtx.DivOp).build();
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
    boolean isJS = context.state == Context.State.JSBlockCmt;
    char replacement = ' ';
    for (int i = off; i < end; ++i) {
      char ch = s.charAt(i);
      if (ch == '*' && i+1 <= end && s.charAt(i+1) == '/') {
        context = context.withState(isJS ? Context.State.JS : Context.State.CSS)
            .build();
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
    if (context.state == Context.State.JSLineCmt) {
      for (; i < end; ++i) {
        char ch = s.charAt(i);
        if (ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029') {
          context = context.withState(Context.State.JS).build();
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
          context = context.withState(Context.State.CSS).build();
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
          while (p > off && isCSSSpace(s.charAt(p-1))) { --p; }
          if (p - 3 >= off) {
            char c0 = s.charAt(p-3), c1 = s.charAt(p-2), c2 = s.charAt(p-1);
            if ((c0 == 'u' || c0 == 'U')
                && (c1 == 'r' || c1 == 'R')
                && (c2 == 'l' || c2 == 'L')) {
              int j = i+1;
              while (j < end && isCSSSpace(s.charAt(j))) { ++j; }
              if (j < end && s.charAt(j) == '"') {
                context = context.withState(Context.State.CSSDqURL).build();
                ++j;
              } else if (j < end && s.charAt(j) == '\'') {
                context = context.withState(Context.State.CSSSqURL).build();
                ++j;
              } else {
                context = context.withState(Context.State.CSSURL).build();
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
                context = context.withState(Context.State.CSSLineCmt).build();
                emit(s, off, i);  // Skip comment open.
                return i + 2;
              case '*':
                context = context.withState(Context.State.CSSBlockCmt).build();
                emit(s, off, i);  // Skip comment open.
                return i + 2;
            }
          }
          break;
        case '"':
          context = context.withState(Context.State.CSSDqStr).build();
          emit(s, off, i+1);
          return i+1;
        case '\'':
          context = context.withState(Context.State.CSSSqStr).build();
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
      switch (context.state) {
        case CSSDqStr: case CSSDqURL:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '"') { ++i; }
          break;
        case CSSSqStr: case CSSSqURL:
          while (i < end && s.charAt(i) != '\\' && s.charAt(i) != '\'') { ++i; }
          break;
        case CSSURL:
          // Unquoted URLs end with a newline or close parenthesis.
          // The below includes the wc (whitespace character) and nl.
          while (i < end && s.charAt(i) != '\\'
                 && !isCSSSpace(s.charAt(i)) && s.charAt(i) != ')') {
            ++i;
          }
          break;
        default:
          throw new AssertionError(context.state.toString());
      }
      if (i == end) {
        String decoded = CSS.decodeCSS(s, off, end);
        context = nextURLContext(context, decoded, 0, decoded.length());
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
        context = context.withState(Context.State.CSS).build();
        emit(s, off, i+1);
        return i+1;
      }
      String decoded = CSS.decodeCSS(s, off, i+1);
      context = nextURLContext(context, decoded, 0, decoded.length());
      emit(s, off, i+1);
      off = i + 1;
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
   * eatTagName returns the largest j such that s[i:j] is a tag name and the
   * tag type.
   */
  private static int eatTagName(String s, int i, int end) {
    if (i == end || !asciiAlpha(s.charAt(i))) {
      return i;
    }
    int j = i + 1;
    while (j < end) {
      char ch = s.charAt(j);
      if (asciiAlphaNum(ch)) {
        j++;
        continue;
      }
      // Allow "x-y" or "x:y" but not "x-", "-y", or "x--y".
      if ((ch == ':' || ch == '-') && j+1 < end
          && asciiAlphaNum(s.charAt(j+1))) {
        j += 2;
        continue;
      }
      break;
    }
    return j;
  }

  /** eatWhiteSpace returns the largest j such that s[i:j] is white space. */
  private static int eatWhiteSpace(String s, int i, int end) {
    for (int j = i; j < end; ++j) {
      switch (s.charAt(j)) {
        case ' ': case '\t': case '\n': case '\f': case '\r':
          break;
        default:
          return j;
      }
    }
    return end;
  }

  private static boolean matchIgnoreCase(
      String s, int off, int end, String lowerCaseMatch) {
    int n = lowerCaseMatch.length();
    if (n != end - off) { return false; }
    for (int i = 0, j = off; i < n; ++i, ++j) {
      char a = s.charAt(j), b = lowerCaseMatch.charAt(i);
      if (a != b && !('A' <= a && a <= 'Z' && (a|32) == b)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCSSSpace(char ch) {
    switch (ch) {
      case '\t': case '\n': case '\f': case '\r': case ' ': return true;
      default: return false;
    }
  }

  private TemplateException makeTemplateException(
      String s, int off, int pos, int end, String msg) {
    String str = s.substring(off, pos) + "^" + s.substring(pos, end);
    return new TemplateException(msg + str);
  }

  private void updateJSCtx(String s, int off, int end) {
    Context.JSCtx jsCtx = JS.nextJSCtx(s, off, end, context.jsCtx);
    if (jsCtx != context.jsCtx) {
      context = context.withJSCtx(jsCtx).build();
    }
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
  private static Context nudge(Context c, Writer out) throws IOException {
    switch (c.state) {
      case Tag:
        // In `<foo {{.}}`, the action should emit an attribute.
        return c.withState(Context.State.AttrName).build();
      case BeforeValue:
        // Emit an open double quote to match that emitted by tTag when it
        // transitions directly into a SpaceOrTagEnd context.
        out.write('"');
        // In `<foo bar={{.}}`, the action is an undelimited value.
        return c.withState(ATTR_START_STATES.get(c.attr))
            .withDelim(Context.Delim.SpaceOrTagEnd)
            .withAttr(Context.Attr.None).build();
      case AfterName:
        // In `<foo bar {{.}}`, the action is an attribute name.
        return c.withState(Context.State.AttrName)
            .withAttr(Context.Attr.None).build();
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
  }

  static final ReplacementTable HTML_SQ_OK
      = new ReplacementTable(HTML.REPLACEMENT_TABLE).add('\'', null);
  static final ReplacementTable HTML_DQ_OK
      = new ReplacementTable(HTML.REPLACEMENT_TABLE).add('"', null);
  static final ReplacementTable NORM_HTML
      = new ReplacementTable(HTML.REPLACEMENT_TABLE).add('&', null);
  static final ReplacementTable NORM_HTML_SQ_OK
      = new ReplacementTable(HTML_SQ_OK).add('&', null);
  static final ReplacementTable NORM_HTML_DQ_OK
      = new ReplacementTable(HTML_DQ_OK).add('&', null);

  private static final Writer DEV_NULL = new Writer()  {
    @Override public void close() throws IOException { /* no-op */ }
    @Override public void flush() throws IOException { /* no-op */ }
    @Override public void write(char[] cbuf, int off, int len) { /* no-op */ }
    @Override public void write(String s, int off, int len) { /* no-op */ }
    @Override public void write(int c) { /* no-op */ }
 };
}
