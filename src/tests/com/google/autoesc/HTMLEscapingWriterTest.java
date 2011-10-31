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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

public class HTMLEscapingWriterTest extends TestCase {
  private void assertWritten(String html, String outContext) throws Exception {
    assertWritten(html, outContext, html);
  }

  private void assertWritten(String html, String outContext, String normalized)
      throws Exception {
    {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      w.writeSafe(html);
      assertEquals(outContext, Context.toString(w.getContext()));
      assertEquals(normalized, sw.toString());
    }
    {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      char[] chars = html.toCharArray();
      w.writeSafe(chars, 0, chars.length);
      assertEquals(outContext, Context.toString(w.getContext()));
      assertEquals(normalized, sw.toString());
    }
  }

  public final void testWriteSafe() throws Exception {
    assertWritten(
        "",
        "Text"
        );
    assertWritten(
        "Hello, World!",
        "Text"
        );
    assertWritten(
        // An orphaned "<" is OK.
        "I <3 Ponies!",
        "Text",
        "I &lt;3 Ponies!"
        );
    assertWritten(
        "<a",
        "TagName"
        );
    assertWritten(
        "<a ",
        "Tag"
        );
    assertWritten(
        "<a>",
        "Text"
        );
    assertWritten(
        "<a href",
        "AttrName URL"
        );
    assertWritten(
        "<a on",
        "AttrName Script"
        );
    assertWritten(
        "<a href ",
        "AfterName URL"
        );
    assertWritten(
        "<a style  =  ",
        "BeforeValue Style"
        );
    assertWritten(
        "<a href=",
        "BeforeValue URL"
        );
    assertWritten(
        "<a href=x",
        "URL SpaceOrTagEnd PreQuery",
        "<a href=\"x"
        );
    assertWritten(
        "<a href=x ",
        "Tag",
        "<a href=\"x\" "
        );
    assertWritten(
        "<a href=>",
        "Text",
        "<a href=\"\">"
        );
    assertWritten(
        "<a href=x>",
        "Text",
        "<a href=\"x\">"
        );
    assertWritten(
        "<a href ='",
        "URL SingleQuote"
        );
    assertWritten(
        "<a href=''",
        "Tag"
        );
    assertWritten(
        "<a href= \"",
        "URL DoubleQuote"
        );
    assertWritten(
        "<a href=\"\"",
        "Tag"
        );
    assertWritten(
        "<a title=\"",
        "Attr DoubleQuote"
        );
    assertWritten(
        "<a HREF='http:",
        "URL SingleQuote PreQuery"
        );
    assertWritten(
        "<a Href='/",
        "URL SingleQuote PreQuery"
        );
    assertWritten(
        "<a href='\"",
        "URL SingleQuote PreQuery"
        );
    assertWritten(
        "<a href=\"'",
        "URL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a href='&apos;",
        "URL SingleQuote PreQuery",
        "<a href='&#39;"
        );
    assertWritten(
        "<a href=\"&quot;",
        "URL DoubleQuote PreQuery",
        "<a href=\"&#34;"
        );
    assertWritten(
        "<a href=\"&#34;",
        "URL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a href=&quot;",
        "URL SpaceOrTagEnd PreQuery",
        "<a href=\"&#34;"
        );
    assertWritten(
        "<img alt=\"1\">",
        "Text"
        );
    assertWritten(
        "<img alt=\"1>\"",
        "Tag",
        "<img alt=\"1&gt;\""
        );
    assertWritten(
        "<img alt=\"1>\">",
        "Text",
        "<img alt=\"1&gt;\">"
        );
    assertWritten(
        "<input checked type=\"checkbox\"",
        "Tag"
        );
    assertWritten(
        "<a onclick=\"",
        "JS DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"//foo",
        "JSLineCmt DoubleQuote",
        "<a onclick=\""  // comment elided
        );
    assertWritten(
        "<a onclick='//\n",
        "JS SingleQuote",
        "<a onclick='\n"
        );
    assertWritten(
        "<a onclick='//\r\n",
        "JS SingleQuote",
        "<a onclick='\r\n"
        );
    assertWritten(
        "<a onclick='//\u2028",
        "JS SingleQuote",
        "<a onclick='\u2028"
        );
    assertWritten(
        "<a onclick=\"/*",
        "JSBlockCmt DoubleQuote",
        "<a onclick=\""
        );
    assertWritten(
        "<a onclick=\"/*/",
        "JSBlockCmt DoubleQuote",
        "<a onclick=\" "
        );
    assertWritten(
        "<a onclick=\"/**/",
        "JS DoubleQuote",
        "<a onclick=\" "
        );
    assertWritten(
        "<a onkeypress=\"&quot;",
        "JSDqStr DoubleQuote",
        "<a onkeypress=\"&#34;"
        );
    assertWritten(
        "<a onclick='&quot;foo&quot;",
        "JS SingleQuote DivOp",
        "<a onclick='\"foo\""
        );
    assertWritten(
        "<a onclick=&#39;foo&#39;",
        "JS SpaceOrTagEnd DivOp",
        "<a onclick=\"'foo'"
        );
    assertWritten(
        "<a onclick=&#39;foo",
        "JSSqStr SpaceOrTagEnd",
        "<a onclick=\"'foo"
        );
    assertWritten(
        "<a onclick=\"&quot;foo'",
        "JSDqStr DoubleQuote",
        "<a onclick=\"&#34;foo'"
        );
    assertWritten(
        "<a onclick=\"'foo&quot;",
        "JSSqStr DoubleQuote",
        "<a onclick=\"'foo&#34;"
        );
    assertWritten(
        "<A ONCLICK=\"'",
        "JSSqStr DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"/",
        "JSRegexp DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"'foo'",
        "JS DoubleQuote DivOp"
        );
    assertWritten(
        "<a onclick=\"'foo\\'",
        "JSSqStr DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"'foo\\'",
        "JSSqStr DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"/foo/",
        "JS DoubleQuote DivOp"
        );
    assertWritten(
        "<script>/foo/ /=",
        "JS Script"
        );
    assertWritten(
        "<a onclick=\"1 /foo",
        "JS DoubleQuote DivOp"
        );
    assertWritten(
        "<a onclick=\"1 /*c*/ /foo",
        "JS DoubleQuote DivOp",
        "<a onclick=\"1   /foo"
        );
    assertWritten(
        "<a onclick=\"/foo[/]",
        "JSRegexp DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"/foo\\/",
        "JSRegexp DoubleQuote"
        );
    assertWritten(
        "<a onclick=\"/foo/",
        "JS DoubleQuote DivOp"
        );
    assertWritten(
        "<input checked style=\"",
        "CSS DoubleQuote"
        );
    assertWritten(
        "<a style=\"//",
        "CSSLineCmt DoubleQuote",
        "<a style=\""
        );
    assertWritten(
        "<a style=\"//</script>",
        "CSSLineCmt DoubleQuote",
        "<a style=\""
        );
    assertWritten(
        "<a style='//\n",
        "CSS SingleQuote",
        "<a style='\n"
        );
    assertWritten(
        "<a style='//\r",
        "CSS SingleQuote",
        "<a style='\r"
        );
    assertWritten(
        "<a style=\"/*",
        "CSSBlockCmt DoubleQuote",
        "<a style=\""
        );
    assertWritten(
        "<a style=\"/*/",
        "CSSBlockCmt DoubleQuote",
        "<a style=\" "
        );
    assertWritten(
        "<a style=\"/**/",
        "CSS DoubleQuote",
        "<a style=\" "
        );
    assertWritten(
        "<a style=\"background: '",
        "CSSSqStr DoubleQuote"
        );
    assertWritten(
        "<a style=\"background: &quot;",
        "CSSDqStr DoubleQuote",
        "<a style=\"background: &#34;"
        );
    assertWritten(
        "<a style=\"background: '/foo?img=",
        "CSSSqStr DoubleQuote QueryOrFrag"
        );
    assertWritten(
        "<a style=\"background: '/",
        "CSSSqStr DoubleQuote PreQuery"
        );
    assertWritten(
        "<a style=\"background: url(&#x22;/",
        "CSSDqURL DoubleQuote PreQuery",
        "<a style=\"background: url(&#34;/"
        );
    assertWritten(
        "<a style=\"background: url('/",
        "CSSSqURL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a style=\"background: url('/)",
        "CSSSqURL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a style=\"background: url('/ ",
        "CSSSqURL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a style=\"background: url(/",
        "CSSURL DoubleQuote PreQuery"
        );
    assertWritten(
        "<a style=\"background: url( ",
        "CSSURL DoubleQuote"
        );
    assertWritten(
        "<a style=\"background: url( /image?name=",
        "CSSURL DoubleQuote QueryOrFrag"
        );
    assertWritten(
        "<a style=\"background: url(x)",
        "CSS DoubleQuote"
        );
    assertWritten(
        "<a style=\"background: url('x'",
        "CSS DoubleQuote"
        );
    assertWritten(
        "<a style=\"background: url( x ",
        "CSS DoubleQuote"
        );
    assertWritten(
        "<!-- foo",
        "HTMLCmt",
        ""
        );
    assertWritten(
        "<!-->",
        "HTMLCmt",
        ""
        );
    assertWritten(
        "<!--->",
        "HTMLCmt",
        ""
        );
    assertWritten(
        "<!-- foo -->",
        "Text",
        ""
        );
    assertWritten(
        "<h",
        "TagName"
        );
    assertWritten(
        "</h",
        "TagName"
        );
    assertWritten(
        "<script",
        "TagName Script"
        );
    assertWritten(
        "<script ",
        "Tag Script"
        );
    assertWritten(
        "<script src=\"foo.js\" ",
        "Tag Script"
        );
    assertWritten(
        "<script src='foo.js' ",
        "Tag Script"
        );
    assertWritten(
        "<script type=text/javascript ",
        "Tag Script",
        "<script type=\"text/javascript\" "
        );
    assertWritten(
        "<script>foo",
        "JS DivOp Script"
        );
    assertWritten(
        "<script>foo</script>",
        "Text"
        );
    assertWritten(
        "<script>foo</script><!--",
        "HTMLCmt",
        "<script>foo</script>"
        );
    assertWritten(
        "<script>document.write(\"<p>foo</p>\");",
        "JS Script"
        );
    assertWritten(
        "<script>document.write(\"<p>foo<\\/script>\");",
        "JS Script"
        );
    assertWritten(
        "<script>document.write(\"<script>alert(1)</script>\");",
        "Text"
        );
    assertWritten(
        "<Script>",
        "JS Script"
        );
    assertWritten(
        "<SCRIPT>foo",
        "JS DivOp Script"
        );
    assertWritten(
        "<textarea>value",
        "RCDATA Textarea"
        );
    assertWritten(
        "<textarea>value</TEXTAREA>",
        "Text"
        );
    assertWritten(
        "<textarea name=html><b",
        "RCDATA Textarea",
        "<textarea name=\"html\">&lt;b"
        );
    assertWritten(
        "<title>value",
        "RCDATA Title"
        );
    assertWritten(
        "<style>value",
        "CSS Style"
        );
    assertWritten(
        "<a xlink:href",
        "AttrName URL"
        );
    assertWritten(
        "<a xmlns",
        "AttrName URL"
        );
    assertWritten(
        "<a xmlns:foo",
        "AttrName URL"
        );
    assertWritten(
        "<a xmlnsxyz",
        "AttrName"
        );
    assertWritten(
        "<a data-url",
        "AttrName URL"
        );
    assertWritten(
        "<a data-iconUri",
        "AttrName URL"
        );
    assertWritten(
        "<a data-urlItem",
        "AttrName URL"
        );
    assertWritten(
        "<a g:",
        "AttrName"
        );
    assertWritten(
        "<a g:url",
        "AttrName URL"
        );
    assertWritten(
        "<a g:iconUri",
        "AttrName URL"
        );
    assertWritten(
        "<a g:urlItem",
        "AttrName URL"
        );
    assertWritten(
        "<a g:value",
        "AttrName"
        );
    assertWritten(
        "<a svg:style='",
        "CSS SingleQuote"
        );
    assertWritten(
        "<svg:font-face",
        "TagName"
        );
    assertWritten(
        "<svg:a svg:onclick=\"",
        "JS DoubleQuote"
        );
  }

