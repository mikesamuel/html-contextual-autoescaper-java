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
import java.io.Writer;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utilieis for deailing with CSS3 contexts.
 */
class CSS {
  /**
   * decodeCSS decodes CSS3 escapes given a sequence of stringchars.
   * If there is no change, it returns the input, otherwise it returns a slice
   * backed by a new array.
   * http://www.w3.org/TR/css3-syntax/#SUBTOK-stringchar defines stringchar.
   */
  static String decodeCSS(String s, int off, int end) {
    int i = off;
    while (i < end && s.charAt(i) != '\\') { ++i; }
    if (i == end) {
      return s.substring(off, end);
    }
    // The UTF-8 sequence for a codepoint is never longer than 1 + the
    // number hex digits need to represent that codepoint, so len(s) is an
    // upper bound on the output length.
    StringBuilder sb = new StringBuilder(end - off);
    while (true) {
      sb.append(s, off, i);
      if (i+1 >= end) {
        return sb.toString();
      }
      // http://www.w3.org/TR/css3-syntax/#SUBTOK-escape
      // escape ::= unicode | '\' [#x20-#x7E#x80-#xD7FF#xE000-#xFFFD#x10000-#x10FFFF]
      if (isHex(s.charAt(i+1))) {
        // http://www.w3.org/TR/css3-syntax/#SUBTOK-unicode
        //   unicode ::= '\' [0-9a-fA-F]{1,6} wc?
        int j = i+2;
        while (j < end && j < i+7 && isHex(s.charAt(j))) {
          j++;
        }
        int rune = hexDecode(s, i+1, j);
        if (rune > Character.MAX_CODE_POINT) {
          rune >>= 4;
          --j;
        }
        sb.appendCodePoint(rune);
        // The optional space at the end allows a hex
        // sequence to be followed by a literal hex.
        // string(decodeCSS([]byte(`\A B`))) == "\nB"
        off = skipCSSSpace(s, j, end);
      } else {
        // `\\` decodes to `\` and `\"` to `"`.
        int rune = s.codePointAt(i+1);
        sb.appendCodePoint(rune);
        off = i + 1 + Character.charCount(rune);
      }
      i = off;
      while (i < end && s.charAt(i) != '\\') { ++i; }
    }
  }

  /** isHex returns whether the given character is a hex digit. */
  static boolean isHex(char c) {
    return ('0' <= c && c <= '9') || ('a' <= c && c <= 'f')
      || ('A' <= c && c <= 'F');
  }

  /** hexDecode decodes a short hex digit sequence: "10" -> 16. */
  static int hexDecode(String s, int off, int end) {
    int n = 0;
    for (int i = off; i < end; i++) {
      char c = s.charAt(i);
      n <<= 4;
      if ('0' <= c && c <= '9') {
        n |= c - '0';
      } else if ('a' <= c && c <= 'f') {
        n |= (c-'a') + 10;
      } else if ('A' <= c && c <= 'F') {
        n |= (c-'A') + 10;
      } else {
        throw new AssertionError("Bad hex digit in " + c);
      }
    }
    return n;
  }

  /** skipCSSSpace returns a suffix of c, skipping over a single space. */
  static int skipCSSSpace(String s, int off, int end) {
    if (off == end) { return off; }
    // wc ::= #x9 | #xA | #xC | #xD | #x20
    switch (s.charAt(off)) {
      case '\t': case '\n': case '\f': case ' ':
        return off + 1;
      case '\r':
        // This differs from CSS3's wc production because it contains a
        // probable spec error whereby wc contains all the single byte
        // sequences in nl (newline) but not CRLF.
        if (end - off >= 2 && s.charAt(off + 1) == '\n') {
          return off + 2;
        }
        return off + 1;
    }
    return off;
  }

  /** True if cp is in the CSS3 wc production. */
  static boolean isCSSSpace(int cp) {
    switch (cp) {
      case '\t': case '\n': case '\f': case '\r': case ' ': return true;
    }
    return false;
  }

