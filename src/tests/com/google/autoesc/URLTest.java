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
}
