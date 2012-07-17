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

/**
 * Context describes the state an HTML parser must be in when it reaches the
 * portion of HTML produced by evaluating a particular template node.
 * <p>
 * {@link Context#TEXT} is the start context for a template that produces an
 * HTML fragment as defined at
 * http://www.w3.org/TR/html5/the-end.html#parsing-html-fragments
 * where the context element is null.
 * <p>
 * Contexts are represented as bitfields of enumerated values which are defined
 * as inner classes of this class.
 */
final class Context {
  private Context() { /* uninstantiable */ }

  /** Context where regular HTML text nodes and tags can appear. */
  static final int TEXT = State.Text;

  /** Context where regular XML text nodes and tags can appear. */
  static final int XML = State.XML;

  /** Context inside the body of a generic tag. */
  static final int GENERIC_TAG = State.Tag;

  private static final String[] STATE_NAMES = new String[State.COUNT];
  static {
    STATE_NAMES[State.Text >> State.SHIFT] = "Text";
    STATE_NAMES[State.TagName >> State.SHIFT] = "TagName";
    STATE_NAMES[State.Tag >> State.SHIFT] = "Tag";
    STATE_NAMES[State.AttrName >> State.SHIFT] = "AttrName";
    STATE_NAMES[State.AfterName >> State.SHIFT] = "AfterName";
    STATE_NAMES[State.BeforeValue >> State.SHIFT] = "BeforeValue";
    STATE_NAMES[State.MarkupCmt >> State.SHIFT] = "MarkupCmt";
    STATE_NAMES[State.RCDATA >> State.SHIFT] = "RCDATA";
    STATE_NAMES[State.CDATA >> State.SHIFT] = "CDATA";
    STATE_NAMES[State.Attr >> State.SHIFT] = "Attr";
    STATE_NAMES[State.URL >> State.SHIFT] = "URL";
    STATE_NAMES[State.JS >> State.SHIFT] = "JS";
    STATE_NAMES[State.JSDqStr >> State.SHIFT] = "JSDqStr";
    STATE_NAMES[State.JSSqStr >> State.SHIFT] = "JSSqStr";
    STATE_NAMES[State.JSRegexp >> State.SHIFT] = "JSRegexp";
    STATE_NAMES[State.JSBlockCmt >> State.SHIFT] = "JSBlockCmt";
    STATE_NAMES[State.JSLineCmt >> State.SHIFT] = "JSLineCmt";
    STATE_NAMES[State.CSS >> State.SHIFT] = "CSS";
    STATE_NAMES[State.CSSDqStr >> State.SHIFT] = "CSSDqStr";
    STATE_NAMES[State.CSSSqStr >> State.SHIFT] = "CSSSqStr";
    STATE_NAMES[State.CSSDqURL >> State.SHIFT] = "CSSDqURL";
    STATE_NAMES[State.CSSSqURL >> State.SHIFT] = "CSSSqURL";
    STATE_NAMES[State.CSSURL >> State.SHIFT] = "CSSURL";
    STATE_NAMES[State.CSSBlockCmt >> State.SHIFT] = "CSSBlockCmt";
    STATE_NAMES[State.CSSLineCmt >> State.SHIFT] = "CSSLineCmt";
    STATE_NAMES[State.XML >> State.SHIFT] = "XML";
  }

  private static final String[] DELIM_NAMES = new String[Delim.COUNT];
  static {
    DELIM_NAMES[Delim.None >> Delim.SHIFT] = "None";
    DELIM_NAMES[Delim.DoubleQuote >> Delim.SHIFT] = "DoubleQuote";
    DELIM_NAMES[Delim.SingleQuote >> Delim.SHIFT] = "SingleQuote";
    DELIM_NAMES[Delim.SpaceOrTagEnd >> Delim.SHIFT] = "SpaceOrTagEnd";
  }

  private static final String[] URLPART_NAMES = new String[URLPart.COUNT];
  static {
    URLPART_NAMES[URLPart.None >> URLPart.SHIFT] = "None";
    URLPART_NAMES[URLPart.PreQuery >> URLPart.SHIFT] = "PreQuery";
    URLPART_NAMES[URLPart.QueryOrFrag >> URLPart.SHIFT] = "QueryOrFrag";
  }

  private static final String[] JSCTX_NAMES = new String[JSCtx.COUNT];
  static {
    JSCTX_NAMES[JSCtx.Regexp >> JSCtx.SHIFT] = "Regexp";
    JSCTX_NAMES[JSCtx.DivOp >> JSCtx.SHIFT] = "DivOp";
    JSCTX_NAMES[JSCtx.Unknown >> JSCtx.SHIFT] = "Unknown";
  }

