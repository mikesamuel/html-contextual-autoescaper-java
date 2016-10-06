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

@SuppressWarnings("javadoc")
public final class SafeContentTest extends TestCase {

  private static final Object[] INPUTS = {
    "<b> \"foo%\" O'Reilly &bar;",
    new SafeContentString("a[href =~ \"//example.com\"]#foo", ContentType.CSS),
    new SafeContentString("Hello, <b>World</b> &amp;tc!", ContentType.Markup),
    new SafeContentString("dir=\"ltr\" title=\"x<y\"", ContentType.Attr),
    new SafeContentString("c && alert(\"Hello, World!\");", ContentType.JS),
    new SafeContentString("Hello, World & O'Reilly\\x21", ContentType.JSStr),
    new SafeContentString("greeting=H%69&addressee=(World)", ContentType.URL),
  };

  /** @param goldens correspond to INPUTS */
  private static void assertInterp(String tmpl, String... goldens)
      throws Exception {
    assertEquals(INPUTS.length, goldens.length);
    int prefixLen = tmpl.indexOf("{{.}}");
    String prefix = tmpl.substring(0, prefixLen);
    String suffix = tmpl.substring(prefixLen + 5);
    for (int i = 0; i < INPUTS.length; ++i) {
      Object input = INPUTS[i];
      String type = input.getClass().getSimpleName() +
          (input instanceof SafeContent
           ? " " + ((SafeContent) input).getContentType()
           : "");
      StringWriter buf = new StringWriter();
      try (HTMLEscapingWriter w = new HTMLEscapingWriter(buf)) {
        w.writeSafe(prefix);
        w.write(input);
        w.writeSafe(suffix);
      }
      String actual = buf.toString();
      String testdesc = "`" + tmpl + "` with " + type + " -> `" + actual + "`";
      assertTrue("prefix " + testdesc, actual.startsWith(prefix));
      assertTrue("suffix " + testdesc, actual.endsWith(suffix));
      actual = actual.substring(prefixLen, actual.length() - suffix.length());
      assertEquals(testdesc, goldens[i], actual);

      if (input instanceof String) {
        String sInput = (String) input;
        buf = new StringWriter();
        try (HTMLEscapingWriter w = new HTMLEscapingWriter(buf)) {
          w.writeSafe(prefix);
          w.write(("'`," + sInput).toCharArray(), 3, sInput.length());
          w.writeSafe(suffix);
        }
        String actual2 = buf.toString();
        testdesc = "`" + tmpl + "` char with " + type + " -> `" + actual2 + "`";
        assertTrue(
            "prefix char " + testdesc, actual2.startsWith(prefix));
        assertTrue(
            "suffix char " + testdesc, actual2.endsWith(suffix));
        assertEquals(
            "char equiv " + testdesc, actual,
            actual2.substring(prefixLen, actual2.length() - suffix.length()));
      }
    }
  }

