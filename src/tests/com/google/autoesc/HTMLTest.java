package com.google.autoesc;

import java.io.StringWriter;

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

  public final void testEscapeOnto() throws Exception {
    String input = (
        "\0\1\2\3\4\5\6\7\10\t\n\13\14\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\"#$%&'()*+,-./" +
        "0123456789:;<=>?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ufdec\ud834\udd13");

    String want = (
        "\ufffd\1\2\3\4\5\6\7\10\t\n\13\14\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !&#34;#$%&amp;&#39;()*&#43;,-./" +
        "0123456789:;&lt;&#61;&gt;?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "&#96;abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ufdec\ud834\udd13");

    StringWriter buf = new StringWriter();
    HTML.REPLACEMENT_TABLE.escapeOnto(input, buf);
    String got = buf.toString();
    assertEquals(want, got);

    String decoded = HTML.unescapeString(got, 0, got.length());
    String wantDecoded = input.replace("\0", "\ufffd");
    assertEquals(wantDecoded, decoded);
  }

  private void assertStrippedTags(String html, String htmlNoTags)
      throws Exception {
    StringWriter buf = new StringWriter();
    HTMLEscapingWriter w = new HTMLEscapingWriter(buf);
    w.stripTags(html, Context.Delim.DoubleQuote);
    assertEquals(buf.toString(), htmlNoTags);
  }

  public final void testStripTags() throws Exception {
    assertStrippedTags("", "");
    assertStrippedTags("Hello, World!", "Hello, World!");
    assertStrippedTags("foo&amp;bar", "foo&amp;bar");
    assertStrippedTags("Hello <a href=\"www.example.com/\">World</a>!",
                       "Hello World!");
    assertStrippedTags("Foo <textarea>Bar</textarea> Baz", "Foo Bar Baz");
    assertStrippedTags("Foo <!-- Bar --> Baz", "Foo  Baz");
    assertStrippedTags("<", "&lt;");
    assertStrippedTags("foo < bar", "foo &lt; bar");
    assertStrippedTags("foo &amp; bar", "foo &amp; bar");
    assertStrippedTags("foo & bar", "foo & bar");  // OK if "foo &amp; bar"
    assertStrippedTags(
        "Foo<script type=\"text/javascript\">alert(1337)</script>Bar",
        "FooBar");
    assertStrippedTags("Foo<div title=\"1>2\">Bar", "FooBar");
    assertStrippedTags("I <3 Ponies!", "I &lt;3 Ponies!");
    assertStrippedTags("<script>foo()</script>", "");
    assertStrippedTags("x<script>foo()</script>y", "xy");
  }
}