  private static final String[] ATTR_NAMES = new String[Attr.COUNT];
  static {
    ATTR_NAMES[Attr.None >> Attr.SHIFT] = "None";
    ATTR_NAMES[Attr.Script >> Attr.SHIFT] = "Script";
    ATTR_NAMES[Attr.Style >> Attr.SHIFT] = "Style";
    ATTR_NAMES[Attr.URL >> Attr.SHIFT] = "URL";
  }

  private static final String[] ELEMENT_NAMES = new String[Element.COUNT];
  static {
    ELEMENT_NAMES[Element.None >> Element.SHIFT] = "None";
    ELEMENT_NAMES[Element.Script >> Element.SHIFT] = "Script";
    ELEMENT_NAMES[Element.Style >> Element.SHIFT] = "Style";
    ELEMENT_NAMES[Element.Textarea >> Element.SHIFT] = "Textarea";
    ELEMENT_NAMES[Element.Title >> Element.SHIFT] = "Title";
    ELEMENT_NAMES[Element.XML >> Element.SHIFT] = "XML";
  }

  public static String toString(int ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append(STATE_NAMES[state(ctx) >> State.SHIFT]);
    int delim = delim(ctx);
    if (delim != Delim.None) {
      sb.append(' ').append(DELIM_NAMES[delim >> Delim.SHIFT]);
    }
    int urlPart = urlPart(ctx);
    if (urlPart != URLPart.None) {
      sb.append(' ').append(URLPART_NAMES[urlPart >> URLPart.SHIFT]);
    }
    int jsCtx = jsCtx(ctx);
    if (jsCtx != JSCtx.Regexp) {
      sb.append(' ').append(JSCTX_NAMES[jsCtx >> JSCtx.SHIFT]);
    }
    int attr = attr(ctx);
    if (attr != Attr.None) {
      sb.append(' ').append(ATTR_NAMES[attr >> Attr.SHIFT]);
    }
    int element = element(ctx);
    if (element != Element.None) {
      sb.append(' ').append(ELEMENT_NAMES[element >> Element.SHIFT]);
    }
    return sb.toString();
  }

  /**
   * State describes a high-level HTML parser state.
   *
   * It bounds the top of the element stack, and by extension the HTML insertion
   * mode, but also contains state that does not correspond to anything in the
   * HTML5 parsing algorithm because a single token production in the HTML
   * grammar may contain embedded actions in a template. For instance, the
   * quoted HTML attribute produced by<pre>
   *     &lt;div title="Hello {$World}"&gt;
   * </pre>
   * is a single token in HTML's grammar but in a template spans several nodes.
   */
  static class State {

    private State() { /* uninstantiable */ }

    static final int SHIFT = 0;
    static final int MASK = 0x1f << SHIFT;