  /** Escapes HTML and CSS special characters using {@code \<hex>+} escapes. */
  private static final ReplacementTable REPLACEMENT_TABLE
    = new ReplacementTable() {
        @Override
        protected void writeReplacement(
            int cp, String repl, int lookahead, Writer out)
            throws IOException {
          out.write(repl);
          if (isHex(repl.charAt(repl.length()-1)) &&
              (lookahead == -1 || isHex((char) lookahead)
               || isCSSSpace((char) lookahead))) {
            // Separate the hex-escape from any following hex-digits.
            out.write(' ');
          }
        }
      }
      .add((char) 0, "\\0")
      .add('\t', "\\9")
      .add('\n', "\\a")
      .add('\f', "\\c")
      .add('\r', "\\d")
      // Encode HTML specials as hex so the output can be embedded
      // in HTML attributes without further encoding.
      .add('"', "\\22")
      .add('&', "\\26")
      .add('\'', "\\27")
      .add('(', "\\28")
      .add(')', "\\29")
      .add('+', "\\2b")
      .add('/', "\\2f")
      .add(':', "\\3a")
      .add(';', "\\3b")
      .add('<', "\\3c")
      .add('>', "\\3e")
      .add('\\', "\\\\")
      .add('`', "\\60")
      .add('{', "\\7b")
      .add('}', "\\7d");

  /**
   * escapeStrOnto escapes HTML and CSS special characters using
   * {@code \<hex>+} escapes.
   */
  static void escapeStrOnto(@Nullable Object o, Writer out) throws IOException {
    REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  /**
   * filterValueOnto allows innocuous CSS values in the output including CSS
   * quantities (10px or 25%), ID or class literals (#foo, .bar), keyword values
   * (inherit, blue), and colors (#888).
   * It filters out unsafe values, such as those that affect token boundaries,
   * and anything that might execute scripts.
   */
  static void filterValueOnto(@Nullable Object o, Writer out)
      throws IOException {
    String safe = ContentType.CSS.derefSafeContent(o);
    if (safe != null) {
      out.write(safe);
      return;
    }
    String s = ReplacementTable.toString(o);
    s = decodeCSS(s, 0, s.length());

    int n = s.length();
    StringBuilder idchars = new StringBuilder(n);
    // CSS3 error handling is specified as honoring string boundaries per
    // http://www.w3.org/TR/css3-syntax/#error-handling :
    //     Malformed declarations. User agents must handle unexpected
    //     tokens encountered while parsing a declaration by reading until
    //     the end of the declaration, while observing the rules for
    //     matching pairs of (), [], {}, "", and '', and correctly handling
    //     escapes. For example, a malformed declaration may be missing a
    //     property, colon (:) or value.
    // So we need to make sure that values do not have mismatched bracket
    // or quote characters to prevent the browser from restarting parsing
    // inside a string that might embed JavaScript source.
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case 0: case '"': case '\'': case '(': case ')': case '/': case ';':
        case '@': case '[': case '\\': case ']': case '`': case '{': case '}':
          out.write("ZautoescZ");
          return;
        case '-':
          // Disallow <!-- or -->.
          // -- should not appear in valid identifiers.
          if (i != 0 && '-' == s.charAt(i-1)) {
            out.write("ZautoescZ");
            return;
          }
          break;
        default:
          if (ch < 0x80 && isCSSNmchar(ch)) {
            if ('A' <= ch && ch <= 'Z') { ch |= 32; }
            idchars.append(ch);
          }
      }
    }
    String id = idchars.toString();
    if (id.contains("expression") || id.contains("mozbinding")) {
      s = "ZautoescZ";
    }
    out.write(s);
  }

  /**
   * isCSSNmchar returns whether rune is allowed anywhere in a CSS identifier.
   */
  @VisibleForTesting
  static boolean isCSSNmchar(int rune) {
    // Based on the CSS3 nmchar production but ignores multi-rune escape
    // sequences.
    // http://www.w3.org/TR/css3-syntax/#SUBTOK-nmchar
    return ('a' <= rune && rune <= 'z') ||
      ('A' <= rune && rune <= 'Z') ||
      ('0' <= rune && rune <= '9') ||
      '-' == rune ||
      '_' == rune ||
      // Non-ASCII cases below.
      (0x80 <= rune && rune <= 0xd7ff) ||
      (0xe000 <= rune && rune <= 0xfffd) ||
      (0x10000 <= rune && rune <= 0x10ffff);
  }
}
