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
import java.util.Collections;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

public final class JSTest extends TestCase {
  private void assertNextJSCtx(Context.JSCtx want, String tokens) {
    assertEquals(
        want, JS.nextJSCtx(tokens, 0, tokens.length(), Context.JSCtx.Regexp));
    assertEquals(
        want, JS.nextJSCtx(tokens, 0, tokens.length(), Context.JSCtx.DivOp));
    assertEquals(
        want,
        JS.nextJSCtx("." + tokens + "*", 1, 1 + tokens.length(),
                     Context.JSCtx.Regexp));
    assertEquals(
        want,
        JS.nextJSCtx("." + tokens + ";", 1, 1 + tokens.length(),
                     Context.JSCtx.DivOp));
    assertEquals(
        want,
        JS.nextJSCtx(tokens + " ", 0, tokens.length() + 1,
                     Context.JSCtx.Regexp));
    assertEquals(
        want,
        JS.nextJSCtx(tokens + " ", 0, tokens.length() + 1,
                     Context.JSCtx.DivOp));
  }

  public final void testNextJSCtx() throws Exception {
    // Statement terminators precede regexps.
    assertNextJSCtx(Context.JSCtx.Regexp, ";");
    // This is not airtight.
    //     ({ valueOf: function () { return 1 } } / 2)
    // is valid JavaScript but in practice, devs do not do this.
    // A block followed by a statement starting with a RegExp is
    // much more common:
    //     while (x) {...} /foo/.test(x) || panic()
    assertNextJSCtx(Context.JSCtx.Regexp, "}");
    // But member, call, grouping, and array expression terminators
    // precede div ops.
    assertNextJSCtx(Context.JSCtx.DivOp, ")");
    assertNextJSCtx(Context.JSCtx.DivOp, "]");
    // At the start of a primary expression, array, or expression
    // statement, expect a regexp.
    assertNextJSCtx(Context.JSCtx.Regexp, "(");
    assertNextJSCtx(Context.JSCtx.Regexp, "[");
    assertNextJSCtx(Context.JSCtx.Regexp, "{");
    // Assignment operators precede regexps as do all exclusively
    // prefix and binary operators.
    assertNextJSCtx(Context.JSCtx.Regexp, "=");
    assertNextJSCtx(Context.JSCtx.Regexp, "+=");
    assertNextJSCtx(Context.JSCtx.Regexp, "*=");
    assertNextJSCtx(Context.JSCtx.Regexp, "*");
    assertNextJSCtx(Context.JSCtx.Regexp, "!");
    // Whether the + or - is infix or prefix, it cannot precede a
    // div op.
    assertNextJSCtx(Context.JSCtx.Regexp, "+");
    assertNextJSCtx(Context.JSCtx.Regexp, "-");
    // An incr/decr op precedes a div operator.
    // This is not airtight. In (g = ++/h/i) a regexp follows a
    // pre-increment operator, but in practice devs do not try to
    // increment or decrement regular expressions.
    // (g++/h/i) where ++ is a postfix operator on g is much more
    // common.
    assertNextJSCtx(Context.JSCtx.DivOp, "--");
    assertNextJSCtx(Context.JSCtx.DivOp, "++");
    assertNextJSCtx(Context.JSCtx.DivOp, "x--");
    // When we have many dashes or pluses, then they are grouped
    // left to right.
    assertNextJSCtx(Context.JSCtx.Regexp, "x---"); // A postfix -- then a -.
    // return followed by a slash returns the regexp literal or the
    // slash starts a regexp literal in an expression statement that
    // is dead code.
    assertNextJSCtx(Context.JSCtx.Regexp, "return");
    assertNextJSCtx(Context.JSCtx.Regexp, "return ");
    assertNextJSCtx(Context.JSCtx.Regexp, "return\t");
    assertNextJSCtx(Context.JSCtx.Regexp, "return\n");
    assertNextJSCtx(Context.JSCtx.Regexp, "return\u2028");
    // Identifiers can be divided and cannot validly be preceded by
    // a regular expressions. Semicolon insertion cannot happen
    // between an identifier and a regular expression on a new line
    // because the one token lookahead for semicolon insertion has
    // to conclude that it could be a div binary op and treat it as
    // such.
    assertNextJSCtx(Context.JSCtx.DivOp, "x");
    assertNextJSCtx(Context.JSCtx.DivOp, "x ");
    assertNextJSCtx(Context.JSCtx.DivOp, "x\t");
    assertNextJSCtx(Context.JSCtx.DivOp, "x\n");
    assertNextJSCtx(Context.JSCtx.DivOp, "x\u2028");
    assertNextJSCtx(Context.JSCtx.DivOp, "preturn");
    // Numbers precede div ops.
    assertNextJSCtx(Context.JSCtx.DivOp, "0");
    // Dots that are part of a number are div preceders.
    assertNextJSCtx(Context.JSCtx.DivOp, "0.");

    assertEquals(Context.JSCtx.Regexp,
                 JS.nextJSCtx("   ", 0, 3, Context.JSCtx.Regexp));

    assertEquals(Context.JSCtx.DivOp,
                 JS.nextJSCtx("   ", 0, 3, Context.JSCtx.DivOp));
  }