    static final int
      /**
       * Text is parsed character data. An HTML parser is in
       * this state when its parse position is outside an HTML tag,
       * directive, comment, and special element body.
       */
      Text = 0 << SHIFT,
      /**
       * TagName occurs inside a tag name.
       */
      TagName = 1 << SHIFT,
      /**
       * Tag occurs before an HTML attribute or the end of a tag.
       */
      Tag = 2 << SHIFT,
      /**
       * AttrName occurs inside an attribute name.
       * It occurs between the ^'s in ` ^name^ = value`.
       */
      AttrName = 3 << SHIFT,
      /**
       * AfterName occurs after an attr name has ended but before any
       * equals sign. It occurs between the ^'s in ` name^ ^= value`.
       */
      AfterName = 4 << SHIFT,
      /**
       * BeforeValue occurs after the equals sign but before the value.
       * It occurs between the ^'s in ` name =^ ^value`.
       */
      BeforeValue = 5 << SHIFT,
      /**
       * MarkupCmt occurs inside an {@code <!-- HTML or XML comment -->}.
       */
      MarkupCmt = 6 << SHIFT,
      /**
       * RCDATA occurs inside an RCDATA element ({@code<textarea>} or
       * {@code <title>}) as described at
       * http://dev.w3.org/html5/spec/syntax.html#elements-0
       */
      RCDATA = 7 << SHIFT,
      /**
       * CDATA occurs inside a {@code <![CDATA[...]]>} section.
       * We allow these in HTML even though they are not standard outside
       * foreign XML elements.  They do have a clear meaning so
       * we normalize their content to HTML Text nodes unless the element is
       * {@link Element#XML} in which case we don't normalize to avoid
       * upsetting idiosyncratic XML parsers.
       */
      CDATA = 8 << SHIFT,
      /**
       * Attr occurs inside an HTML attribute whose content is text.
       */
      Attr = 9 << SHIFT,
      /**
       * URL occurs inside an HTML attribute whose content is a URL.
       */
      URL = 10 << SHIFT,
      /**
       * JS occurs inside an event handler or script element.
       */
      JS = 11 << SHIFT,
      /**
       * JSDqStr occurs inside a JavaScript double quoted string.
       */
      JSDqStr = 12 << SHIFT,
      /**
       * JSSqStr occurs inside a JavaScript single quoted string.
       */
      JSSqStr = 13 << SHIFT,
      /**
       * JSRegexp occurs inside a JavaScript regexp literal.
       */
      JSRegexp = 14 << SHIFT,
      /**
       * JSBlockCmt occurs inside a JavaScript
       * {@code /* block comment *}{@code /}.
       */
      JSBlockCmt = 15 << SHIFT,
      /**
       * JSLineCmt occurs inside a JavaScript {@code // line comment}.
       */
      JSLineCmt = 16 << SHIFT,
      /**
       * CSS occurs inside a {@code <style>} element or style attribute.
       */
      CSS = 17 << SHIFT,
      /**
       * CSSDqStr occurs inside a CSS double quoted string.
       */
      CSSDqStr = 18 << SHIFT,
      /**
       * CSSSqStr occurs inside a CSS single quoted string.
       */
      CSSSqStr = 19 << SHIFT,
      /**
       * CSSDqURL occurs inside a CSS double quoted {@code url("...")}.
       */
      CSSDqURL = 20 << SHIFT,
      /**
       * CSSSqURL occurs inside a CSS single quoted {@code url('...')}.
       */
      CSSSqURL = 21 << SHIFT,
      /**
       * CSSURL occurs inside a CSS unquoted {@code url(...)}.
       */
      CSSURL = 22 << SHIFT,
      /**
       * CSSBlockCmt occurs inside a CSS {@code /* block comment *}{@code /}.
       */
      CSSBlockCmt = 23 << SHIFT,
      /**
       * CSSLineCmt occurs inside a CSS {@code // line comment}.
       */
      CSSLineCmt = 24 << SHIFT,
      /**
       * Indicates that the content is an XML text block which should require
       * less normalization than a normal one.  We do not normalize XML since
       * there are more (and more idiosyncratic especially in web services)
       * XML parsers than HTML parsers.
       */
      XML = 25 << SHIFT;

    private static final int COUNT = 27;

    /**
     * isComment is true for any state that contains content meant for template
     * authors and maintainers, not for end-users or machines.
     */
    static boolean isComment(int ctx) {
      switch (ctx & MASK) {
        case MarkupCmt:
        case JSBlockCmt:
        case JSLineCmt:
        case CSSBlockCmt:
        case CSSLineCmt:
          return true;
        default:
          return false;
      }
    }

    /** isInTag return whether this state occurs solely inside an HTML tag. */
    static boolean isInTag(int ctx) {
      switch (ctx & MASK) {
        case Tag:
        case AttrName:
        case AfterName:
        case BeforeValue:
        case Attr:
          return true;
        default:
          return false;
      }
    }
  }

  /** Delim is the delimiter that will end the current HTML attribute. */
  static class Delim {
    private Delim() { /* uninstantiable */ }

    static final int SHIFT = 5;
    static final int MASK = 0x3 << SHIFT;

    static final int
      /**
       * None occurs outside any attribute.
       */
      None = 0 << SHIFT,
      /**
       * DoubleQuote occurs when a double quote (") closes the attribute.
       */
      DoubleQuote = 1 << SHIFT,
      /**
       * SingleQuote occurs when a single quote (') closes the attribute.
       */
      SingleQuote = 2 << SHIFT,
      /**
       * SpaceOrTagEnd occurs when a space or right angle bracket ({@code >})
       * closes the attribute.
       */
      SpaceOrTagEnd = 3 << SHIFT;

    private static final int COUNT = 4;
  }

  /**
   * URLPart identifies a part in an RFC 3986 hierarchical URL to allow
   * different encoding strategies.
   */
  static class URLPart {
    private URLPart() { /* uninstantiable */ }