  @SuppressWarnings("serial")
  private static Map<String, Object> SUBSTS
     = new LinkedHashMap<String, Object>() {{
    this.put("F", false);
    this.put("T", true);
    this.put("C", "<Cincinatti>");
    this.put("G", "<Goodbye>");
    this.put("H", "<Hello>");
    this.put("A", Collections.unmodifiableList(Arrays.asList("<a>", "<b>")));
    this.put("E", Collections.emptyList());
    this.put("N", 42);
    this.put("B", new BadMarshaler());
    this.put("M", new GoodMarshaler());
    this.put("W", new SafeContentString(
        "&iexcl;<b class=\"foo\">Hello</b>,"
        + " <textarea>O'World</textarea>!", ContentType.HTML));
    this.put("SU", new SafeContentString("%3cCincinatti%3e", ContentType.URL));
  }};

  private void assertTemplateOutput(String msg, String tmpl, String want)
      throws Exception {
    assertTemplateOutput(msg, tmpl, want, want);
  }

  private static void writeSafeToAll(HTMLEscapingWriter[] ws, String s)
      throws Exception {
    assert 6 == ws.length;
    String salt = "'" + s + "'";
    char[] chars = salt.toCharArray();
    int len = s.length();
    ws[0].writeSafe(s);
    ws[1].writeSafe(salt, 1, 1 + len);
    ws[2].writeSafe(s);
    ws[3].writeSafe(salt, 1, 1 + len);
    ws[4].writeSafe(chars, 1, 1 + len);
    ws[5].writeSafe(chars, 1, 1 + len);
  }