  private void assertEscapedValue(Object val, String want) throws Exception {
    {
      StringWriter buf = new StringWriter();
      JS.escapeValueOnto(val, buf);
      assertEquals(want, buf.toString());
    }
    // Check that JSON handling does not introduce problems.
    {
      StringWriter buf = new StringWriter();
      JS.escapeValueOnto(Collections.singleton(val), buf);
      assertEquals("[" + want.trim() + "]", buf.toString());
    }
  }

  public final void testEscapeValueOnto() throws Exception {
    assertEscapedValue(42, " 42 ");
    assertEscapedValue((short) 42, " 42 ");
    assertEscapedValue(42L, " 42 ");
    assertEscapedValue(-42, " -42 ");
    assertEscapedValue((short) -42, " -42 ");
    assertEscapedValue(-42L, " -42 ");
    // ulp(1 << 53) > 1 so this loses precision in JS
    // but it is still a representable integer literal.
    assertEscapedValue(1L << 53, " 9007199254740992 ");
    assertEscapedValue((float) 1.0, " 1.0 ");
    assertEscapedValue((float) -1.0, " -1.0 ");
    assertEscapedValue((float) 0.5, " 0.5 ");
    assertEscapedValue((float) -0.5, " -0.5 ");
    assertEscapedValue((float) 1.0 / 256, " 0.00390625 ");
    assertEscapedValue((float) 0, " 0.0 ");
    assertEscapedValue(1 / Double.NEGATIVE_INFINITY, " -0.0 ");
    assertEscapedValue("", "''");
    assertEscapedValue(1.0, " 1.0 ");
    assertEscapedValue(-1.0, " -1.0 ");
    assertEscapedValue(0.5, " 0.5 ");
    assertEscapedValue(-0.5, " -0.5 ");
    assertEscapedValue(1.0 / 256, " 0.00390625 ");
    assertEscapedValue(0d, " 0.0 ");
    assertEscapedValue("foo", "'foo'");
    // Newlines.
    assertEscapedValue("\r\n\u2028\u2029", "'\\r\\n\\u2028\\u2029'");
    // "\v" == "v" on IE 6 so use "\x0b" instead.
    assertEscapedValue("\t\u000b", "'\\t\\x0b'");
    assertEscapedValue(ImmutableMap.of("X", 1, "Y", 2), "{'X':1,'Y':2}");
    assertEscapedValue(ImmutableList.of(), "[]");
    assertEscapedValue(Collections.emptySet(), "[]");
    assertEscapedValue(Lists.<Object>newArrayList(42, "foo", null),
                       "[42,'foo',null]");
    assertEscapedValue(ImmutableList.of("<!--", "</script>", "-->"),
                       "['\\x3c!--','\\x3c\\/script\\x3e','--\\x3e']");
    assertEscapedValue("<!--", "'\\x3c!--'");
    assertEscapedValue("-->", "'--\\x3e'");
    assertEscapedValue("<![CDATA[", "'\\x3c![CDATA['");
    assertEscapedValue("]]>", "']]\\x3e'");
    assertEscapedValue("</script", "'\\x3c\\/script'");
    // or "'\\ud834\\udd1E'"
    assertEscapedValue("\ud834\udd1e", "'\ud834\udd1e'");
  }

  private String jsStr(String s) throws Exception {
    StringWriter buf = new StringWriter();
    JS.escapeStrOnto(s, buf);
    return buf.toString();
  }

  private void assertEscapedStrChars(String plainText, String jsStrChars)
      throws Exception {
    assertEquals(jsStrChars, jsStr(plainText));
  }