    static final int SHIFT = 7;
    static final int MASK = 0x3 << SHIFT;

    static final int
      /**
       * None occurs when not in a URL, or possibly at the start:
       * ^ in "^http://auth/path?k=v#frag".
       */
      None = 0 << SHIFT,
      /**
       * PreQuery occurs in the scheme, authority, or path; between the
       * ^s in "h^ttp://auth/path^?k=v#frag".
       */
      PreQuery = 1 << SHIFT,
      /**
       * QueryOrFrag occurs in the query portion between the ^s in
       * "http://auth/path?^k=v#frag^".
       */
      QueryOrFrag = 2 << SHIFT;

    private static final int COUNT = 3;
  }

  /**
   * JSCtx determines whether a '/' starts a regular expression literal or a
   * division operator.
   */
  static class JSCtx {
    private JSCtx() { /* uninstantiable */ }

    static final int SHIFT = 9;
    static final int MASK = 0x3 << SHIFT;

    static final int
      /**
       * Regexp occurs where a '/' would start a regexp literal.
       */
      Regexp = 0 << SHIFT,
      /**
       * DivOp occurs where a '/' would start a division operator.
       */
      DivOp = 1 << SHIFT,
      /**
       * Unknown occurs where a '/' is ambiguous due to context joining.
       */
      Unknown = 2 << SHIFT;

    private static final int COUNT = 3;
  }

  /**
   * Element identifies the HTML element when inside a start tag or special
   * body.
   * Certain HTML element (for example {@code <script>} and {@code <style>})
   * have bodies that are treated differently from state Text so the element
   * type is necessary to transition into the correct context at the end of a
   * tag and to identify the end delimiter for the body.
   */
  static class Element {
    private Element() { /* uninstantiable */ }

    static final int SHIFT = 11;
    static final int MASK = 0x7 << SHIFT;

    static final int
      /**
       * None occurs outside a special tag or special element body.
       */
      None = 0 << SHIFT,
      /**
       * Script corresponds to the raw text {@code <script>} element.
       */
      Script = 1 << SHIFT,
      /**
       * Style corresponds to the raw text {@code <style>} element.
       */
      Style = 2 << SHIFT,
      /**
       * Textarea corresponds to the RCDATA {@code <textarea>} element.
       */
      Textarea = 3 << SHIFT,
      /**
       * Title corresponds to the RCDATA {@code <title>} element.
       */
      Title = 4 << SHIFT,
      /**
       * XML corresponds to an XML element in an XML document.
       * After XML elements we transition back into an XMLText state.
       */
      XML = 5 << SHIFT;

    private static final int COUNT = 6;
  }


  /** Attr identifies the most recent HTML attribute when inside a start tag. */
  static class Attr {
    private Attr() { /* uninstantiable */ }

    static final int SHIFT = 14;
    static final int MASK = 0x3 << SHIFT;

    static final int
      /**
       * None corresponds to a normal attribute or no attribute.
       */
      None = 0 << SHIFT,
      /**
       * Script corresponds to an event handler attribute.
       */
      Script = 1 << SHIFT,
      /**
       * Style corresponds to the style attribute whose value is CSS.
       */
      Style = 2 << SHIFT,
      /**
       * URL corresponds to an attribute whose value is a URL.
       */
      URL = 3 << SHIFT;

    private static final int COUNT = 4;
  }

  static int state(int ctx, int state) {
    return (ctx & ~State.MASK) | state;
  }
  static int state(int ctx) {
    return ctx & State.MASK;
  }
  static int delim(int ctx, int delim) {
    return (ctx & ~Delim.MASK) | delim;
  }
  static int delim(int ctx) {
    return ctx & Delim.MASK;
  }
  static int urlPart(int ctx, int urlPart) {
    return (ctx & ~URLPart.MASK) | urlPart;
  }
  static int urlPart(int ctx) {
    return ctx & URLPart.MASK;
  }
  static int jsCtx(int ctx, int jsCtx) {
    return (ctx & ~JSCtx.MASK) | jsCtx;
  }
  static int jsCtx(int ctx) {
    return ctx & JSCtx.MASK;
  }
  static int element(int ctx, int element) {
    return (ctx & ~Element.MASK) | element;
  }
  static int element(int ctx) {
    return ctx & Element.MASK;
  }
  static int attr(int ctx, int attr) {
    return (ctx & ~Attr.MASK) | attr;
  }
  static int attr(int ctx) {
    return ctx & Attr.MASK;
  }
}