  private void assertTemplateOutput(
      String msg, String tmpl, String wantHard, String wantSoft)
      throws Exception {
    // We want to test a number of implementations:
    // (soft, hard) x (memoizing, non-memoizing, char[])
    StringWriter[] bufs = new StringWriter[] {
        new StringWriter(tmpl.length() * 2),
        new StringWriter(tmpl.length() * 2),
        new StringWriter(tmpl.length() * 2),
        new StringWriter(tmpl.length() * 2),
        new StringWriter(tmpl.length() * 2),
        new StringWriter(tmpl.length() * 2),
    };
    HTMLEscapingWriter[] ws = new HTMLEscapingWriter[] {
        new HTMLEscapingWriter(bufs[0]),
        new HTMLEscapingWriter(bufs[1]),
        new MemoizingHTMLEscapingWriter(bufs[2]),
        new MemoizingHTMLEscapingWriter(bufs[3]),
        new HTMLEscapingWriter(bufs[4]),
        new HTMLEscapingWriter(bufs[5]),
    };
    ws[1].setSoft(true);
    ws[3].setSoft(true);
    ws[5].setSoft(true);

    int off = 0, end = tmpl.length();
    for (int open; (open = tmpl.indexOf("{{", off)) != -1;) {
      int close = tmpl.indexOf("}}", open + 2);
      if (off != open) {
        writeSafeToAll(ws, tmpl.substring(off, open));
      }
      String expr = tmpl.substring(open+2, close).trim();
      if (!"".equals(expr)) {
        Object value;
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
          StringBuilder sb = new StringBuilder(expr.length() - 2);
          int k = 1, n = expr.length() - 1;
          for (int i = 1; i < n; ++i) {
            if (expr.charAt(i) != '\\') { continue; }
            sb.append(expr, k, i);
            char next = expr.charAt(++i);
            switch (next) {
              case 'n': sb.append('\n'); break;
              case 'r': sb.append('\r'); break;
              case 'u':
                sb.append((char) Integer.parseInt(expr.substring(i+1, i+5)));
                i += 4;
                break;
              default: sb.append(next); break;
            }
            k = i + 1;
          }
          value = sb.append(expr.substring(k, n)).toString();
        } else if (isNumberLit(expr)) {
          value = parseAsNumber(expr);
        } else if ("true".equals(expr) || "false".equals(expr)) {
          value = Boolean.valueOf(expr);
        } else if ("null".equals(expr)) {
          value = null;
        } else {
          value = SUBSTS.get(expr);
          if (!SUBSTS.containsKey(expr)) { fail(expr); }
        }
        if (value instanceof String) {
          String svalue = (String) value;
          int vlen = svalue.length();
          svalue = "\"'" + svalue + "'";
          char[] valueChars = svalue.toCharArray();
          ws[0].write(value);
          ws[1].write(value);
          ws[2].write(svalue, 2, vlen);
          ws[3].write(svalue, 2, vlen);
          ws[4].write(valueChars, 2, vlen);
          ws[5].write(valueChars, 2, vlen);
        } else {
          for (HTMLEscapingWriter w : ws) {
            w.write(value);
          }
        }
      }
      off = close + 2;
    }
    if (off != end) {
      writeSafeToAll(ws, tmpl.substring(off));
    }

