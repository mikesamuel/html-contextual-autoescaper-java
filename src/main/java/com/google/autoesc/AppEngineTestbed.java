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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that provides a testbed that security auditors can use to try
 * out attacks.
 */
public class AppEngineTestbed extends HttpServlet {

  static final long serialVersionUID = 550932736980754956L;

  private static final String SAMPLE_SRC = (
      "<div style=\"color: {{.Color}}\">\n" +
      "  <a href=\"/{{.Color}}?q={{.World}}\"\n" +
      "   onclick=\"alert('Hello, {{.World}}!');return false\">\n" +
      "    Hello, {{.World}}!\n" +
      "  </a>\n" +
      "  <script>(function () {  // Sleepy devs put secrets in comments.\n" +
      "    var o = {{.}},\n" +
      "        w = \"{{.World | html}}\";\n" +
      "  })();</script>\n" +
      "</div>");
  private static final String SAMPLE_INPUT = (
      "{\n    \"World\": \"<Cincinatti>\",\n    \"Color\": \"blue\"\n}");

  private static final Pattern HOLE = Pattern.compile(
      "\\{\\{\\s*((?:\\.\\s*\\w+\\s*)+|\\.\\s*)\\}\\}");

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String src = req.getParameter("src");
    String inp = req.getParameter("inp");

    resp.setContentType("text/html; charset=UTF-8");
    handle(src, inp, resp.getWriter());
  }

  static void handle(@Nullable String src, @Nullable String inp, Writer resp)
      throws IOException {
    String error = null, result = null;
    if (src != null && inp != null) {
      try {
        Object json = new JSONParser(inp).parse();
        StringWriter buf = new StringWriter();
        try (MemoizingHTMLEscapingWriter w =
                 new MemoizingHTMLEscapingWriter(buf)) {
          Matcher m = HOLE.matcher(src);
          int pos = 0;
          while (m.find()) {
            w.writeSafe(src.substring(pos, m.start()));
            pos = m.end();
            String key = m.group(1).trim();
            Object value = json;
            int keylen = key.length();
            for (int dot = 0, nextdot = 1; nextdot != keylen; dot = nextdot) {
              nextdot = key.indexOf(".", dot+1);
              if (nextdot == -1) { nextdot = keylen; }
              String prop = key.substring(dot+1, nextdot).trim();
              if (value instanceof Map<?, ?>) {
                value = ((Map<?, ?>) value).get(prop);
              } else if (value instanceof List<?>) {
                try {
                  int i = Integer.parseInt(prop);
                  value = ((List<?>) value).get(i);
                } catch (@SuppressWarnings("unused") NumberFormatException ex) {
                  value = null;
                } catch (@SuppressWarnings("unused")
                IndexOutOfBoundsException ex) {
                  value = null;
                }
              } else {
                value = null;
              }
            }
            w.write(value);
          }
          w.writeSafe(src.substring(pos));
        }
        result = buf.toString();
      } catch (Exception ex) {
        error = ex.toString();
        ex.printStackTrace();
      }
    }

    String finalSrc = src != null ? src : SAMPLE_SRC;
    String finalInp = inp != null ? inp : SAMPLE_INPUT;

    @SuppressWarnings("resource")  // resp closed by caller
    HTMLEscapingWriter out = new MemoizingHTMLEscapingWriter(resp);
    try {
      renderForm(out, error, finalSrc, finalInp, result);
    } catch (TemplateException ex) {
      resp.write("<!--></script></style><plaintext>");
      @SuppressWarnings("resource")  // resp closed by caller
      PrintWriter pw = new PrintWriter(resp);
      ex.printStackTrace(pw);
      pw.flush();
    }
  }

  private static void renderForm(
      HTMLEscapingWriter out, @Nullable String error, String src, String inp,
      @Nullable String result)
      throws IOException, TemplateException {
    out.writeSafe(
        "<!DOCTYPE html>" +
        "<title>HTML Contextual Autoescaping Testbench</title>" +
        // Enable IE8 compatibility mode.
        "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=EmulateIE8\" />" +
        "<style>" +
        "  tr { vertical-align: top }" +
        "  th { text-align: left }" +
        "  td, th { padding-left: .5em }" +
        "  table { width: 100%; margin: 1em 0 }" +
        "  textarea { width: 100%; height: 20em; font-family: monospace }" +
        "  code { white-space: pre-wrap }" +
        "  .err { color: red; font-weight: bold }" +
        "  .err:before { content: \"Error - \" }" +
        "  button { font-weight: bold }" +
        "</style>" +
        "<h1>HTML Contextual Autoescaping Testbench</h1>" +
        "<p>This is a testbed for a Java HTML autoescaper" +
        " which aims to protect template languages from XSS." +
        "<p>You can enter a template in the box labeled \"Template\" " +
        " and <code>{{.X.Y}}</code> will cause interpolation of the property " +
        " Y of the property X of the JSON data value from the second input." +
        " The template is assumed to be trusted, and the" +
        " JSON data is assumed to be malicious.  An exploit occurs whenever" +
        " a template that a naive but trusted author is likely to write" +
        " suffers an XSS when rendered with any data value." +
        "<p>You can browse the <a href=\"" +
        "https://github.com/mikesamuel/html-contextual-autoescaper-java" +
        "\">source code</a> online.");
    if (error != null) {
      out.writeSafe("<p class=\"err\">");
      out.write(error);
      out.writeSafe("</p>");
    }
    out.writeSafe(
        "<p>Please report issues to " +
        " <a href=\"mailto:mikesamuel@gmail.com\">mikesamuel@gmail.com</a> or" +
        " <a href=\"https://github.com/mikesamuel/" +
        "html-contextual-autoescaper-java/issues\">issue tracker</a>." +
        "</p>" +
        "<form method=\"GET\" action=\"/\">" +
        "<button type=\"submit\">Evaluate Template</button>" +
        "<table>" +
        "  <tr><th width=50%>Template");
    if (result != null) {
      out.writeSafe("<th width=50%>Output Src");
    }
    out.writeSafe("  <tr><td><textarea name=\"src\">");
    out.write(src);
    out.writeSafe("</textarea>");
    if (result != null) {
      out.writeSafe("<td><code>");
      out.write(result);
      out.writeSafe("</code>");
    }
    out.writeSafe("  <tr><th>Input JSON");
    if (result != null) {
      out.writeSafe("<th>Output HTML");
    }
    out.writeSafe("  <tr><td><textarea name=\"inp\">");
    out.write(inp);
    out.writeSafe("</textarea>");
    if (result != null) {
      out.writeSafe("<td>");
      out.write(new SafeContentString(result, ContentType.Markup));
    }
    out.writeSafe(
        "</table>" +
        "<button type=\"submit\">Evaluate Template</button>" +
        "</form>");
    out.close();
  }
}

