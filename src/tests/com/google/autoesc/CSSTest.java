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

import java.io.StringWriter;
import junit.framework.TestCase;

public class CSSTest extends TestCase {

  public final void testIsCSSNmchar() throws Exception {
    assertEquals(CSS.isCSSNmchar(0), false);
    assertEquals(CSS.isCSSNmchar('0'), true);
    assertEquals(CSS.isCSSNmchar('9'), true);
    assertEquals(CSS.isCSSNmchar('A'), true);
    assertEquals(CSS.isCSSNmchar('Z'), true);
    assertEquals(CSS.isCSSNmchar('a'), true);
    assertEquals(CSS.isCSSNmchar('z'), true);
    assertEquals(CSS.isCSSNmchar('_'), true);
    assertEquals(CSS.isCSSNmchar('-'), true);
    assertEquals(CSS.isCSSNmchar(':'), false);
    assertEquals(CSS.isCSSNmchar(';'), false);
    assertEquals(CSS.isCSSNmchar(' '), false);
    assertEquals(CSS.isCSSNmchar(0x7f), false);
    assertEquals(CSS.isCSSNmchar(0x80), true);
    assertEquals(CSS.isCSSNmchar(0x1234), true);
    assertEquals(CSS.isCSSNmchar(0xd800), false);
    assertEquals(CSS.isCSSNmchar(0xdc00), false);
    assertEquals(CSS.isCSSNmchar(0xfffe), false);
    assertEquals(CSS.isCSSNmchar(0x10000), true);
    assertEquals(CSS.isCSSNmchar(0x110000), false);
  }

  private void assertDecoded(String css, String want) throws Exception {
    String decoded = CSS.decodeCSS(css, 0, css.length());
    assertEquals(want, decoded);
    assertEquals(want, CSS.decodeCSS("foo" + css + "bar", 3, 3 + css.length()));

    // decodeCSS is the dual of escapeStrOnto.
    StringWriter buf = new StringWriter();
    CSS.escapeStrOnto(decoded, buf);
    String recoded = buf.toString();
    assertEquals(want, CSS.decodeCSS(recoded, 0, recoded.length()));
  }

  public final void testDecodeCSS() throws Exception {
    assertDecoded("", "");
    assertDecoded("foo", "foo");
    assertDecoded("foo\\", "foo");
    assertDecoded("foo\\\\", "foo\\");
    assertDecoded("\\", "");
    assertDecoded("\\A", "\n");
    assertDecoded("\\a", "\n");
    assertDecoded("\\0a", "\n");
    assertDecoded("\\00000a", "\n");
    assertDecoded("\\000000a", "\u0000a");
    assertDecoded("\\1234 5", "\u1234" + "5");
    assertDecoded("\\1234\\20 5", "\u1234" + " 5");
    assertDecoded("\\1234\\A 5", "\u1234" + "\n5");
    assertDecoded("\\1234\t5", "\u1234" + "5");
    assertDecoded("\\1234\n5", "\u1234" + "5");
    assertDecoded("\\1234\r\n5", "\u1234" + "5");
    assertDecoded(
        "\\12345",
        new StringBuilder().appendCodePoint(0x12345).toString());
    assertDecoded("\\\\", "\\");
    assertDecoded("\\\\ ", "\\ ");
    assertDecoded("\\\"", "\"");
    assertDecoded("\\'", "'");
    assertDecoded("\\.", ".");
    assertDecoded("\\. .", ". .");
    assertDecoded(
        "The \\3c i\\3equick\\3c/i\\3e,\\d\\A\\3cspan style=\\27 color:brown\\27\\3e brown\\3c/span\\3e  fox jumps\\2028over the \\3c canine class=\\22lazy\\22 \\3e dog\\3c/canine\\3e",
        "The <i>quick</i>,\r\n<span style='color:brown'>brown</span> fox jumps\u2028over the <canine class=\"lazy\">dog</canine>");
  }

  public final void testHexDecode() throws Exception {
    for (int i = 0; i < 0x200000; i += 101) {  // coprime with 16
      String s = Integer.toString(i, 16);
      assertEquals(i, CSS.hexDecode(s, 0, s.length()));
      assertEquals(i, CSS.hexDecode(s.toUpperCase(), 0, s.length()));
      assertEquals(i, CSS.hexDecode("a" + s + "f", 1, 1 + s.length()));
    }
  }

  private void assertSkipSpace(String css, String skipped) {
    String padded = "  " + css + "  ";
    int x = CSS.skipCSSSpace(padded, 2, css.length() + 2);
    assertEquals(css, skipped, padded.substring(x, css.length() + 2));
  }