    assertFalse(ws[0].isSoft());
    assertEquals(msg + ":hard", wantHard, bufs[0].toString());
    assertTrue(ws[1].isSoft());
    assertEquals(msg + ":soft", wantSoft, bufs[1].toString());
    assertFalse(ws[2].isSoft());
    assertEquals(msg + ":hard", wantHard, bufs[2].toString());
    assertTrue(ws[3].isSoft());
    assertEquals(msg + ":soft", wantSoft, bufs[3].toString());
    assertFalse(ws[4].isSoft());
    assertEquals(msg + ":hardchars", wantHard, bufs[4].toString());
    assertTrue(ws[5].isSoft());
    assertEquals(msg + ":softchars", wantSoft, bufs[5].toString());
  }

  public final void testSafeWriter() throws Exception {
    assertTemplateOutput(
            "single value",
            "Hello, {{C}}!",
            "Hello, &lt;Cincinatti&gt;!"
        );
    assertTemplateOutput(
            "single value 2",
            "{{H}}",
            "&lt;Hello&gt;"
        );
    assertTemplateOutput(
            "existing entities in text",
            "{{\"foo&amp <bar>\"}}",
            "foo&amp;amp &lt;bar&gt;",
            "foo&amp &lt;bar&gt;"
        );
    assertTemplateOutput(
            "existing entities in atts",
            "<div title='{{\"foo&amp <o'bar>\"}}'>",
            "<div title='foo&amp;amp &lt;o&#39;bar&gt;'>",
            "<div title='foo&amp &lt;o&#39;bar&gt;'>"
    );
    assertTemplateOutput(
            "nonStringValue",
            "{{T}}",
            "true"
        );
    assertTemplateOutput(
            "constant",
            "<a href=\"/search?q={{\"'a<b'\"}}\">",
            "<a href=\"/search?q=%27a%3cb%27\">"
        );
    assertTemplateOutput(
            "multipleAttrs",
            "<a b=1 c={{H}}>",
            "<a b=\"1\" c=\"&lt;Hello&gt;\">"
        );
    assertTemplateOutput(
            "urlStartRel",
            "<a href='{{\"/foo/bar?a=b&c=d\"}}'>",
            "<a href='/foo/bar?a=b&amp;c=d'>"
        );
    assertTemplateOutput(
            "urlStartAbsOk",
            "<a href='{{\"http://example.com/foo/bar?a=b&c=d\"}}'>",
            "<a href='http://example.com/foo/bar?a=b&amp;c=d'>"
        );
    assertTemplateOutput(
            "protocolRelativeURLStart",
            "<a href='{{\"//example.com:8000/foo/bar?a=b&c=d\"}}'>",
            "<a href='//example.com:8000/foo/bar?a=b&amp;c=d'>"
        );
    assertTemplateOutput(
            "pathRelativeURLStart",
            "<a href=\"{{\"/javascript:80/foo/bar\"}}\">",
            "<a href=\"/javascript:80/foo/bar\">"
        );
    assertTemplateOutput(
            "dangerousURLStart",
            "<a href='{{\"javascript:alert(%22pwned%22)\"}}'>",
            "<a href='#ZautoescZ'>"
        );
    assertTemplateOutput(
            "dangerousURLStart2",
            "<a href='  {{\"javascript:alert(%22pwned%22)\"}}'>",
            "<a href='  #ZautoescZ'>"
        );
    assertTemplateOutput(
            "dangerousURLStart3",
            "<a href='{{\"  \"}}{{\"javascript:alert(%22pwned%22)\"}}'>",
            "<a href='#ZautoescZ'>"
        );
    assertTemplateOutput(
            "innocuousURLStart1",
            "<a href='{{\" /foo/\"}}{{\"javascript:alert(%22pwned%22)\"}}'>",
            "<a href='/foo/javascript:alert%28%22pwned%22%29'>"
        );
    assertTemplateOutput(
            "innocuousURLStart2",
            "<a href='{{\" /foo/\"}}{{\" javascript:alert(%22pwned%22)\"}}'>",
            "<a href='/foo/%20javascript:alert%28%22pwned%22%29'>"
        );
    assertTemplateOutput(
            "nonHierURL",
            "<a href={{\"mailto:Muhammed \\\"The Greatest\\\" Ali <m.ali@example.com>\"}}>",
            "<a href=\"mailto:Muhammed%20%22The%20Greatest%22%20Ali%20%3cm.ali@example.com%3e\">"
        );
    assertTemplateOutput(
            "urlPath",
            "<a href='http://{{\"javascript:80\"}}/foo'>",
            "<a href='http://javascript:80/foo'>"
        );
    assertTemplateOutput(
            "urlQuery",
            "<a href='/search?q={{H}}'>",
            "<a href='/search?q=%3cHello%3e'>"
        );
    assertTemplateOutput(
            "urlFragment",
            "<a href='/faq#{{H}}'>",
            "<a href='/faq#%3cHello%3e'>"
        );
    assertTemplateOutput(
            "jsStrValue",
            "<button onclick='alert({{H}})'>",
            "<button onclick='alert(&#39;\\x3cHello\\x3e&#39;)'>"
        );
    assertTemplateOutput(
            "jsNumericValue",
            "<button onclick='alert({{N}})'>",
            "<button onclick='alert( 42 )'>"
        );
    assertTemplateOutput(
            "jsBoolValue",
            "<button onclick='alert({{T}})'>",
            "<button onclick='alert( true )'>"
        );
    assertTemplateOutput(
            "jsNilValue",
            "<button onclick='alert(typeof{{null}})'>",
            "<button onclick='alert(typeof null )'>"
        );
    assertTemplateOutput(
            "jsObjValue",
            "<button onclick='alert({{A}})'>",
            "<button onclick='alert([&#39;\\x3ca\\x3e&#39;,&#39;\\x3cb\\x3e&#39;])'>"
        );
    assertTemplateOutput(
            "jsObjValueScript",
            "<script>alert({{A}})</script>",
            "<script>alert(['\\x3ca\\x3e','\\x3cb\\x3e'])</script>"
        );
    assertTemplateOutput(
            "jsStr",
            "<button onclick='alert(&quot;{{H}}&quot;)'>",
            "<button onclick='alert(\"\\x3cHello\\x3e\")'>"
        );
    assertTemplateOutput(
            "badMarshaller",
            "<button onclick='alert(1/{{B}}in numbers)'>",
            "<button onclick='alert(1/ /* json: foo: &#39;not quite valid JSON&#39; } */ null in numbers)'>"
        );
    assertTemplateOutput(
            "jsMarshaller",
            "<button onclick='alert({{M}})'>",
            "<button onclick='alert({ \"&lt;foo&gt;\": \"O&#39;Reilly\" })'>"
        );
    assertTemplateOutput(
            "jsMarshaller2",
            "<button onclick=\"alert({{M}})\">",
            "<button onclick=\"alert({ &#34;&lt;foo&gt;&#34;: &#34;O'Reilly&#34; })\">"
    );
    assertTemplateOutput(
            "jsStrNotUnderEscaped",
            "<button onclick='alert({{SU}})'>",
            // URL escaped, then quoted for JS.
            "<button onclick='alert(&#39;%3cCincinatti%3e&#39;)'>"
        );
    assertTemplateOutput(
            "jsRe",
            "<button onclick='alert(/{{\"foo+bar\"}}/.test(\"\"))'>",
            "<button onclick='alert(/foo\\x2bbar/.test(\"\"))'>"
        );
    assertTemplateOutput(
            "jsReBlank",
            "<script>alert(/{{\"\"}}/.test(\"\"));</script>",
            "<script>alert(/(?:)/.test(\"\"));</script>"
        );
    assertTemplateOutput(
            "styleBidiKeywordPassed",
            "<p style=\"dir: {{\"ltr\"}}\">",
            "<p style=\"dir: ltr\">"
        );
    assertTemplateOutput(
            "styleBidiPropNamePassed",
            "<p style=\"border-{{\"left\"}}: 0; border-{{\"right\"}}: 1in\">",
            "<p style=\"border-left: 0; border-right: 1in\">"
        );
    assertTemplateOutput(
            "styleExpressionBlocked",
            "<p style=\"width: {{\"expression(alert(1337))\"}}\">",
            "<p style=\"width: ZautoescZ\">"
        );
    assertTemplateOutput(
            "styleTagSelectorPassed",
            "<style>{{\"p\"}} { color: pink }</style>",
            "<style>p { color: pink }</style>"
        );
    assertTemplateOutput(
            "styleIDPassed",
            "<style>p{{\"#my-ID\"}} { font: Arial }</style>",
            "<style>p#my-ID { font: Arial }</style>"
        );
    assertTemplateOutput(
            "styleClassPassed",
            "<style>p{{\".my_class\"}} { font: Arial }</style>",
            "<style>p.my_class { font: Arial }</style>"
        );
    assertTemplateOutput(
            "styleQuantityPassed",
            "<a style=\"left: {{\"2em\"}}; top: {{0}}\">",
            "<a style=\"left: 2em; top: 0\">"
        );
    assertTemplateOutput(
            "stylePctPassed",
            "<table style=width:{{\"100%\"}}>",
            "<table style=\"width:100%\">"
        );
    assertTemplateOutput(
            "styleColorPassed",
            "<p style=\"color: {{\"#8ff\"}}; background: {{\"#000\"}}\">",
            "<p style=\"color: #8ff; background: #000\">"
        );
    assertTemplateOutput(
            "styleObfuscatedExpressionBlocked",
            "<p style=\"width: {{\"  e\\78preS\\0Sio/**/n(alert(1337))\"}}\">",
            "<p style=\"width: ZautoescZ\">"
        );
    assertTemplateOutput(
            "styleMozBindingBlocked",
            "<p style=\"{{\"-moz-binding(alert(1337))\"}}: ...\">",
            "<p style=\"ZautoescZ: ...\">"
        );
    assertTemplateOutput(
            "styleObfuscatedMozBindingBlocked",
            "<p style=\"{{\"  -mo\\7a-B\\0I/**/nding(alert(1337))\"}}: ...\">",
            "<p style=\"ZautoescZ: ...\">"
        );
    assertTemplateOutput(
            "styleFontNameString",
            "<p style='font-family: \"{{\"Times New Roman\"}}\"'>",
            "<p style='font-family: \"Times New Roman\"'>"
        );
    assertTemplateOutput(
            "styleFontNameString",
            "<p style='font-family: \"{{\"Times New Roman\"}}\", \"{{\"sans-serif\"}}\"'>",
            "<p style='font-family: \"Times New Roman\", \"sans-serif\"'>"
        );
    assertTemplateOutput(
            "styleFontNameUnquoted",
            "<p style='font-family: {{\"Times New Roman\"}}'>",
            "<p style='font-family: Times New Roman'>"
        );
    assertTemplateOutput(
            "styleURLQueryEncoded",
            "<p style=\"background: url(/img?name={{\"O'Reilly Animal(1)<2>.png\"}})\">",
            "<p style=\"background: url(/img?name=O%27Reilly%20Animal%281%29%3c2%3e.png)\">"
        );
    assertTemplateOutput(
            "styleQuotedURLQueryEncoded",
            "<p style=\"background: url('/img?name={{\"O'Reilly Animal(1)<2>.png\"}}')\">",
            "<p style=\"background: url('/img?name=O%27Reilly%20Animal%281%29%3c2%3e.png')\">"
        );
    assertTemplateOutput(
            "styleStrQueryEncoded",
            "<p style=\"background: '/img?name={{\"O'Reilly Animal(1)<2>.png\"}}'\">",
            "<p style=\"background: '/img?name=O%27Reilly%20Animal%281%29%3c2%3e.png'\">"
        );
    assertTemplateOutput(
            "styleURLBadProtocolBlocked",
            "<a style=\"background: url('{{\"javascript:alert(1337)\"}}')\">",
            "<a style=\"background: url('#ZautoescZ')\">"
        );
    assertTemplateOutput(
            "styleStrBadProtocolBlocked",
            "<a style=\"background: '{{\"vbscript:alert(1337)\"}}'\">",
            "<a style=\"background: '#ZautoescZ'\">"
        );
    assertTemplateOutput(
            "styleStrEncodedProtocolEncoded",
            "<a style=\"background: '{{\"javascript\\\\3a alert(1337)\"}}'\">",
            // The CSS string 'javascript\\3a alert(1337)' does not contains a colon.
            "<a style=\"background: 'javascript\\\\3a alert\\28 1337\\29 '\">"
        );
    assertTemplateOutput(
            "styleURLGoodProtocolPassed",
            "<a style=\"background: url('{{\"http://oreilly.com/O'Reilly Animals(1)<2>;{}.html\"}}')\">",
            "<a style=\"background: url('http://oreilly.com/O%27Reilly%20Animals%281%29%3c2%3e;%7b%7d.html')\">"
        );
    assertTemplateOutput(
            "styleStrGoodProtocolPassed",
            "<a style=\"background: '{{\"http://oreilly.com/O'Reilly Animals(1)<2>;{}.html\"}}'\">",
            "<a style=\"background: 'http\\3a\\2f\\2foreilly.com\\2fO\\27Reilly Animals\\28 1\\29\\3c 2\\3e\\3b\\7b\\7d.html'\">"
        );
    assertTemplateOutput(
            "styleURLEncodedForHTMLInAttr",
            "<a style=\"background: url('{{\"/search?img=foo&size=icon\"}}')\">",
            "<a style=\"background: url('/search?img=foo&amp;size=icon')\">"
        );
    assertTemplateOutput(
            "styleURLNotEncodedForHTMLInCdata",
            "<style>body { background: url('{{\"/search?img=foo&size=icon\"}}') }</style>",
            "<style>body { background: url('/search?img=foo&size=icon') }</style>"
        );
    assertTemplateOutput(
            "styleURLMixedCase",
            "<p style=\"background: URL(#{{H}})\">",
            "<p style=\"background: URL(#%3cHello%3e)\">"
        );
    assertTemplateOutput(
            "stylePropertyPairPassed",
            "<a style='{{\"color: red\"}}'>",
            "<a style='color: red'>"
        );
    assertTemplateOutput(
            "styleStrSpecialsEncoded",
            "<a style=\"font-family: '{{\"/**/'\\\";:// \\\\\"}}', &quot;{{\"/**/'\\\";:// \\\\\"}}&quot;\">",
            "<a style=\"font-family: '\\2f**\\2f\\27\\22\\3b\\3a\\2f\\2f  \\\\', &#34;\\2f**\\2f\\27\\22\\3b\\3a\\2f\\2f  \\\\&#34;\">"
        );
    assertTemplateOutput(
            "styleURLSpecialsEncoded",
            "<a style=\"border-image: url({{\"/**/'\\\";:// \\\\\"}}), url(&quot;{{\"/**/'\\\";:// \\\\\"}}&quot;), url('{{\"/**/'\\\";:// \\\\\"}}'), 'http://www.example.com/?q={{\"/**/'\\\";:// \\\\\"}}''\">",
            "<a style=\"border-image: url(/**/%27%22;://%20%5c), url(&#34;/**/%27%22;://%20%5c&#34;), url('/**/%27%22;://%20%5c'), 'http://www.example.com/?q=%2f%2a%2a%2f%27%22%3b%3a%2f%2f%20%5c''\">"
        );
    assertTemplateOutput(
            "HTML comment",
            "<b>Hello, <!-- name of world -->{{C}}</b>",
            "<b>Hello, &lt;Cincinatti&gt;</b>"
        );
    assertTemplateOutput(
            "HTML comment not first < in text node.",
            "<<!-- -->!--",
            "&lt;!--"
        );
    assertTemplateOutput(
            "HTML normalization 1",
            "a < b",
            "a &lt; b"
        );
    assertTemplateOutput(
            "HTML normalization 2",
            "a << b",
            "a &lt;&lt; b"
        );
    assertTemplateOutput(
            "HTML normalization 3",
            "a<<!-- --><!-- -->b",
            "a&lt;b"
        );
    assertTemplateOutput(
            "HTML doctype not normalized",
            "<!DOCTYPE html>Hello, World!",
            "<!DOCTYPE html>Hello, World!"
        );
    assertTemplateOutput(
            "No doctype injection",
            "<!{{\"DOCTYPE\"}}",
            "&lt;!DOCTYPE"
        );
    assertTemplateOutput(
            "Split HTML comment",
            "<b>Hello, <!-- name of city {{W}} -->{{C}}</b>",
            "<b>Hello, &lt;Cincinatti&gt;</b>"
        );
    assertTemplateOutput(
            "JS line comment",
            "<script>for (;;) { if (c()) break// foo not a label\n" +
                "foo({{T}});}</script>",
            "<script>for (;;) { if (c()) break\n" +
                "foo( true );}</script>"
        );
    assertTemplateOutput(
            "JS multiline block comment",
            "<script>for (;;) { if (c()) break/* foo not a label\n" +
                " */foo({{T}});}</script>",
            // Newline separates break from call. If newline
            // removed, then break will consume label leaving
            // code invalid.
            "<script>for (;;) { if (c()) break\n" +
                "foo( true );}</script>"
        );
    assertTemplateOutput(
            "JS single-line block comment",
            "<script>for (;;) {\n" +
                "if (c()) break/* foo a label */foo;" +
                "x({{T}});}</script>",
            // Newline separates break from call. If newline
            // removed, then break will consume label leaving
            // code invalid.
            "<script>for (;;) {\n" +
                "if (c()) break foo;" +
                "x( true );}</script>"
        );
    assertTemplateOutput(
            "JS block comment flush with mathematical division",
            "<script>var a/*b*//c\nd</script>",
            "<script>var a /c\nd</script>"
        );
    assertTemplateOutput(
            "JS mixed comments",
            "<script>var a/*b*///c\nd</script>",
            "<script>var a \nd</script>"
        );
    assertTemplateOutput(
            "CSS comments",
            "<style>p// paragraph\n" +
                "{border: 1px/* color */{{\"#00f\"}}}</style>",
            "<style>p\n" +
                "{border: 1px #00f}</style>"
        );
    assertTemplateOutput(
            "JS attr block comment",
            "<a onclick=\"f(&quot;&quot;); /* alert({{H}}) */\">",
            // Attribute comment tests should pass if the comments
            // are successfully elided.
            "<a onclick=\"f(&#34;&#34;);   \">"
        );
    assertTemplateOutput(
            "JS attr line comment",
            "<a onclick=\"// alert({{G}})\">",
            "<a onclick=\"\">"
        );
    assertTemplateOutput(
            "CSS attr block comment",
            "<a style=\"/* color: {{H}} */\">",
            "<a style=\"  \">"
        );
    assertTemplateOutput(
            "CSS attr line comment",
            "<a style=\"// color: {{G}}\">",
            "<a style=\"\">"
        );
    assertTemplateOutput(
            "HTML substitution commented out",
            "<p><!-- {{H}} --></p>",
            "<p></p>"
        );
    assertTemplateOutput(
            "Comment ends flush with start",
            "<!--{{H}}--><script>/*{{H}}*///{{H}}\n</script><style>/*{{H}}*///{{H}}\n</style><a onclick='/*{{H}}*///{{H}}' style='/*{{H}}*///{{H}}'>",
            "<script> \n</script><style> \n</style><a onclick=' ' style=' '>"
        );
    assertTemplateOutput(
            "typed HTML in text",
            "{{W}}",
            "&iexcl;<b class=\"foo\">Hello</b>, <textarea>O'World</textarea>!"
        );
    assertTemplateOutput(
            "typed HTML in attribute",
            "<div title=\"{{W}}\">",
            "<div title=\"&iexcl;Hello, O'World!\">"
        );
    assertTemplateOutput(
            "typed HTML in attribute 2",
            "<div title='{{W}}'>",
            "<div title='&iexcl;Hello, O&#39;World!'>"
    );
    assertTemplateOutput(
            "typed HTML in script",
            "<button onclick=\"alert({{W}})\">",
            "<button onclick=\"alert('\\x26iexcl;\\x3cb class=\\x22foo\\x22\\x3eHello\\x3c\\/b\\x3e, \\x3ctextarea\\x3eO\\x27World\\x3c\\/textarea\\x3e!')\">"
        );
    assertTemplateOutput(
            "typed HTML in RCDATA",
            "<textarea>{{W}}</textarea>",
            "<textarea>&iexcl;&lt;b class=&#34;foo&#34;&gt;Hello&lt;/b&gt;, &lt;textarea&gt;O&#39;World&lt;/textarea&gt;!</textarea>"
        );
    assertTemplateOutput(
            "No tag injection",
            "{{\"10$\"}}<{{\"script src,evil.org/pwnd.js\"}}...",
            "10$&lt;script src,evil.org/pwnd.js..."
        );
    assertTemplateOutput(
            "No comment injection",
            "<{{\"!--\"}}",
            "&lt;!--"
        );
    assertTemplateOutput(
            "No RCDATA end tag injection",
            "<textarea><{{\"/textarea \"}}...</textarea>",
            "<textarea>&lt;/textarea ...</textarea>"
        );
    assertTemplateOutput(
            "split attrs",
            "<img class=\"{{\"iconClass\"}}\"" +
                "{{}} id=\"{{\"<iconId>\"}}\"{{}}" +
                // Double quotes inside if/else.
                " src=" +
                "{{}}\"?{{\"<iconPath>\"}}\"{{}}" +
                // Missing space before title, but it is not a
                // part of the src attribute.
                "{{}}title=\"{{\"<title>\"}}\"{{}}" +
                // Quotes outside if/else.
                " alt=\"" +
                "{{}}{{\"<alt>\"}}{{}}" +
                "\"" +
                ">",
            "<img class=\"iconClass\" id=\"&lt;iconId&gt;\" src=\"?%3ciconPath%3e\"title=\"&lt;title&gt;\" alt=\"&lt;alt&gt;\">"
        );
    assertTemplateOutput(
            "conditional valueless attr name",
            "<input{{}} checked{{}} name=n>",
            "<input checked name=\"n\">"
        );
    assertTemplateOutput(
            "conditional dynamic valueless attr name 1",
            "<input{{}} {{\"checked\"}}{{}} name=n>",
            "<input checked name=\"n\">"
        );
    assertTemplateOutput(
            "conditional dynamic valueless attr name 2",
            "<input {{}}{{\"checked\"}} {{}}name=n>",
            "<input checked name=\"n\">"
        );
    assertTemplateOutput(
            "dynamic attribute name",
            "<img on{{\"load\"}}=\"alert({{\"loaded\"}})\">",
            // Treated as JS since quotes are inserted.
            "<img onload=\"alert('loaded')\">"
        );
    assertTemplateOutput(
            "dynamic attribute name2",
            "<img on{{\"load\"}}='alert({{\"loaded\"}})'>",
            // Treated as JS since quotes are inserted.
            "<img onload='alert(&#39;loaded&#39;)'>"
        );
    assertTemplateOutput(
            "bad dynamic attribute name 1",
            // The value is interpreted consistent with the attribute name.
            "<input {{\"onchange\"}}=\"{{\"doEvil()\"}}\">",
            "<input onchange=\"'doEvil\\(\\)'\">"
        );
    assertTemplateOutput(
            "bad dynamic attribute name 2",
            // Structure preservation requires values to associate
            // with a consistent attribute.
            "<input checked {{\"\"}}=\"Whose value am I?\">",
            "<input checked ZautoescZ=\"Whose value am I?\">"
        );
    assertTemplateOutput(
            "bad dynamic attribute name 3",
            // Structure preservation requires values to associate
            // with a consistent attribute.
            "<input checked {{\" \"}}=\"Whose value am I?\">",
            "<input checked ZautoescZ=\"Whose value am I?\">"
        );
    assertTemplateOutput(
            "bad dynamic attribute name / value pair 1",
            "<div {{\"sTyle\"}}=\"{{\"color: expression(alert(1337))\"}}\">",
            "<div sTyle=\"ZautoescZ\">"
        );
    assertTemplateOutput(
            "bad dynamic attribute name / value pair 2",
            // Allow title or alt, but not a URL.
            "<img {{\"src\"}}=\"{{\"javascript:doEvil()\"}}\">",
            "<img src=\"#ZautoescZ\">"
        );
    assertTemplateOutput(
            "unrecognized dynamic attribute name",
            // Allow checked, selected, disabled, but not JS or
            // CSS attributes.
            "<div {{\"bogus\"}}=\"{{\"doEvil()\"}}\">",
            "<div ZautoescZ=\"doEvil()\">"
        );
    assertTemplateOutput(
            "dynamic element name",
            "<h{{3}}><table><t{{\"head\"}}>...</h{{3}}>",
            "<h3><table><thead>...</h3>"
        );
    assertTemplateOutput(
            "bad dynamic element name",
            // Dynamic element names are typically used to switch
            // between (thead, tfoot, tbody), (ul, ol), (th, td),
            // and other replaceable sets.
            // We do not currently easily support (ul, ol).
            // If we do change to support that, this test should
            // catch failures to filter out special tag names which
            // would violate the structure preservation property --
            // if any special tag name could be substituted, then
            // the content could be raw text/RCDATA for some inputs
            // and regular HTML content for others.
            "<{{\"script\"}}>{{\"doEvil()\"}}</{{\"script\"}}>",
            "&lt;script>doEvil()&lt;/script>"
        );
  }

  private void assertErrorMsg(String html, String error) throws Exception {
    try {
      HTMLEscapingWriter w = new HTMLEscapingWriter(new StringWriter());
      w.writeSafe(html);
      w.close();
      fail("Expected error " + error);
    } catch (TemplateException ex) {
      assertEquals(html, error, ex.getMessage());
    }
    // None of these error cases trigger exceptions when stripping tags.
    {
      HTMLEscapingWriter w = new HTMLEscapingWriter(new StringWriter());
      w.stripTags(html, Context.Delim.DoubleQuote);
    }
    // Errors should be triggered regardless of whether the input is passed
    // as a String or a char[]
    try {
      HTMLEscapingWriter w = new HTMLEscapingWriter(new StringWriter());
      char[] chars = ("'" + html + "'").toCharArray();
      w.writeSafe(chars, 1, 1 + html.length());
      w.close();
      fail("Expected error " + error);
    } catch (TemplateException ex) {
      assertEquals(html, error, ex.getMessage());
    }
  }

  public final void testErrors() throws Exception {
    assertErrorMsg(
        // Missing quote.
        "<a href=\"bar>",
        "Incomplete document fragment ended in URL DoubleQuote PreQuery");
    assertErrorMsg(
        "<a<a",
        "< in attribute name: ^<a");
    assertErrorMsg(
        "<a <a",
        "< in attribute name: ^<a");
    assertErrorMsg(
        "<a b=1 c=",
        "Incomplete document fragment ended in BeforeValue");
    assertErrorMsg(
        "<script>foo();",
        "Incomplete document fragment ended in JS Script");
    assertErrorMsg(
        "<a onclick=\"alert('Hello \\",
        "unfinished escape sequence in JS string: Hello ^\\");
    assertErrorMsg(
        "<a onclick='alert(\"Hello\\, World\\",
        "unfinished escape sequence in JS string:  World^\\");
    assertErrorMsg(
        "<a onclick='alert(/x+\\",
        "unfinished escape sequence in JS string: x+^\\");
    assertErrorMsg(
        "<a onclick=\"/foo[\\]/",
        "unfinished JS regexp charset: ^");
    assertErrorMsg(
        "<input type=button value=onclick=>",
        "= in unquoted attr: onclick^=");
    assertErrorMsg(
        "<input type=button value= onclick=>",
        "= in unquoted attr: onclick^=");
    assertErrorMsg(
        "<input type=button value= 1+1=2>",
        "= in unquoted attr: 1+1^=2");
    assertErrorMsg(
        "<a class=`foo>",
        "` in unquoted attr: ^`foo");
    assertErrorMsg(
        "<a style=font:'Arial'>",
        "' in unquoted attr: font:^'Arial'");
    assertErrorMsg(
        "<a=foo>",
        "expected space, attr name, or end of tag, but got ^=foo>");
  }

  private static boolean isNumberLit(String s) {
    try {
      Float.parseFloat(s);
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static Object parseAsNumber(String s) {
    try {
      return Integer.valueOf(s);
    } catch (NumberFormatException ex) {
      return Float.valueOf(s);
    }
  }

  static class BadMarshaler implements JSONMarshaler {
    public String toJSON() {
      return "{ foo: 'not quite valid JSON' }";
    }
  }
  static class GoodMarshaler implements JSONMarshaler {
    public String toJSON() {
      return "{ \"<foo>\": \"O'Reilly\" }";
    }
  }
}
