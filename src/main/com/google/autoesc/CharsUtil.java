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

/**
 * Utilities that make it easier to write individual methods that work equally
 * well when a String input is replaced with a char[] input.
 * <p>
 * All of the case-ignoring deals only with lower-7 letters since that
 * is the case-equivalence scheme used for identifiers in HTML, CSS, and URIs.
 */
final class CharsUtil {
  private CharsUtil() { /* uninstantiable */ }

  static char charAt(char[] s, int i) { return s[i]; }

  static int codePointAt(char[] s, int i) {
    return Character.codePointAt(s, i);
  }

  /** findHtmlCommentEnd is equivalent to {@code indexOf("-->")}. */
  static int findHtmlCommentEnd(String s, int off, int end) {
    int i = s.indexOf("-->", off);
    if (i + 3 > end) { return -1; }
    return i;
  }

  static int findHtmlCommentEnd(char[] s, int off, int end) {
    for (int i = off; i < end; i += 3) {
      char c = s[i];
      if (c == '-') {
        if (i+1 < end) {
          c = s[i+1];
          if (c == '>') {
            if (i > off && s[i] == '-') { return i-1; }
          } else if (c == '-' && i+2 < end && s[i+2] == '>') { return i; }
        }
      } else if (c == '>') {
        if (i - 2 >= off && s[i-1] == '-' && s[i-2] == '-') { return i - 2; }
      }
    }
    return -1;
  }

  /** findEndTag is equivalent to {@code indexOf("</")}. */
  static int findEndTag(String s, int off, int end) {
    int i = s.indexOf("</", off);
    if (i + 2 > end) { return -1; }
    return i;
  }

  static int findEndTag(char[] s, int off, int end) {
    for (int i = off; i < end; i += 2) {
      char c = s[i];
      if (c == '<') {
        if (i+1 < end && s[i+1] == '/') { return i; }
      } else if (c == '/' && i != off && s[i-1] == '<') {
        return i-1;
      }
    }
    return -1;
  }

  static boolean startsWith(
      String s, int off, int end, String t) {
    int n = t.length();
    if (end - off < n) { return false; }
    for (int i = 0; i < n; ++off, ++i) {
      if (s.charAt(off) != t.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  static boolean startsWith(
      char[] s, int off, int end, String t) {
    int n = t.length();
    if (end - off < n) { return false; }
    for (int i = 0; i < n; ++off, ++i) {
      if (s[off] != t.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  static boolean startsWithIgnoreCase(
      String s, int off, int end, String lowerCase) {
    int n = lowerCase.length();
    if (end - off < n) { return false; }
    for (int i = 0; i < n; ++off, ++i) {
      char ch = lcase(s.charAt(off));
      if (ch != lowerCase.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  static boolean startsWithIgnoreCase(
      char[] s, int off, int end, String lowerCase) {
    int n = lowerCase.length();
    if (end - off < n) { return false; }
    for (int i = 0; i < n; ++off, ++i) {
      char ch = lcase(s[off]);
      if (ch != lowerCase.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  static int indexOf(String s, int off, int end, char ch) {
    for (int i = off; i < end; ++i) {
      if (s.charAt(i) == ch) { return i - off; }
    }
    return -1;
  }

  static int indexOf(char[] s, int off, int end, char ch) {
    for (int i = off; i < end; ++i) {
      if (s[i] == ch) { return i - off; }
    }
    return -1;
  }

  static boolean containsIgnoreCase(
      String s, int off, int end, String lowerCase) {
    int n = lowerCase.length();
    if (n == 0) { return true; }
    char ch0 = lowerCase.charAt(0);
    outer:
    for (int i = off, limit = end - n + 1; i < limit; ++i) {
      if (lcase(s.charAt(i)) == ch0) {
        for (int j = 1, k = i+1; j < n; ++j, ++k) {
          if (lcase(s.charAt(k)) != lowerCase.charAt(j)) {
            continue outer;
          }
        }
        return true;
      }
    }
    return false;
  }

  static boolean containsIgnoreCase(
      char[] s, int off, int end, String lowerCase) {
    int n = lowerCase.length();
    if (n == 0) { return true; }
    char ch0 = lowerCase.charAt(0);
    outer:
    for (int i = off, limit = end - n + 1; i < limit; ++i) {
      char ch = lcase(s[i]);
      if (ch == ch0) {
        for (int j = 1, k = i+1; j < n; ++j, ++k) {
          if (lcase(s[k]) != lowerCase.charAt(j)) {
            continue outer;
          }
        }
        return true;
      }
    }
    return false;
  }

  static char lcase(char ch) {
    if ('A' <= ch && ch <= 'Z') { ch |= 32; }
    return ch;
  }

  static void append(StringBuilder sb, String s, int off, int end) {
    sb.append(s, off, end);
  }

  static void append(StringBuilder sb, char[] s, int off, int end) {
    sb.append(s, off, end - off);
  }
}