  public final void testSkipCSSSpace() throws Exception {
    assertSkipSpace("", "");
    assertSkipSpace("foo", "foo");
    assertSkipSpace("\n", "");
    assertSkipSpace("\r\n", "");
    assertSkipSpace("\r", "");
    assertSkipSpace("\t", "");
    assertSkipSpace(" ", "");
    assertSkipSpace("\f", "");
    assertSkipSpace(" foo", "foo");
    assertSkipSpace("  foo", " foo");
    assertSkipSpace("\\20", "\\20");
  }

  public final void testCSSEscaper() throws Exception {
    String input = (
        "\0\1\2\3\4\5\6\7\10\t\n\13\14\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\"#$%&'()*+,-./" +
        "0123456789:;<=>?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ud834\udd13");

    String want = (
        "\\0\1\2\3\4\5\6\7\10\\9 \\a\13\\c \\d\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\\22#$%\\26\\27\\28\\29*\\2b,-.\\2f " +
        "0123456789\\3a\\3b\\3c=\\3e?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\\\]^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz\\7b|\\7d~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ud834\udd13");

    StringWriter buf = new StringWriter();
    CSS.escapeStrOnto(input, buf);
    assertEquals(want, buf.toString());
  }

  private void assertFilteredValue(String css, String filtered)
      throws Exception {
    StringWriter buf = new StringWriter();
    CSS.filterValueOnto(css, buf);
    assertEquals(filtered, buf.toString());
  }

  public final void testCSSValueFilter() throws Exception {
    assertFilteredValue("", "");
    assertFilteredValue("foo", "foo");
    assertFilteredValue("0", "0");
    assertFilteredValue("0px", "0px");
    assertFilteredValue("-5px", "-5px");
    assertFilteredValue("1.25in", "1.25in");
    assertFilteredValue("+.33em", "+.33em");
    assertFilteredValue("100%", "100%");
    assertFilteredValue("12.5%", "12.5%");
    assertFilteredValue(".foo", ".foo");
    assertFilteredValue("#bar", "#bar");
    assertFilteredValue("corner-radius", "corner-radius");
    assertFilteredValue("-moz-corner-radius", "-moz-corner-radius");
    assertFilteredValue("#000", "#000");
    assertFilteredValue("#48f", "#48f");
    assertFilteredValue("#123456", "#123456");
    assertFilteredValue("U+00-FF, U+980-9FF", "U+00-FF, U+980-9FF");
    assertFilteredValue("color: red", "color: red");
    assertFilteredValue("<!--", "ZautoescZ");
    assertFilteredValue("-->", "ZautoescZ");
    assertFilteredValue("<![CDATA[", "ZautoescZ");
    assertFilteredValue("]]>", "ZautoescZ");
    assertFilteredValue("</style", "ZautoescZ");
    assertFilteredValue("\"", "ZautoescZ");
    assertFilteredValue("'", "ZautoescZ");
    assertFilteredValue("`", "ZautoescZ");
    assertFilteredValue("\0", "ZautoescZ");
    assertFilteredValue("/* foo */", "ZautoescZ");
    assertFilteredValue("//", "ZautoescZ");
    assertFilteredValue("[href=~", "ZautoescZ");
    assertFilteredValue("expression(alert(1337))", "ZautoescZ");
    assertFilteredValue("-expression(alert(1337))", "ZautoescZ");
    assertFilteredValue("expression", "ZautoescZ");
    assertFilteredValue("Expression", "ZautoescZ");
    assertFilteredValue("EXPRESSION", "ZautoescZ");
    assertFilteredValue("-moz-binding", "ZautoescZ");
    assertFilteredValue("-expr\0ession(alert(1337))", "ZautoescZ");
    assertFilteredValue("-expr\\0ession(alert(1337))", "ZautoescZ");
    assertFilteredValue("-express\\69on(alert(1337))", "ZautoescZ");
    assertFilteredValue("-express\\69 on(alert(1337))", "ZautoescZ");
    assertFilteredValue("-exp\\72 ession(alert(1337))", "ZautoescZ");
    assertFilteredValue("-exp\\52 ession(alert(1337))", "ZautoescZ");
    assertFilteredValue("-exp\\000052 ession(alert(1337))", "ZautoescZ");
    assertFilteredValue("-expre\\0000073sion", "-expre\u00073sion");
    assertFilteredValue("@import url evil.css", "ZautoescZ");
    assertFilteredValue("@import evil.css", "ZautoescZ");
  }
}