final class JSONParser {
  private final String s;
  private int off;
  private final int end;

  JSONParser(String s) {
    this.s = s;
    this.off = 0;
    this.end = s.length();
  }

  final Object parse() {
    eatSpace();
    if (off == end) { return null; }
    char ch = s.charAt(off);
    switch (ch) {
      case '"': {
        StringBuilder sb = new StringBuilder();
        int pos = off + 1;
        for (int e = pos; e < end;) {
          switch (s.charAt(e)) {
            case '\\':
              sb.append(s, pos, e);
              ++e;
              if (e == end) { throw error("unfinished escape sequence"); }
              if (s.charAt(e) == 'u') {
                ++e;
                if (e+4 == end) { throw error("unfinished escape sequence"); }
                sb.append((char) Integer.parseInt(s.substring(e, e+4), 16));
                e += 4;
              }
              pos = e;
              break;
            case '"':
              sb.append(s, pos, e);
              off = e + 1;
              return sb.toString();
            case '\r': case '\n':
              throw error("newline in string");
            default:
              ++e;
              break;
          }
        }
        throw error("unclosed string literal");
      }
      case '[': {
        List<Object> list = new ArrayList<>();
        ++off;
        while (off < end) {
          eatSpace();
          if (off == end) { break; }
          if (s.charAt(off) == ']') {
            ++off;
            return list;
          }
          list.add(parse());
          eatSpace();
          if (off == end) { break; }
          switch (s.charAt(off)) {
            case ',': ++off; break;
            case ']': break;
            default:
              throw error("expected comma or array end");
          }
        }
        throw error("unclosed array");
      }
      case '{': {
        Map<String, Object> map = new LinkedHashMap<>();
        ++off;
        while (off < end) {
          eatSpace();
          if (off == end) { break; }
          if (s.charAt(off) == '}') {
            ++off;
            return map;
          }
          String key = (String) parse();
          eatSpace();
          if (off == end) { break; }
          if (s.charAt(off) != ':') {
            throw error("expected colon");
          }
          ++off;
          map.put(key, parse());
          eatSpace();
          if (off == end) { break; }
          switch (s.charAt(off)) {
            case ',': ++off; break;
            case '}': break;
            default:
              throw error("expected comma or array end");
          }
        }
        throw error("unclosed object");
      }
      case 'f':
        requireWordMatches("false");
        return false;
      case 't':
        requireWordMatches("true");
        return true;
      case 'n':
        requireWordMatches("null");
        return null;
      case '+': case '-': case '.': case '0': case '1': case '2': case '3':
      case '4': case '5': case '6': case '7': case '8': case '9':
        int e = off + 1;
        while (e < end && isNumChar(s.charAt(e))) { ++e; }
        String num = s.substring(off, e);
        try {
          Number n = (num.indexOf('x') & num.indexOf('X')) >= 0
              ? Long.decode(num)  // Handle 0x...
              : Double.valueOf(num);
          off = e;
          return n;
        } catch (NumberFormatException ex) {
          throw error("Bad number " + num, ex);
        }

      default:
        throw error(
            "char '" + ch + "':" + Integer.toString(ch, 16));
    }
  }

  void eatSpace() {
    while (off < end && Character.isWhitespace(s.charAt(off))) { ++off; }
  }

  void requireWordMatches(String word) {
    int e = off;
    while (e < end && isIdentChar(s.charAt(e))) { ++e; }
    if (!word.equals(s.substring(off, e))) {
      throw error("expected " + word);
    }
    off = e;
  }

  static boolean isIdentChar(char ch) {
    return ('0' <= ch && ch <= '9') || ('A' <= ch && ch <= 'Z')
        || ('a' <= ch && ch <= 'z') || ch == '_' || ch == '$';
  }

  static boolean isNumChar(char ch) {
    // Number tokens cannot be adjacent to word tokens, and can include x and e
    // so interpret liberally.  Signs can appear leading or in exponent part.
    return isIdentChar(ch) || ch == '.' || ch == '+' || ch == '-';
  }

  IllegalArgumentException error(String msg) {
    return error(msg, null);
  }

  IllegalArgumentException error(String msg, Throwable th) {
    return new IllegalArgumentException(
        "JSON: " + msg + " at " + off + " in " + s.substring(0, off) + "><"
        + s.substring(off), th);
  }
}
