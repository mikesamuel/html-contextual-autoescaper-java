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
import java.net.URLDecoder;

import junit.framework.TestCase;

public class URLTest extends TestCase {

  private void assertNormalized(String text, String norm) throws Exception {
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(true, text, buf);
      assertEquals(norm, buf.toString());
    }
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(true, norm, buf);
      assertEquals(norm, buf.toString());
    }
    URLDecoder.decode(norm, "UTF-8");
  }

  public final void testURLNormalizer() throws Exception {
    assertNormalized("", "");
    assertNormalized(
        "http://example.com:80/foo/bar?q=foo%20&bar=x+y#frag",
        "http://example.com:80/foo/bar?q=foo%20&bar=x+y#frag");
    assertNormalized(" ", "%20");
    assertNormalized("%7c", "%7c");
    assertNormalized("%7C", "%7C");
    assertNormalized("%2", "%252");
    assertNormalized("%", "%25");
    assertNormalized("%z", "%25z");
    assertNormalized("/foo|bar/%5c\u1234", "/foo%7cbar/%5c%e1%88%b4");
  }

  public final void testURLFilters() throws Exception {
    String input = (
        "\0\1\2\3\4\5\6\7\10\t\n\13\14\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\"#$%&'()*+,-./" +
        "0123456789:;<=>?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\]^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\u2028\u2029\ufeff\ud834\udd1e");

    String urlEscaped = (
        "%00%01%02%03%04%05%06%07%08%09%0a%0b%0c%0d%0e%0f" +
        "%10%11%12%13%14%15%16%17%18%19%1a%1b%1c%1d%1e%1f" +
        "%20%21%22%23%24%25%26%27%28%29%2a%2b%2c-.%2f" +
        "0123456789%3a%3b%3c%3d%3e%3f" +
        "%40ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ%5b%5c%5d%5e_" +
        "%60abcdefghijklmno" +
        "pqrstuvwxyz%7b%7c%7d~%7f" +
        "%c2%a0%c4%80%e2%80%a8%e2%80%a9%ef%bb%bf%f0%9d%84%9e");
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(false, input, buf);
      assertEquals(urlEscaped, buf.toString());
    }

    String urlNormalized = (
        "%00%01%02%03%04%05%06%07%08%09%0a%0b%0c%0d%0e%0f" +
        "%10%11%12%13%14%15%16%17%18%19%1a%1b%1c%1d%1e%1f" +
        "%20!%22#$%25&%27%28%29*+,-./" +
        "0123456789:;%3c=%3e?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[%5c]%5e_" +
        "%60abcdefghijklmno" +
        "pqrstuvwxyz%7b%7c%7d~%7f" +
        "%c2%a0%c4%80%e2%80%a8%e2%80%a9%ef%bb%bf%f0%9d%84%9e");
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(true, input, buf);
      assertEquals(urlNormalized, buf.toString());
    }
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(true, urlNormalized, buf);
      assertEquals(urlNormalized, buf.toString());
    }
    {
      StringWriter buf = new StringWriter();
      URL.escapeOnto(true, urlEscaped, buf);
      assertEquals(urlEscaped, buf.toString());
    }
  }

  public final void testURLEncode() throws Exception {
    // This tests the custom UTF-8 encoding code in URL.escapeOnto.
    // Step by a value that is < 128 and coprime with 2**n to sample a
    // representative range of codepoints.
    for (int i = 0; i < Character.MAX_CODE_POINT; i += 101) {
      String input = new StringBuilder(2).appendCodePoint(i).toString();
      StringWriter buf = new StringWriter();
      URL.escapeOnto(false, input, buf);
      String encoded = buf.toString();
      String actual = URLDecoder.decode(encoded, "UTF-8");

      String expected = input;
      if (i < 0x10000 && Character.isSurrogate((char) i)) {
        // Orphaned surrogates need not round-trip properly.
        expected = "\uFFFD";
      }

      assertEquals(
          "i=" + Integer.toHexString(i) + ": " + encoded, expected, actual);

      StringWriter buf2 = new StringWriter();
      URL.escapeOnto(
          ("x" + input).toCharArray(), 1, 1 + input.length(), false, buf2);
      assertEquals(encoded, buf2.toString());
    }
  }

  public final void testURLPrefixAllowed() throws Exception {
    String[] ok = {
      "http://example.com/",
      "HTTP://example.com/",
      "HTTP://example.com/path",
      "HTTP://example.com/path?query",
      "http://example.com:80/path?query#frag",
      "https://example.com/",
      "Https://example.com:334/",
      "http://wiki.ecmascript.org/",
      "http://ecmascript.org/",
      "http://javascript.about.org:80/",
      "mailto:foo@example.com",
      "mailto:First Last <foo@example.com>",
      "//example.com/path",
      "//example.com/path?query",
      "//example.com/path?query#frag",
      "//example.com:8080/path?query#frag",
      "//javascript.about.com/javaccript",
      "/path",
      "/path?query",
      "/path?query#frag",
      "path",
      "path?query",
      "path?query#frag",
      "?query",
      "#frag",
      "alert(1337)",
    };

    String[] bad = {
      "javascript:alert(1337)",
      "JaVaScRiPt:alert(1337)",
      "JaVa%73cRiPt:alert(1337)",
      "JaVa&#115cRiPt:alert(1337)",
      "java\\script:alert(1337)",
      "vbscript:alert(1337)",
      "coffeescript:alert 1337",
      "script:alert(1337)",
      "ja\0vaS\nscript:evil",
      " javascript:alert(1337)",
      "\njavascript:alert(1337)",
      "data:text/javascript,alert(1337)",
    };

    for (String okUrl : ok) {
      String s2 = "javascript:" + okUrl + "/";
      assertTrue(
          okUrl, URL.urlPrefixAllowed(okUrl, 0, okUrl.length()));
      assertTrue(
          okUrl, URL.urlPrefixAllowed(s2, 11, 11 + okUrl.length()));
      assertTrue(
          okUrl, URL.urlPrefixAllowed(okUrl.toCharArray(), 0, okUrl.length()));
      assertTrue(
          okUrl,
          URL.urlPrefixAllowed(s2.toCharArray(), 11, 11 + okUrl.length()));
    }

    for (String badUrl : bad) {
      String s2 = "/'" + badUrl + ":";
      assertFalse(
          badUrl, URL.urlPrefixAllowed(badUrl, 0, badUrl.length()));
      assertFalse(
          badUrl, URL.urlPrefixAllowed(s2, 2, 2 + badUrl.length()));
      assertFalse(
          badUrl,
          URL.urlPrefixAllowed(badUrl.toCharArray(), 0, badUrl.length()));
      assertFalse(
          badUrl,
          URL.urlPrefixAllowed(s2.toCharArray(), 2, 2 + badUrl.length()));
    }
  }
}
