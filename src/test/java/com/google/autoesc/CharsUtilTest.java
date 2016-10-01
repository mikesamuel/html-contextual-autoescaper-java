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

import junit.framework.TestCase;

public class CharsUtilTest extends TestCase {
  public final void testCharAt() {
    char[] chars = "Hello\ud800\udc00".toCharArray();
    assertEquals('H', CharsUtil.charAt(chars, 0));
    assertEquals('e', CharsUtil.charAt(chars, 1));
    assertEquals('l', CharsUtil.charAt(chars, 2));
    assertEquals('l', CharsUtil.charAt(chars, 3));
    assertEquals('o', CharsUtil.charAt(chars, 4));
    assertEquals((char) 0xd800, CharsUtil.charAt(chars, 5));
  }

  public final void testCodePointAt() {
    char[] chars = "Hello\ud800\udc00".toCharArray();
    assertEquals('H', CharsUtil.codePointAt(chars, 0));
    assertEquals('e', CharsUtil.codePointAt(chars, 1));
    assertEquals('l', CharsUtil.codePointAt(chars, 2));
    assertEquals('l', CharsUtil.codePointAt(chars, 3));
    assertEquals('o', CharsUtil.codePointAt(chars, 4));
    assertEquals(0x10000, CharsUtil.codePointAt(chars, 5));
  }

  public final void testFindHtmlCommentEnd() {
    String s1 = "foo-->bar--> baz -->";
    char[] s2 = s1.toCharArray();
    int n = s1.length();
    assertEquals(3, CharsUtil.findHtmlCommentEnd(s1, 0, n));
    assertEquals(3, CharsUtil.findHtmlCommentEnd(s1, 1, n));
    assertEquals(3, CharsUtil.findHtmlCommentEnd(s1, 2, n));
    assertEquals(3, CharsUtil.findHtmlCommentEnd(s1, 3, n));
    assertEquals(3, CharsUtil.findHtmlCommentEnd(s1, 1, 6));
    assertEquals(-1, CharsUtil.findHtmlCommentEnd(s1, 0, 5));
    assertEquals(-1, CharsUtil.findHtmlCommentEnd(s1, 1, 5));
    assertEquals(-1, CharsUtil.findHtmlCommentEnd(s1, 2, 5));
    assertEquals(-1, CharsUtil.findHtmlCommentEnd(s1, 3, 5));
    assertEquals(9, CharsUtil.findHtmlCommentEnd(s1, 4, n));
    assertEquals(9, CharsUtil.findHtmlCommentEnd(s1, 4, n));
    assertEquals(17, CharsUtil.findHtmlCommentEnd(s1, 10, n));
    for (int i = 0; i < n; ++i) {
      for (int j : new int[] { n, 0, Math.min(i+2, n),
                               Math.min(i+4, n), Math.min(i+8, n) }) {
        assertEquals(
            CharsUtil.findHtmlCommentEnd(s1, i, j),
            CharsUtil.findHtmlCommentEnd(s2, i, j));
      }
    }
  }

  public final void testFindEndTag() {
    String s1 = "foo</bar </baz</";
    char[] s2 = s1.toCharArray();
    int n = s1.length();
    assertEquals(3, CharsUtil.findEndTag(s1, 0, n));
    assertEquals(3, CharsUtil.findEndTag(s1, 1, n));
    assertEquals(3, CharsUtil.findEndTag(s1, 2, n));
    assertEquals(3, CharsUtil.findEndTag(s1, 3, n));
    assertEquals(3, CharsUtil.findEndTag(s1, 3, 6));
    assertEquals(3, CharsUtil.findEndTag(s1, 3, 5));
    assertEquals(-1, CharsUtil.findEndTag(s1, 3, 4));
    assertEquals(9, CharsUtil.findEndTag(s1, 4, n));
    for (int i = 0; i < n; ++i) {
      for (int j : new int[] { n, 0, Math.min(i+1, n), Math.min(i+5, n) }) {
        assertEquals(
            CharsUtil.findEndTag(s1, i, j),
            CharsUtil.findEndTag(s2, i, j));
      }
    }
  }

  private void assertStartsWithOne(
      boolean golden, boolean ignoreCase, String s, int off, int end,
      String prefix) {
    char[] s2 = s.toCharArray();
    String msg = s + "[" + off + ":" + end + "]" + (ignoreCase ? "i" : "")
        + " vs " + prefix;
    if (ignoreCase) {
      assertEquals(
          msg, golden, CharsUtil.startsWithIgnoreCase(s, off, end, prefix));
      assertEquals(
          msg, golden, CharsUtil.startsWithIgnoreCase(s2, off, end, prefix));
    } else {
      assertEquals(
          msg, golden, CharsUtil.startsWith(s, off, end, prefix));
      assertEquals(
          msg, golden, CharsUtil.startsWith(s2, off, end, prefix));
    }
  }

