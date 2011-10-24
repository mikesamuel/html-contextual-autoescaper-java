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
  static final int TEXT = 0;

  /** Context inside the body of a generic tag. */
  static final int GENERIC_TAG = State.Tag;

  public static String toString(int ctx) {
    StringBuilder sb = new StringBuilder();
    switch (state(ctx)) {
      case State.Text: sb.append("Text"); break;
      case State.TagName: sb.append("TagName"); break;
      case State.Tag: sb.append("Tag"); break;
      case State.AttrName: sb.append("AttrName"); break;
      case State.AfterName: sb.append("AfterName"); break;
      case State.BeforeValue: sb.append("BeforeValue"); break;
      case State.HTMLCmt: sb.append("HTMLCmt"); break;
      case State.RCDATA: sb.append("RCDATA"); break;
      case State.Attr: sb.append("Attr"); break;
      case State.URL: sb.append("URL"); break;
      case State.JS: sb.append("JS"); break;
      case State.JSDqStr: sb.append("JSDqStr"); break;
      case State.JSSqStr: sb.append("JSSqStr"); break;
      case State.JSRegexp: sb.append("JSRegexp"); break;
      case State.JSBlockCmt: sb.append("JSBlockCmt"); break;
      case State.JSLineCmt: sb.append("JSLineCmt"); break;
      case State.CSS: sb.append("CSS"); break;
      case State.CSSDqStr: sb.append("CSSDqStr"); break;
      case State.CSSSqStr: sb.append("CSSSqStr"); break;
      case State.CSSDqURL: sb.append("CSSDqURL"); break;
      case State.CSSSqURL: sb.append("CSSSqURL"); break;
      case State.CSSURL: sb.append("CSSURL"); break;
      case State.CSSBlockCmt: sb.append("CSSBlockCmt"); break;
      case State.CSSLineCmt: sb.append("CSSLineCmt"); break;
      default:
        throw new AssertionError("bad state " + Integer.toString(ctx, 16));
    }
    switch (delim(ctx)) {
      case Delim.None: break;
      case Delim.DoubleQuote: sb.append(" DoubleQuote"); break;
      case Delim.SingleQuote: sb.append(" SingleQuote"); break;
      case Delim.SpaceOrTagEnd: sb.append(" SpaceOrTagEnd"); break;
      default:
        throw new AssertionError("bad delim " + Integer.toString(ctx, 16));
    }
    switch (urlPart(ctx)) {
      case URLPart.None: break;
      case URLPart.PreQuery: sb.append(" PreQuery"); break;
      case URLPart.QueryOrFrag: sb.append(" QueryOrFrag"); break;
      default:
        throw new AssertionError("bad urlPart " + Integer.toString(ctx, 16));
    }
    switch (jsCtx(ctx)) {
      case JSCtx.Regexp: break;
      case JSCtx.DivOp: sb.append(" DivOp"); break;
      case JSCtx.Unknown: sb.append(" Unknown"); break;
      default:
        throw new AssertionError("bad jsCtx " + Integer.toString(ctx, 16));
    }
    switch (attr(ctx)) {
      case Attr.None: break;
      case Attr.Script: sb.append(" Script"); break;
      case Attr.Style: sb.append(" Style"); break;
      case Attr.URL: sb.append(" URL"); break;
      default:
        throw new AssertionError("bad attr " + Integer.toString(ctx, 16));
    }
    switch (element(ctx)) {
      case Element.None: break;
      case Element.Script: sb.append(" Script"); break;
      case Element.Style: sb.append(" Style"); break;
      case Element.Textarea: sb.append(" Textarea"); break;
      case Element.Title: sb.append(" Title"); break;
      default:
        throw new AssertionError("bad element " + Integer.toString(ctx, 16));
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
       * HTMLCmt occurs inside an {@code <!-- HTML comment -->}.
       */
      HTMLCmt = 6 << SHIFT,
      /**
       * RCDATA occurs inside an RCDATA element ({@code<textarea>} or
       * {@code <title>}) as described at
       * http://dev.w3.org/html5/spec/syntax.html#elements-0
       */
      RCDATA = 7 << SHIFT,
      /**
       * Attr occurs inside an HTML attribute whose content is text.
       */
      Attr = 8 << SHIFT,
      /**
       * URL occurs inside an HTML attribute whose content is a URL.
       */
      URL = 9 << SHIFT,
      /**
       * JS occurs inside an event handler or script element.
       */
      JS = 10 << SHIFT,
      /**
       * JSDqStr occurs inside a JavaScript double quoted string.
       */
      JSDqStr = 11 << SHIFT,
      /**
       * JSSqStr occurs inside a JavaScript single quoted string.
       */
      JSSqStr = 12 << SHIFT,
      /**
       * JSRegexp occurs inside a JavaScript regexp literal.
       */
      JSRegexp = 13 << SHIFT,
      /**
       * JSBlockCmt occurs inside a JavaScript
       * {@code /* block comment *}{@code /}.
       */
      JSBlockCmt = 14 << SHIFT,
      /**
       * JSLineCmt occurs inside a JavaScript {@code // line comment}.
       */
      JSLineCmt = 15 << SHIFT,
      /**
       * CSS occurs inside a {@code <style>} element or style attribute.
       */
      CSS = 16 << SHIFT,
      /**
       * CSSDqStr occurs inside a CSS double quoted string.
       */
      CSSDqStr = 17 << SHIFT,
      /**
       * CSSSqStr occurs inside a CSS single quoted string.
       */
      CSSSqStr = 18 << SHIFT,
      /**
       * CSSDqURL occurs inside a CSS double quoted {@code url("...")}.
       */
      CSSDqURL = 19 << SHIFT,
      /**
       * CSSSqURL occurs inside a CSS single quoted {@code url('...')}.
       */
      CSSSqURL = 20 << SHIFT,
      /**
       * CSSURL occurs inside a CSS unquoted {@code url(...)}.
       */
      CSSURL = 21 << SHIFT,
      /**
       * CSSBlockCmt occurs inside a CSS {@code /* block comment *}{@code /}.
       */
      CSSBlockCmt = 22 << SHIFT,
      /**
       * CSSLineCmt occurs inside a CSS {@code // line comment}.
       */
      CSSLineCmt = 23 << SHIFT;

    /**
     * isComment is true for any state that contains content meant for template
     * authors and maintainers, not for end-users or machines.
     */
    static boolean isComment(int ctx) {
      switch (ctx & MASK) {
        case HTMLCmt:
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
      Title = 4 << SHIFT;
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
