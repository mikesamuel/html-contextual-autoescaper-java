#!python

# This generates a java source file by taking each method that has a
# parameters (String s, int off, int end) and generating a copy that
# takes (char[] s, int off, int end).

# Fix emacs syntax highlighting "

src = r"""
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

/**
 * Utilities for dealing with URL contexts.
 */
class URL {
  /**
   * escapeURLOnto normalizes (when norm is true) or escapes its input to
   * produce a valid hierarchical or opaque URL part.
   */
  static void escapeOnto(boolean norm, Object o, Writer out)
      throws IOException {
    String safe = ContentType.URL.derefSafeContent(o);
    if (safe != null) {
      escapeOnto(safe, 0, safe.length(), true, out);
    } else {
      String s = ReplacementTable.toString(o);
      escapeOnto(s, 0, s.length(), norm, out);
    }
  }

  private static final boolean[] URL_NO_ENCODE = new boolean[127];
  private static final boolean[] NORM_URL_NO_ENCODE = new boolean[127];

  static {
    NORM_URL_NO_ENCODE['!'] = true;
    NORM_URL_NO_ENCODE['#'] = true;
    NORM_URL_NO_ENCODE['$'] = true;
    NORM_URL_NO_ENCODE['&'] = true;
    NORM_URL_NO_ENCODE['*'] = true;
    NORM_URL_NO_ENCODE['+'] = true;
    NORM_URL_NO_ENCODE[','] = true;
    NORM_URL_NO_ENCODE['/'] = true;
    NORM_URL_NO_ENCODE[':'] = true;
    NORM_URL_NO_ENCODE[';'] = true;
    NORM_URL_NO_ENCODE['='] = true;
    NORM_URL_NO_ENCODE['?'] = true;
    NORM_URL_NO_ENCODE['@'] = true;
    NORM_URL_NO_ENCODE['['] = true;
    NORM_URL_NO_ENCODE[']'] = true;

    // Single quote and parens are sub-delims in RFC 3986, but we
    // escape them so the output can be embedded in in single
    // quoted attributes and unquoted CSS url(...) constructs.
    // Single quotes are reserved in URLs, but are only used in
    // the obsolete "mark" rule in an appendix in RFC 3986
    // so can be safely encoded.

    // Unreserved according to RFC 3986 sec 2.3
    // "For consistency, percent-encoded octets in the ranges of
    // ALPHA (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen (%2D),
    // period (%2E), underscore (%5F), or tilde (%7E) should not be
    // created by URI producers.
    URL_NO_ENCODE['-'] = true;
    URL_NO_ENCODE['.'] = true;
    URL_NO_ENCODE['_'] = true;
    URL_NO_ENCODE['~'] = true;
    NORM_URL_NO_ENCODE['-'] = true;
    NORM_URL_NO_ENCODE['.'] = true;
    NORM_URL_NO_ENCODE['_'] = true;
    NORM_URL_NO_ENCODE['~'] = true;

    // Unreserved according to RFC 3986 sec 2.3
    for (int i = '0'; i <= '9'; ++i) {
      URL_NO_ENCODE[i] = NORM_URL_NO_ENCODE[i] = true;
    }
    for (int i = 'A'; i <= 'Z'; ++i) {
      URL_NO_ENCODE[i] = NORM_URL_NO_ENCODE[i] = true;
    }
    for (int i = 'a'; i <= 'z'; ++i) {
      URL_NO_ENCODE[i] = NORM_URL_NO_ENCODE[i] = true;
    }
  }

  /**
   * escapeURLOnto normalizes (when norm is true) or escapes its input to
   * produce a valid hierarchical or opaque URL part.
   */
  static void escapeOnto(
      String s, int offset, int end, boolean norm, Writer out)
      throws IOException {
    boolean[] noEncode = norm ? NORM_URL_NO_ENCODE : URL_NO_ENCODE;
    int off = offset;
    for (int i = off, nc; i < end; i += nc) {
      int cp = s.codePointAt(i);
      nc = Character.charCount(cp);
      if (cp < noEncode.length && noEncode[cp]) { continue; }
      // When normalizing do not re-encode valid escapes.
      if (cp == '%' && norm && i+2 < end && isHex(s.charAt(i+1))
          && isHex(s.charAt(i+2))) {
        i = i+2;  // Skip hex digits.
        continue;
      }
      out.write(s, off, i - off);
      off = i + nc;
      // This assumes that all URLs use UTF-8 as the content-encoding.
      // This is similar to the URI to IRI encoding scheme defined in
      // section 3.1 of RFC 3987, and behaves the same as the EcmaScript builtin
      // encodeURIComponent.
      // It should not cause any misencoding of URLs in pages with
      // Content-type: text/html;charset=UTF-8.
      if (cp < 0x800) {
        if (cp < 0x80) {
          emitPctOctet((byte)(cp & 0x7f), out);
        } else {
          emitPctOctet((byte)(0xc0 | (cp >> 6)), out);
          emitPctOctet((byte)(0x80 | (cp & 0x3f)), out);
        }
      } else {
        if (cp < 0x10000) {
          emitPctOctet((byte)(0xe0 | (cp >> 12)), out);
        } else {
          emitPctOctet((byte)(0xf0 | (cp >> 18)), out);
          emitPctOctet((byte)(0x80 | ((cp >> 12) & 0x3f)), out);
        }
        emitPctOctet((byte)(0x80 | ((cp >> 6) & 0x3f)), out);
        emitPctOctet((byte)(0x80 | (cp & 0x3f)), out);
      }
    }
    out.write(s, off, end - off);
  }

  private static boolean isHex(int cp) {
    return ('0' <= cp && cp <= '9') || ('A' <= cp && cp <='F')
      || ('a' <= cp && cp <= 'f');
  }

  private static void emitPctOctet(byte octet, Writer out) throws IOException {
    out.write('%');
    out.write("0123456789abcdef".charAt((octet >> 4) & 0xf));
    out.write("0123456789abcdef".charAt(octet & 0xf));
  }

  static final String FILTER_REPLACEMENT_URL = "#ZautoescZ";

  /**
   * urlFilter returns its input unless it contains an unsafe protocol in which
   * case it defangs the entire URL.
   */
  static String filterURL(@Nullable Object o) {
    String safe = ContentType.URL.derefSafeContent(o);
    if (safe != null) { return safe; }
    String s = ReplacementTable.toString(o);
    return urlPrefixAllowed(s, 0, s.length()) ? s : FILTER_REPLACEMENT_URL;
  }

  static boolean urlPrefixAllowed(String s, int off, int end) {
    int colon = CharsUtil.indexOf(s, off, end, ':');
    if (colon < 0) { return true; }
    int slash = CharsUtil.indexOf(s, off, colon, '/');
    if (slash >= 0) { return true; }
    switch (colon - off) {
    case 4:
      return CharsUtil.startsWithIgnoreCase(s, off, colon, "http");
    case 5:
      return CharsUtil.startsWithIgnoreCase(s, off, colon, "https");
    case 6:
      return CharsUtil.startsWithIgnoreCase(s, off, colon, "mailto");
    }
    return false;
  }
}
"""  # Fix emacs syntax highlighting "

import dupe_methods
print dupe_methods.dupe(src)
