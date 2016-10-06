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
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * ReplacementTable maps strings in one language (e.g. plain text) to strings
 * in another (e.g. HTML) by transforming the strings character by character.
 */
class ReplacementTable {
  private String[] table;
  private int[] nonAscii;
  private String[] nonAsciiReplacements;
  private int minNonAscii;

  ReplacementTable() {
    this.table = new String[0];
    this.nonAscii = new int[0];
    this.nonAsciiReplacements = new String[0];
    this.minNonAscii = Integer.MAX_VALUE;
  }

  ReplacementTable(ReplacementTable t) {
    this.table = t.table.clone();
    replaceNonAscii(t.nonAscii.clone(), t.nonAsciiReplacements.clone());
  }

  ReplacementTable add(char ch, @Nullable String repl) {
    if (ch >= 128) { throw new IllegalArgumentException(); }
    int cp = ch & 0xff;
    if (table.length <= cp) {
      String[] ntable = new String[
          Math.max(cp + 1, Math.min(128, table.length * 2))];
      System.arraycopy(table, 0, ntable, 0, table.length);
      table = ntable;
    }
    table[cp] = repl;
    return this;
  }

  ReplacementTable replaceNonAscii(
      int[] codePointsSorted, String[] replacements) {
    this.nonAscii = codePointsSorted.clone();
    this.nonAsciiReplacements = replacements.clone();
    this.minNonAscii = codePointsSorted.length != 0
        ? codePointsSorted[0] : Integer.MAX_VALUE;
    return this;
  }

  static String toString(@Nullable Object o) {
    if (o == null) {
      return "";
    } else if (o instanceof char[]) {
      char[] ca = (char[]) o;
      int len = ca.length;
      while (len != 0 && ca[len-1] == 0) { --len; }
      return new String(ca, 0, len);
    }
    return o.toString();
  }

  private String replacement(int cp) {
    // HACK: This is not consistently called with a codepoint, because no
    // clients replace supplemental codepoints.
    if (cp < table.length) {
      return table[cp];
    } else if (cp >= minNonAscii) {
      int i = Arrays.binarySearch(nonAscii, cp);
      if (i >= 0) {
        return nonAsciiReplacements[i];
      }
    }
      return null;
  }

  void escapeOnto(String s, Writer out) throws IOException {
    escapeOnto(s, 0, s.length(), out);
  }

  void escapeOnto(String s, int offset, int end, Writer out)
      throws IOException {
    int off = offset;
    if (off == end) {
      writeEmpty(out);
      return;
    }
    for (int i = off; i < end; ++i) {
      char ch = s.charAt(i);
      String repl = replacement(ch);
      if (repl != null) {
        out.write(s, off, i - off);
        off = i + 1;
        int next = off<end ? s.charAt(off) : -1;
        writeReplacement(ch, repl, next, out);
      }
    }
    out.write(s, off, end - off);
  }

  void escapeOnto(@Nullable Object o, Writer out) throws IOException {
    if (o == null) { return; }
    if (o instanceof char[]) {
      escapeOnto((char[]) o, out);
    } else if (o instanceof Character) {
      escapeOnto(((Character) o).charValue(), out);
    } else {
      escapeOnto(o.toString(), out);
    }
  }

  void escapeOnto(char[] s, Writer out) throws IOException {
    int len = s.length;
    while (len != 0 && s[len - 1] == 0) {
      --len;
    }
    escapeOnto(s, 0, len, out);
  }

  void escapeOnto(char[] s, int offset, int end, Writer out)
      throws IOException {
    if (offset == end) {
      writeEmpty(out);
      return;
    }
    int off = offset;
    for (int i = off; i < end; ++i) {
      char ch = s[i];
      String repl = replacement(ch);
      if (repl != null) {
        out.write(s, off, i - off);
        off = i + 1;
        int next = off < end ? s[off] : -1;
        writeReplacement(ch, repl, next, out);
      }
    }
    out.write(s, off, end - off);
  }

  void escapeOnto(int cp, Writer out) throws IOException {
    String repl = replacement(cp);
    if (repl != null) {
      writeReplacement(cp, repl, -1, out);
    } else {
      out.write(cp);
    }
  }

  /**
   * @param cp The codepoint being escaped.
   * @param repl The replacement for cp.
   * @param lookahead The next code-unit or -1 if not known.
   * @param out The writer to receive the output.
   */
  @SuppressWarnings("static-method")  // Overridable
  protected void writeReplacement(
      int cp, String repl, int lookahead, Writer out)
      throws IOException {
    out.write(repl);
  }

  /**
   * May be overridden to take an alternate action when presented with the
   * empty string.
   * @param out receives any alternate output.
   * @throws IOException on failure to write to out.
   */
  protected void writeEmpty(Writer out) throws IOException {
    // Default implementation is a NOOP.
  }
}
