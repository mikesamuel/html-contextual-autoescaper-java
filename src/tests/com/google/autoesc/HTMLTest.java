package com.google.autoesc;

import junit.framework.TestCase;

public class HTMLTest extends TestCase {
  public final void testDecode() {
    assertEquals("", HTML.unescapeString("", 0, 0));
    assertEquals("foo", HTML.unescapeString("foo", 0, 3));
    assertEquals("fo", HTML.unescapeString("foo", 0, 2));
    assertEquals("oo", HTML.unescapeString("foo", 1, 3));
    assertEquals("<foo>", HTML.unescapeString("&lt;foo&gt;", 0, 11));
    assertEquals("<foo>", HTML.unescapeString("&lt;foo&gt", 0, 10));
    assertEquals("<foo>", HTML.unescapeString("&#x3c;foo&#62;", 0, 14));
    assertEquals("a&unknown;b", HTML.unescapeString("a&unknown;b", 0, 11));
    assertEquals("a&unknown", HTML.unescapeString("a&unknown;b", 0, 9));
    assertEquals("\"\"", HTML.unescapeString("&quot;&quot;", 0, 12));
    assertEquals("\u00A1", HTML.unescapeString("&iexcl;", 0, 6));
  }
}
