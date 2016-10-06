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

import java.io.IOException;
import java.io.StringWriter;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class XMLTest extends TestCase {

  public static final void testEscapeOnto() throws Exception {
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
        "0123456789:;&lt;=&gt;?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "&#96;abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ufdec\ud834\udd13");

    StringWriter buf = new StringWriter();
    HTML.escapeOnto(input, buf);
    String got = buf.toString();
    assertEquals(want, got);

    String decoded = HTML.unescapeString(got, 0, got.length());
    String wantDecoded = input.replace("\0", "\ufffd");
    assertEquals(wantDecoded, decoded);
  }

  public static final void testNormalizeOnto() throws Exception {
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
        " !&#34;#$%&&#39;()*&#43;,-./" +
        "0123456789:;&lt;=&gt;?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "&#96;abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ufdec\ud834\udd13");

    StringWriter buf = new StringWriter();
    HTML.normalizeOnto(input, buf);
    String got = buf.toString();
    assertEquals(want, got);

    String decoded = HTML.unescapeString(got, 0, got.length());
    String wantDecoded = input.replace("\0", "\ufffd");
    assertEquals(wantDecoded, decoded);
  }

  private static void assertCDATAEscaped(String golden, String input)
    throws IOException {
    {
      StringWriter sw = new StringWriter();
      XML.escapeCDATAOnto(input, 0, input.length(), sw);
      assertEquals(input, golden, sw.toString());
    }
    {
      StringWriter sw = new StringWriter();
      XML.escapeCDATAOnto(" " + input + " ", 1, input.length() + 1, sw);
      assertEquals(input, golden, sw.toString());
    }
  }

  public static final void testEscapeCDATAOnto() throws Exception {
    assertCDATAEscaped("", "");
    assertCDATAEscaped("foo", "foo");
    assertCDATAEscaped("I <3 Ponies!", "I <3 Ponies!");
    assertCDATAEscaped("a]b", "a]b");
    assertCDATAEscaped("a]>b", "a]>b");
    assertCDATAEscaped("]]><![CDATA[]>foo", "]>foo");
    assertCDATAEscaped("]]><![CDATA[>foo", ">foo");
    assertCDATAEscaped("]]><![CDATA[>foo]]]]><![CDATA[", ">foo]]");
    assertCDATAEscaped("]]]><![CDATA[", "]");
    assertCDATAEscaped("]]]]><![CDATA[", "]]");
    assertCDATAEscaped("]]><![CDATA[]>", "]>");
    assertCDATAEscaped("]]]]><![CDATA[>", "]\u0000]\u0000>");
  }
}