  public final void testJSStrEscaper() throws Exception {
    assertEscapedStrChars("", "");
    assertEscapedStrChars("foo", "foo");
    assertEscapedStrChars("\u0000", "\\0");
    assertEscapedStrChars("\t", "\\t");
    assertEscapedStrChars("\n", "\\n");
    assertEscapedStrChars("\r", "\\r");
    assertEscapedStrChars("\u2028", "\\u2028");
    assertEscapedStrChars("\u2029", "\\u2029");
    assertEscapedStrChars("\\", "\\\\");
    assertEscapedStrChars("\\n", "\\\\n");
    assertEscapedStrChars("foo\r\nbar", "foo\\r\\nbar");
    // Preserve attribute boundaries.
    assertEscapedStrChars("\"", "\\x22");
    assertEscapedStrChars("'", "\\x27");
    // Allow embedding in HTML without further escaping.
    assertEscapedStrChars("&amp;", "\\x26amp;");
    // Prevent breaking out of text node and element boundaries.
    assertEscapedStrChars("</script>", "\\x3c\\/script\\x3e");
    assertEscapedStrChars("<![CDATA[", "\\x3c![CDATA[");
    assertEscapedStrChars("]]>", "]]\\x3e");
    // http://dev.w3.org/html5/markup/aria/syntax.html#escaping-text-span
    //   "The text in style, script, title, and textarea elements
    //   must not have an escaping text span start that is not
    //   followed by an escaping text span end."
    // Furthermore, spoofing an escaping text span end could lead
    // to different interpretation of a </script> sequence otherwise
    // masked by the escaping text span, and spoofing a start could
    // allow regular text content to be interpreted as script
    // allowing script execution via a combination of a JS string
    // injection followed by an HTML text injection.
    assertEscapedStrChars("<!--", "\\x3c!--");
    assertEscapedStrChars("-->", "--\\x3e");
    // From http://code.google.com/p/doctype/wiki/ArticleUtf7
    assertEscapedStrChars(
        "+ADw-script+AD4-alert(1)+ADw-/script+AD4-",
        "\\x2bADw-script\\x2bAD4-alert(1)\\x2bADw-\\/script\\x2bAD4-");
    // Invalid UTF-8 sequence
    assertEscapedStrChars("foo\u00A0bar", "foo\u00A0bar");
  }

  private String jsRegexp(String s) throws Exception {
    StringWriter buf = new StringWriter();
    JS.escapeRegexpOnto(s, buf);
    return buf.toString();
  }

  private void assertEscapedRegexpChars(String plainText, String jsChars)
      throws Exception {
    assertEquals(jsChars, jsRegexp(plainText));
  }

  public final void testJSRegexpEscaper() throws Exception {
    assertEscapedRegexpChars("", "(?:)");
    assertEscapedRegexpChars("foo", "foo");
    assertEscapedRegexpChars("\u0000", "\\0");
    assertEscapedRegexpChars("\t", "\\t");
    assertEscapedRegexpChars("\n", "\\n");
    assertEscapedRegexpChars("\r", "\\r");
    assertEscapedRegexpChars("\u2028", "\\u2028");
    assertEscapedRegexpChars("\u2029", "\\u2029");
    assertEscapedRegexpChars("\\", "\\\\");
    assertEscapedRegexpChars("\\n", "\\\\n");
    assertEscapedRegexpChars("foo\r\nbar", "foo\\r\\nbar");
    // Preserve attribute boundaries.
    assertEscapedRegexpChars("\"", "\\x22");
    assertEscapedRegexpChars("'", "\\x27");
    // Allow embedding in HTML without further escaping.
    assertEscapedRegexpChars("&amp;", "\\x26amp;");
    // Prevent breaking out of text node and element boundaries.
    assertEscapedRegexpChars("</script>", "\\x3c\\/script\\x3e");
    assertEscapedRegexpChars("<![CDATA[", "\\x3c!\\[CDATA\\[");
    assertEscapedRegexpChars("]]>", "\\]\\]\\x3e");
    // Escaping text spans.
    assertEscapedRegexpChars("<!--", "\\x3c!\\-\\-");
    assertEscapedRegexpChars("-->", "\\-\\-\\x3e");
    assertEscapedRegexpChars("*", "\\*");
    assertEscapedRegexpChars("+", "\\x2b");
    assertEscapedRegexpChars("?", "\\?");
    assertEscapedRegexpChars("[](){}", "\\[\\]\\(\\)\\{\\}");
    assertEscapedRegexpChars("$foo|x.y", "\\$foo\\|x\\.y");
    assertEscapedRegexpChars("x^y", "x\\^y");
  }

  public final void testEscapersOnLower7AndSelectHighCodepoints()
      throws Exception {
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

    assertEquals(
        "jsStrEscaper",
        "\\0\1\2\3\4\5\6\7\10\\t\\n\\x0b\\f\\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\\x22#$%\\x26\\x27()*\\x2b,-.\\/" +
        "0123456789:;\\x3c=\\x3e?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ[\\\\]^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz{|}~\u007f" +
        "\u00A0\u0100\\u2028\\u2029\ufeff\ufdec\ud834\udd13",
        jsStr(input));
    assertEquals(
        "jsRegexpEscaper",
        "\\0\1\2\3\4\5\6\7\10\\t\\n\\x0b\\f\\r\16\17" +
        "\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37" +
        " !\\x22#\\$%\\x26\\x27\\(\\)\\*\\x2b,\\-\\.\\/" +
        "0123456789:;\\x3c=\\x3e\\?" +
        "@ABCDEFGHIJKLMNO" +
        "PQRSTUVWXYZ\\[\\\\\\]\\^_" +
        "`abcdefghijklmno" +
        "pqrstuvwxyz\\{\\|\\}~\u007f" +
        "\u00A0\u0100\\u2028\\u2029\ufeff\ufdec\ud834\udd13",
        jsRegexp(input));
  }
}
