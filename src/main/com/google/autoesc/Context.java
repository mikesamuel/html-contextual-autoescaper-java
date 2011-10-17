package com.google.autoesc;

/**
 * Context describes the state an HTML parser must be in when it reaches the
 * portion of HTML produced by evaluating a particular template node.
 *
 * {@link Context#TEXT} is the start context for a template that produces an
 * HTML fragment as defined at
 * http://www.w3.org/TR/html5/the-end.html#parsing-html-fragments
 * where the context element is null.
 */
final class Context {
  final State state;
  final Delim delim;
  final URLPart urlPart;
  final JSCtx jsCtx;
  final Attr attr;
  final Element element;

  static final Context TEXT = new Context(
      State.Text, Delim.None, URLPart.None, JSCtx.Regexp,
      Attr.None, Element.None);

  static final Context GENERIC_TAG = new Context(
      State.Tag, Delim.None, URLPart.None, JSCtx.Regexp,
      Attr.None, Element.None);

  Context(State state,
          Delim delim,
          URLPart urlPart,
          JSCtx jsCtx,
          Attr attr,
          Element element) {
    this.state = state;
    this.delim = delim;
    this.urlPart = urlPart;
    this.jsCtx = jsCtx;
    this.attr = attr;
    this.element = element;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Context)) { return false; }
    Context d = (Context) o;
    return this.state == d.state
      && this.delim == d.delim
      && this.urlPart == d.urlPart
      && this.jsCtx == d.jsCtx
      && this.attr == d.attr
      && this.element == d.element;
  }

  @Override
  public int hashCode() {
    return state.ordinal()
      | (delim.ordinal()
         | (urlPart.ordinal()
            | (jsCtx.ordinal()
               | (attr.ordinal()
                  | (element.ordinal()
                     << 3)
                  << 3)
               << 3)
            << 3)
         << 5);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(state);
    if (delim != Delim.None) {
      sb.append(' ').append(delim);
    }
    if (urlPart != URLPart.None) {
      sb.append(' ').append(urlPart);
    }
    if (jsCtx != JSCtx.Regexp) {
      sb.append(' ').append(jsCtx);
    }
    if (attr != Attr.None) {
      sb.append(' ').append(attr);
    }
    if (element != Element.None) {
      sb.append(' ').append(element);
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
  enum State {

    /**
     * Text is parsed character data. An HTML parser is in
     * this state when its parse position is outside an HTML tag,
     * directive, comment, and special element body.
     */
    Text,
    /**
     * Tag occurs before an HTML attribute or the end of a tag.
     */
    Tag,
    /**
     * AttrName occurs inside an attribute name.
     * It occurs between the ^'s in ` ^name^ = value`.
     */
    AttrName,
    /**
     * AfterName occurs after an attr name has ended but before any
     * equals sign. It occurs between the ^'s in ` name^ ^= value`.
     */
    AfterName,
    /**
     * BeforeValue occurs after the equals sign but before the value.
     * It occurs between the ^'s in ` name =^ ^value`.
     */
    BeforeValue,
    /**
     * HTMLCmt occurs inside an {@code <!-- HTML comment -->}.
     */
    HTMLCmt,
    /**
     * RCDATA occurs inside an RCDATA element ({@code<textarea>} or
     * {@code <title>}) as described at
     * http://dev.w3.org/html5/spec/syntax.html#elements-0
     */
    RCDATA,
    /**
     * Attr occurs inside an HTML attribute whose content is text.
     */
    Attr,
    /**
     * URL occurs inside an HTML attribute whose content is a URL.
     */
    URL,
    /**
     * JS occurs inside an event handler or script element.
     */
    JS,
    /**
     * JSDqStr occurs inside a JavaScript double quoted string.
     */
    JSDqStr,
    /**
     * JSSqStr occurs inside a JavaScript single quoted string.
     */
    JSSqStr,
    /**
     * JSRegexp occurs inside a JavaScript regexp literal.
     */
    JSRegexp,
    /**
     * JSBlockCmt occurs inside a JavaScript
     * {@code /* block comment *}{@code /}.
     */
    JSBlockCmt,
    /**
     * JSLineCmt occurs inside a JavaScript {@code // line comment}.
     */
    JSLineCmt,
    /**
     * CSS occurs inside a {@code <style>} element or style attribute.
     */
    CSS,
    /**
     * CSSDqStr occurs inside a CSS double quoted string.
     */
    CSSDqStr,
    /**
     * CSSSqStr occurs inside a CSS single quoted string.
     */
    CSSSqStr,
    /**
     * CSSDqURL occurs inside a CSS double quoted {@code url("...")}.
     */
    CSSDqURL,
    /**
     * CSSSqURL occurs inside a CSS single quoted {@code url('...')}.
     */
    CSSSqURL,
    /**
     * CSSURL occurs inside a CSS unquoted {@code url(...)}.
     */
    CSSURL,
    /**
     * CSSBlockCmt occurs inside a CSS {@code /* block comment *}{@code /}.
     */
    CSSBlockCmt,
    /**
     * CSSLineCmt occurs inside a CSS {@code // line comment}.
     */
    CSSLineCmt,
    /**
     * Error is an infectious error state outside any valid
     * HTML/CSS/JS construct.
     */
    Error,
    ;

    /**
     * isComment is true for any state that contains content meant for template
     * authors and maintainers, not for end-users or machines.
     */
    public boolean isComment() {
      switch (this) {
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
    public boolean isInTag() {
      switch (this) {
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
  enum Delim {
    /**
     * None occurs outside any attribute.
     */
    None,
    /**
     * DoubleQuote occurs when a double quote (") closes the attribute.
     */
    DoubleQuote,
    /**
     * SingleQuote occurs when a single quote (') closes the attribute.
     */
    SingleQuote,
    /**
     * SpaceOrTagEnd occurs when a space or right angle bracket ({@code >})
     * closes the attribute.
     */
    SpaceOrTagEnd,
    ;
  }

  /**
   * URLPart identifies a part in an RFC 3986 hierarchical URL to allow different
   * encoding strategies.
   */
  enum URLPart {

    /**
     * None occurs when not in a URL, or possibly at the start:
     * ^ in "^http://auth/path?k=v#frag".
     */
    None,
    /**
     * PreQuery occurs in the scheme, authority, or path; between the
     * ^s in "h^ttp://auth/path^?k=v#frag".
     */
    PreQuery,
    /**
     * QueryOrFrag occurs in the query portion between the ^s in
     * "http://auth/path?^k=v#frag^".
     */
    QueryOrFrag,
    /**
     * Unknown occurs due to joining of contexts both before and
     * after the query separator.
     */
    Unknown,
    ;
  }

  /**
   * JSCtx determines whether a '/' starts a regular expression literal or a
   * division operator.
   */
  enum JSCtx {
    /**
     * Regexp occurs where a '/' would start a regexp literal.
     */
    Regexp,
    /**
     * DivOp occurs where a '/' would start a division operator.
     */
    DivOp,
    /**
     * Unknown occurs where a '/' is ambiguous due to context joining.
     */
    Unknown,
    ;
  }

  /**
   * Element identifies the HTML element when inside a start tag or special
   * body.
   * Certain HTML element (for example {@code <script>} and {@code <style>})
   * have bodies that are treated differently from state Text so the element
   * type is necessary to transition into the correct context at the end of a
   * tag and to identify the end delimiter for the body.
   */
  enum Element {

    /**
     * None occurs outside a special tag or special element body.
     */
    None,
    /**
     * Script corresponds to the raw text {@code <script>} element.
     */
    Script,
    /**
     * Style corresponds to the raw text {@code <style>} element.
     */
    Style,
    /**
     * Textarea corresponds to the RCDATA {@code <textarea>} element.
     */
    Textarea,
    /**
     * Title corresponds to the RCDATA {@code <title>} element.
     */
    Title,
    ;
  }


  /** Attr identifies the most recent HTML attribute when inside a start tag. */
  enum Attr {

    /**
     * None corresponds to a normal attribute or no attribute.
     */
    None,
    /**
     * Script corresponds to an event handler attribute.
     */
    Script,
    /**
     * Style corresponds to the style attribute whose value is CSS.
     */
    Style,
    /**
     * URL corresponds to an attribute whose value is a URL.
     */
    URL,
    ;
  }

  Builder withState(State x) { return new Builder(this).withState(x); }
  Builder withDelim(Delim x) { return new Builder(this).withDelim(x); }
  Builder withURLPart(URLPart x) { return new Builder(this).withURLPart(x); }
  Builder withJSCtx(JSCtx x) { return new Builder(this).withJSCtx(x); }
  Builder withAttr(Attr x) { return new Builder(this).withAttr(x); }
  Builder withElement(Element x) { return new Builder(this).withElement(x); }

  static class Builder {
    private State state;
    private Delim delim;
    private URLPart urlPart;
    private JSCtx jsCtx;
    private Attr attr;
    private Element element;

    Builder(Context c) {
      this.state = c.state;
      this.delim = c.delim;
      this.urlPart = c.urlPart;
      this.jsCtx = c.jsCtx;
      this.attr = c.attr;
      this.element = c.element;
    }

    Builder withState(State x) { this.state = x; return this; }
    Builder withDelim(Delim x) { this.delim = x; return this; }
    Builder withURLPart(URLPart x) { this.urlPart = x; return this; }
    Builder withJSCtx(JSCtx x) { this.jsCtx = x; return this; }
    Builder withAttr(Attr x) { this.attr = x; return this; }
    Builder withElement(Element x) { this.element = x; return this; }
    Context build() {
      return new Context(state, delim, urlPart, jsCtx, attr, element);
    }
  }
}
