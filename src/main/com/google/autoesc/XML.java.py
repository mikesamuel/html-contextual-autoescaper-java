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

/** XML contains utilities for dealing with XML contexts. */
class XML {

  static final ReplacementTable REPLACEMENT_TABLE = new ReplacementTable()
      .add('`', "&#96;")
      .add('<', "&lt;")
      .add('>', "&gt;")
      .add('+', "&#43;")
      .add('\'', "&#39;")
      .add('&', "&amp;")
      .add('"', "&#34;")
      // XML cannot contain NULs even if encoded, so treat NUL as an error case
      // and replace it with U+FFFD, the replacement character.
      .add((char) 0, "\ufffd");

  static final ReplacementTable NORM_REPLACEMENT_TABLE
      = new ReplacementTable(REPLACEMENT_TABLE)
      .add('&', null);

  /** escapeOnto escapes for inclusion in XML text. */
  static void escapeOnto(@Nullable Object o, Writer out) throws IOException {
    String safe = ContentType.Markup.derefSafeContent(o);
    if (safe != null) {
      out.write(safe);
      return;
    }
    REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  /** escapeOnto escapes for inclusion in XML text. */
  static void escapeOnto(String s, int off, int end, Writer out)
      throws IOException {
    REPLACEMENT_TABLE.escapeOnto(s, off, end, out);
  }

  /**
   * normalizeOnto escapes for inclusion in XML text but does not break
   * existing entities.
   */
  static void normalizeOnto(@Nullable Object o, Writer out) throws IOException {
    String safe = ContentType.Markup.derefSafeContent(o);
    if (safe != null) {
      out.write(safe);
      return;
    }
    NORM_REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  /**
   * normalizeOnto escapes for inclusion in XML text but does not break
   * existing entities.
   */
  static void normalizeOnto(String s, int off, int end, Writer out)
      throws IOException {
    NORM_REPLACEMENT_TABLE.escapeOnto(s, off, end, out);
  }

  /**
   * escapeCDATAOnto emits the text unchanged assuming it will go inside a
   * {@code <![CDATA[...]]>} block unless the string contains {@code "]]>"} or
   * starts or ends with a prefix or suffix thereof in which case it splits the
   * CDATA section around that chunk and resumes on the other side:
   * {@code "foo]]>bar"} &rarr; {@code "foo]]]]><![CDATA[>bar"}.
   * Any buggy regex based XML parsers that allow CDATA sections to contain 
   * {@code "]]>"} by using surrounding tags as boundaries (e.g. looking for
   * {@code /<tag><!\[CDATA\[(.*?)\]\]><\/tag>/} can simply remove all
   * all occurrences of {@code "]]><![CDATA["}.
   */
  static void escapeCDATAOnto(String s, int off, int end, Writer out)
      throws IOException {
    if (off >= end) { return; }

    // Elide all NULs which are not strictly allowed in XML.
    for (int i = off; i < end; ++i) {
      if (s.charAt(i) == 0) {
        StringBuilder sb = new StringBuilder(end - off);
        for (i = off; i < end; ++i) {
          char ch = s.charAt(i);
          if (ch != 0) { sb.append(ch); }
        }
        escapeCDATAOnto(sb.toString(), 0, sb.length(), out);
        return;
      }
    }

    // Make sure the start of the string can't combine with any characters
    // already on out to break out of the CDATA section.
    {
      char ch0 = s.charAt(off);
      if (ch0 == '>'
          || (ch0 == ']' && off + 1 < end && s.charAt(off + 1) == '>')) {
        out.write("]]><![CDATA[");
      }
    }
    for (int i = off; i < end - 2; ++i) {
      if (s.charAt(i)== ']' && s.charAt(i + 1) == ']'
          && s.charAt(i + 2) == '>') {
        out.write(s, off, i - off);
        out.write("]]]]><![CDATA[>");
        i += 2;
        off = i + 1;
      }
    }
    out.write(s, off, end - off);
    // Prevent the next character written to out from combining with trailing
    // characters from s to form "]]>".
    if (s.charAt(end - 1) == ']') {
      out.write("]]><![CDATA[");
    }
  }

  /**
   * escapeCDATAOnto escapes for inclusion in an XML {@code <![CDATA[...]]>}
   * section.
   */
  static void escapeCDATAOnto(@Nullable Object o, Writer out)
      throws IOException {
    if (o == null) { return; }
    if (o instanceof char[]) {
      char[] chars = (char[]) o;
      escapeCDATAOnto(chars, 0, chars.length, out);
    } else {
      String s = o.toString();
      escapeCDATAOnto(s, 0, s.length(), out);
    }
  }

}
"""  # Fix emacs syntax highlighting "

import dupe_methods
print dupe_methods.dupe(src)