  public static final void testSafeContentInterp() throws Exception {
    // For each content sensitive escaper, see how it does on
    // each of the typed strings above.
    assertInterp("<style>{{.}} { color: blue }</style>",
        "ZautoescZ",
        // Allowed but not escaped.
        "a[href =~ \"//example.com\"]#foo",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ");
    assertInterp("<div style=\"{{.}}\">",
        "ZautoescZ",
        // Allowed and HTML escaped.
        "a[href =~ &#34;//example.com&#34;]#foo",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ");
    assertInterp("{{.}}",
        "&lt;b&gt; &#34;foo%&#34; O&#39;Reilly &amp;bar;",
        "a[href =~ &#34;//example.com&#34;]#foo",
        // Not escaped.
        "Hello, <b>World</b> &amp;tc!",
        "dir=&#34;ltr&#34; title=&#34;x&lt;y&#34;",
        "c &amp;&amp; alert(&#34;Hello, World!&#34;);",
        "Hello, World &amp; O&#39;Reilly\\x21",
        "greeting=H%69&amp;addressee=(World)");
    assertInterp("<a{{.}}>",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        // Allowed and HTML escaped.
        " dir=\"ltr\" title=\"x&lt;y\"",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ");
    assertInterp("<a {{.}}>",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ",
        // Allowed and HTML escaped.
        "dir=\"ltr\" title=\"x&lt;y\"",
        "ZautoescZ",
        "ZautoescZ",
        "ZautoescZ");
    assertInterp("<a title={{.}}>",
        "\"&lt;b&gt; &#34;foo%&#34; O'Reilly &amp;bar;\"",
        "\"a[href =~ &#34;//example.com&#34;]#foo\"",
        // Tags stripped, spaces escaped, entity not re-escaped.
        "\"Hello, World &amp;tc!\"",
        "\"dir=&#34;ltr&#34; title=&#34;x&lt;y&#34;\"",
        "\"c &amp;&amp; alert(&#34;Hello, World!&#34;);\"",
        "\"Hello, World &amp; O'Reilly\\x21\"",
        "\"greeting=H%69&amp;addressee=(World)\"");
    assertInterp("<a title='{{.}}'>",
        "&lt;b&gt; \"foo%\" O&#39;Reilly &amp;bar;",
        "a[href =~ \"//example.com\"]#foo",
        // Tags stripped, entity not re-escaped.
        "Hello, World &amp;tc!",
        "dir=\"ltr\" title=\"x&lt;y\"",
        "c &amp;&amp; alert(\"Hello, World!\");",
        "Hello, World &amp; O&#39;Reilly\\x21",
        "greeting=H%69&amp;addressee=(World)");
    assertInterp("<textarea>{{.}}</textarea>",
        "&lt;b&gt; &#34;foo%&#34; O&#39;Reilly &amp;bar;",
        "a[href =~ &#34;//example.com&#34;]#foo",
        // Angle brackets escaped to prevent injection of close tags, entity
        // not re-escaped.
        "Hello, &lt;b&gt;World&lt;/b&gt; &amp;tc!",
        "dir=&#34;ltr&#34; title=&#34;x&lt;y&#34;",
        "c &amp;&amp; alert(&#34;Hello, World!&#34;);",
        "Hello, World &amp; O&#39;Reilly\\x21",
        "greeting=H%69&amp;addressee=(World)");
    assertInterp("<script>alert({{.}})</script>",
        "'\\x3cb\\x3e \\x22foo%\\x22 O\\x27Reilly \\x26bar;'",
        "'a[href =~ \\x22\\/\\/example.com\\x22]#foo'",
        "'Hello, \\x3cb\\x3eWorld\\x3c\\/b\\x3e \\x26amp;tc!'",
        "'dir=\\x22ltr\\x22 title=\\x22x\\x3cy\\x22'",
        // Not escaped.
        " c && alert(\"Hello, World!\"); ",
        // Escape sequence not over-escaped.
        "'Hello, World \\x26 O\\x27Reilly\\x21'",
        "'greeting=H%69\\x26addressee=\\(World\\)'");
    assertInterp("<button onclick=\"alert({{.}})\">",
        "'\\x3cb\\x3e \\x22foo%\\x22 O\\x27Reilly \\x26bar;'",
        "'a[href =~ \\x22\\/\\/example.com\\x22]#foo'",
        "'Hello, \\x3cb\\x3eWorld\\x3c\\/b\\x3e \\x26amp;tc!'",
        "'dir=\\x22ltr\\x22 title=\\x22x\\x3cy\\x22'",
        // Not JS escaped but HTML escaped.
        " c &amp;&amp; alert(&#34;Hello, World!&#34;); ",
        // Escape sequence not over-escaped.
        "'Hello, World \\x26 O\\x27Reilly\\x21'",
        "'greeting=H%69\\x26addressee=\\(World\\)'");
    assertInterp("<button onclick='alert({{.}})'>",
        "&#39;\\x3cb\\x3e \\x22foo%\\x22 O\\x27Reilly \\x26bar;&#39;",
        "&#39;a[href =~ \\x22\\/\\/example.com\\x22]#foo&#39;",
        "&#39;Hello, \\x3cb\\x3eWorld\\x3c\\/b\\x3e \\x26amp;tc!&#39;",
        "&#39;dir=\\x22ltr\\x22 title=\\x22x\\x3cy\\x22&#39;",
        // Not JS escaped but HTML escaped.
        " c &amp;&amp; alert(\"Hello, World!\"); ",
        // Escape sequence not over-escaped.
        "&#39;Hello, World \\x26 O\\x27Reilly\\x21&#39;",
        "&#39;greeting=H%69\\x26addressee=\\(World\\)&#39;");
    assertInterp("<script>alert(\"{{.}}\")</script>",
        "\\x3cb\\x3e \\x22foo%\\x22 O\\x27Reilly \\x26bar;",
        "a[href =~ \\x22\\/\\/example.com\\x22]#foo",
        "Hello, \\x3cb\\x3eWorld\\x3c\\/b\\x3e \\x26amp;tc!",
        "dir=\\x22ltr\\x22 title=\\x22x\\x3cy\\x22",
        "c \\x26\\x26 alert\\(\\x22Hello, World!\\x22\\);",
        // Escape sequence not over-escaped.
        "Hello, World \\x26 O\\x27Reilly\\x21",
        "greeting=H%69\\x26addressee=\\(World\\)");
    assertInterp("<button onclick='alert(\"{{.}}\")'>",
        "\\x3cb\\x3e \\x22foo%\\x22 O\\x27Reilly \\x26bar;",
        "a[href =~ \\x22\\/\\/example.com\\x22]#foo",
        "Hello, \\x3cb\\x3eWorld\\x3c\\/b\\x3e \\x26amp;tc!",
        "dir=\\x22ltr\\x22 title=\\x22x\\x3cy\\x22",
        "c \\x26\\x26 alert\\(\\x22Hello, World!\\x22\\);",
        // Escape sequence not over-escaped.
        "Hello, World \\x26 O\\x27Reilly\\x21",
        "greeting=H%69\\x26addressee=\\(World\\)");
    assertInterp("<a href=\"?q={{.}}\">",
        "%3cb%3e%20%22foo%25%22%20O%27Reilly%20%26bar%3b",
        "a%5bhref%20%3d~%20%22%2f%2fexample.com%22%5d%23foo",
        "Hello%2c%20%3cb%3eWorld%3c%2fb%3e%20%26amp%3btc%21",
        "dir%3d%22ltr%22%20title%3d%22x%3cy%22",
        "c%20%26%26%20alert%28%22Hello%2c%20World%21%22%29%3b",
        "Hello%2c%20World%20%26%20O%27Reilly%5cx21",
        // Quotes and parens are escaped but %69 is not over-escaped.
        // HTML escaping is done.
        "greeting=H%69&amp;addressee=%28World%29");
    assertInterp("<style>body { background: url('?img={{.}}') }</style>",
        "%3cb%3e%20%22foo%25%22%20O%27Reilly%20%26bar%3b",
        "a%5bhref%20%3d~%20%22%2f%2fexample.com%22%5d%23foo",
        "Hello%2c%20%3cb%3eWorld%3c%2fb%3e%20%26amp%3btc%21",
        "dir%3d%22ltr%22%20title%3d%22x%3cy%22",
        "c%20%26%26%20alert%28%22Hello%2c%20World%21%22%29%3b",
        "Hello%2c%20World%20%26%20O%27Reilly%5cx21",
        // Quotes and parens are escaped but %69 is not over-escaped.
        // HTML escaping is not done.
        "greeting=H%69&addressee=%28World%29");
  }
}
