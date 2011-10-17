package com.google.autoesc;

public enum ContentType {
  Plain,
  CSS,
  HTML,
  HTMLAttr,
  JS,
  JSStr,
  URL,
  /**
   * Unsafe is used in attr.go for values that affect how
   * embedded content and network messages are formed, vetted,
   * or interpreted; or which credentials network messages carry.
   */
  Unsafe,
  ;

  public String derefSafeContent(Object o) {
    if (!(o instanceof SafeContent)) { return null; }
    SafeContent c = (SafeContent) o;
    if (c.getContentType() != this) {
      return null;
    }
    return c.toString();
  }
}