  private void assertStartsWith(boolean golden, String s, String prefix) {
    String ucase = s.toUpperCase();
    assertStartsWithOne(golden, false, s, 0, s.length(), prefix);
    assertStartsWithOne(golden, false, " " + s, 1, s.length() + 1, prefix);
    assertStartsWithOne(golden, false, s + " ", 0, s.length(), prefix);
    if (!ucase.equals(s) && !prefix.toUpperCase().equals(prefix)) {
      assertStartsWithOne(false, false, ucase, 0, s.length(), prefix);
      assertStartsWithOne(false, false, " " + ucase, 1, s.length() + 1, prefix);
      assertStartsWithOne(false, false, ucase + " ", 0, s.length(), prefix);
    }
    assertStartsWithOne(golden, true, s, 0, s.length(), prefix);
    assertStartsWithOne(golden, true, " " + s, 1, s.length() + 1, prefix);
    assertStartsWithOne(golden, true, s + " ", 0, s.length(), prefix);
    assertStartsWithOne(golden, true, s, 0, s.length(), prefix);
    assertStartsWithOne(golden, true, " " + s, 1, s.length() + 1, prefix);
    assertStartsWithOne(golden, true, s + " ", 0, s.length(), prefix);
  }

  public final void testStartsWith() {
    assertStartsWith(true, "", "");
    assertStartsWith(true, "foo", "");
    assertStartsWith(true, "foo", "f");
    assertStartsWith(true, "foo", "fo");
    assertStartsWith(true, "foo", "foo");
    assertStartsWith(true, "foobar", "foo");
    assertStartsWith(false, "foobar", "bar");
    assertStartsWith(false, "foobar", "oobar");
    assertStartsWith(false, "foobar", "x");
    assertStartsWith(false, "foobar", "foobarlonger");
    assertStartsWith(false, "foo", "foo ");
    assertStartsWith(false, "foo", " foo");
  }

  public final void testContainsIgnoreCase() {
    String s = "foobarbazfooboooicks";
    char[] s2 = s.toCharArray();
    String t = "foob";

    for (int i = 0, n = s.length(); i < n; ++i) {
      for (int j = i+1; j < n; ++j) {
        String ssub = s.substring(i, j);
        for (int k = 0, m = t.length(); k < m; ++k) {
          for (int l = k; l < m; ++l) {
            String tsub = t.substring(k, l);
            boolean canon = ssub.contains(tsub);
            String msg = ssub + " vs " + tsub + " with [" + i + ":" + j + "]";
            assertEquals(msg, canon,
                CharsUtil.containsIgnoreCase(s, i, j, tsub));
            assertEquals(msg + " chars",
                canon, CharsUtil.containsIgnoreCase(s2, i, j, tsub));
          }
        }
      }
    }

    String[] strs = new String[] {
        "foobar", "foo", "bar", "foobarbaz", "bazfoobar", "BAR", "fooBAR",
        "FOOBAR", "", "-", "o", "O", "barfoo" };
    for (String a : strs) {
      char[] a2 = a.toCharArray();
      for (String b : strs) {
        assertEquals(
            CharsUtil.containsIgnoreCase(a, 0, a.length(), b),
            CharsUtil.containsIgnoreCase(a2, 0, a.length(), b));
      }
    }
  }

  public final void testLcase() {
    assertEquals('0', CharsUtil.lcase('0'));
    assertEquals('9', CharsUtil.lcase('9'));
    assertEquals('@', CharsUtil.lcase('@'));
    assertEquals('a', CharsUtil.lcase('A'));
    assertEquals('z', CharsUtil.lcase('Z'));
    assertEquals('[', CharsUtil.lcase('['));
    assertEquals('`', CharsUtil.lcase('`'));
    assertEquals('a', CharsUtil.lcase('a'));
    assertEquals('z', CharsUtil.lcase('z'));
    assertEquals('{', CharsUtil.lcase('{'));
  }

  public final void testAppend() {
    StringBuilder sb = new StringBuilder();
    CharsUtil.append(sb, "food", 1, 3);
    CharsUtil.append(sb, new char[] { 'f', 'o', 'o', 'd' }, 1, 3);
    assertEquals("oooo", sb.toString());
  }
}
