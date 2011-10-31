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

import java.lang.reflect.Array;

import junit.framework.TestCase;

public class BenchmarkEscapersTest extends TestCase {

  private static final String DENSE_SNIPPET = (
      "CDcd])|[Ff](?:1[89]|3[579EFef]|4[0-79A-Fa-f]|5[\\dA-Fa-f]|7[1-9A-Fa" +
      "-f]|8[0-46-9ABab]|9[0-579A-Fa-f]|[26]\\d|[Aa][\\dA-Da-d]|[Bb][1-79]" +
      "))|1(?:0(?:[ABDEabde][\\dA-Fa-f]|[Cc][0-5]|[Ff][0-6])|1(?:5[\\dFf]|" +
      "[0-46-9B-Eb-e][\\dA-Fa-f]|[Aa][0-289A-Fa-f]|[Ff]\\d)|[Ee](?:9[\\dAB" +
      "ab]|[0-8A-Ea-e][\\dA-Fa-f]|[Ff]\\d)|[Ff](?:5[0-79BDFbdf]|7[\\dA-Da-" +
      "d]|[023689Aa][\\dA-Fa-f]|[14][0-589A-Da-d]|[Bb][^\\W5DF-Z_df-z]|[CF" +
      "cf][2-46-9A-Ca-c]|[Dd][0-36-9ABab]|[Ee][\\dA-Ca-c]))|2(?:0(?:40|[37" +
      "][Ff]|[Dd][\\dA-Ca-c]|[Ee]1)|1(?:0[27A-Fa-f]|1[0-359A-Da-d]|2[468A-" +
      "DFa-df]|3[013-8]))|3(?:0(?:05|2[A-Fa-f]|3[1-5]|9[0-49ADEade]|[4Aa][" +
      "1-9A-Fa-f]|[5-8B-Eb-e][\\dA-Fa-f]|[Ff][\\dAC-Eac-e])|1(?:0[5-9A-Fa-" +
      "f]|2[\\dA-Ca-c]|3[1-9A-Fa-f]|8[\\dA-Ea-e]|[14-7][\\dA-Fa-f]))|4[EFe" +
      "f][\\dA-Fa-f]{2}|9(?:[\\dA-Ea-e][\\dA-Fa-f]{2}|[Ff](?:\\d[\\dA-Fa-f" +
      "]|[Aa][0-5]))|[5-8BCbc][\\dA-Fa-f]{3}|[Aa][C-Fc-f][\\dA-Fa-f]{2}|[D" +
      "d](?:7(?:\\d[\\dA-Fa-f]|[Aa][0-3])|[0-6][\\dA-Fa-f]{2})|[Ff](?:[9Cc" +
      "][\\dA-Fa-f]{2}|[Aa](?:2[\\dA-Da-d]|[01][\\dA-Fa-f])|[Bb](?:0[0-6]|" +
      "1[3-7EFef]|2[0-8A-Fa-f]|3[0-689A-CEa-ce]|4[^\\W25G-Z_g-z]|[5-9AEFae" +
      "f][\\dA-Fa-f]|[Bb][01]|[Dd][3-9A-Fa-f])|[Dd](?:3[\\dA-Da-d]|9[2-9A-" +
      "Fa-f]|[0-25-8ABab][\\dA-Fa-f]|[Cc][0-7]|[Ff][\\dABab])|[Ee](?:2[0-3" +
      "]|3[34]|4[D-Fd-f]|7[^\\W35G-Z_g-z]|[89A-Ea-e][\\dA-Fa-f]|[Ff][\\dA-" +
      "Ca-c])|[Ff](?:1\\d|3[\\dAFaf]|5[\\dAa]|6[6-9A-Fa-f]|[24][1-9A-Fa-f]" +
      "|[7-9Aa][\\dA-Fa-f]|[Bb][\\dA-Ea-e]|[Cc][2-7A-Fa-f]|[Dd][2-7A-Ca-c]" +
      "))))*|0[Xx][\\dA-Fa-f]+|(?:(?:0[0-7]*(?![89])|[1-9]\\d*)(?:\\.\\d*)" +
      "?|\\.\\d+)(?:[Ee][+-]?\\d+)?|[(),.:;?[\\]{}]|&&|\\|\\||\\+\\+|--|(?" +
      ":[%&*+^|~-]|>{1,3}|<<?|[!=]=?)=?|\"(?:[^\\n\\r\"\\\\\\u2028\\u2029]" +
      "|\\\\(?:\\r\\n?|[^\\r89ux]|u[\\dA-Fa-f]{4}|x[\\dA-Fa-f]{2}))*\"|'(?" +
      ":[^\\n\\r'\\\\\\u2028\\u2029]|\\\\(?:\\r\\n?|[^\\r89ux]|u[\\dA-Fa-f" +
      "]{4}|x[\\dA-Fa-f]{2}))*'|\\/\\*[\\S\\s]*?\\*\\/|\\/\\/.*|[\\t\\x0b" +
      "\\f \\xa0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000\\ufeff]" +
      "+|\\r\\n?|[\\n\\u2028\\u2029])/,\n" +
      "g=/^(?:[\\s\\xa0\\u1680\\u180e\\u2000-\\u200a\\u2028\\u2029\\u202f" +
      "\\u205f\\u3000\\ufeff]|\\/[*/])/;var h=/^(?:break|case|continue|del" +
      "ete|do|else|finally|in|instanceof|return|throw|try|typeof|void|[*-/" +
      "])$|[!%&(:-?[^{-~]$/;var i=/^\\/(?![*/])(?:[^\\n\\r/[\\\\\\u2028" +
      "\\u2029]|\\[(?:[^\\n\\r\\\\\\]\\u2028\\u2029]|\\\\(?:[^\\n\\rux\\u2" +
      "028\\u2029]|u[\\dA-Fa-f]{4}|x[\\dA-Fa-f]{2}))+]|\\\\(?:[^\\n\\rux\\" +
      "u2028\\u2029]|u[\\dA-Fa-f]{4}|x[\\dA-Fa-f]{2}))*\\/[gim]*/,j=/^\\/=" +
      "?/,k=/[\\t\\x0b\\f \\xa0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f" +
      "\\u3000\\ufeff]/,l=/[!%&(--:-?[\\]^{-~]/;var m={'\"':'\\\\\"',\"" +
      "\\\\\":\"\\\\\\\\\",\"\\n\":\"\\\\n\",\"\\r\":\"\\\\r\",\"\\u2028\"" +
      ":\"\\\\u2028\",\"\\u2029\":\"\\\\u2029\"};function n(b){return m[b]" +
      "}function o(b){return String.fromCharCode(b.replace(\"\\\\u\",\"0x" +
      "\"))};global.es5Lexer={makeScanner:function(b){var a=b,c;return fun" +
      "ction(){var b,e;return a?(b=a.match(f)||a.match(!c||h.test(c)?i:j)," +
      "e=b[0],g.test(e)||(c=e),a=a.substring(e.length),e):null}},classifyT" +
      "oken:function(b){var a=b[0];return a==='\"'||a===\"'\"?3:a===\"/\"?" +
      "(a=b[1],a===\"/\"||a===\"*\"?0:b.length>2?5:6):a===\".\"?b.length==" +
      "=1?6:4:\"0\"<=a&&a<=\"9\"?4:k.test(a)?1:l.test(b)?6:a<\" \"||a===\"" +
      "\\u2028\"||a===\"\\u2029\"?2:7},isLineTerminator:function(b){return" +
      "/(?:^|\\/\\*.*)[\\n\\r\\u2028\\u2029]/.test(b)},disambiguateTokenSt" +
      "ream:function(b){var a=\n" +
      "[];return function(){var c,d,e;return a[0]?a.shift():!(c=b())?b=nul" +
      "l:(d=c[0])===\"/\"&&(e=c[1])!==\"/\"&&e!==\"*\"?(d=c.length)>2?(d=c" +
      ".lastIndexOf(\"/\"),a.push(\".\",\"constructor\",\"(\"),c.indexOf(" +
      "\"/\",1)<d?a.push('\"'+c.substring(1,d).replace(/[\\n\\r\"\\\\" +
      "\\u2028\\u2029]/g,n)+'\"',\",\",'\"'+c.substring(d+1)+'\"',\")\"):" +
      "a.push(c,\")\"),\"/./\"):d==1?(a.push(\"1\",\"/\"),\"*\"):(a.push(" +
      "\"\\n\"),c):\"'\"!==d&&'\"'!==d?c.replace(/\\\\u00[\\da-f]{2}/gi,o" +
      "):c}},");
  private static final char[] DENSE_SNIPPET_CHARS = DENSE_SNIPPET.toCharArray();

  private static final String SPARSE_SNIPPET = (
      "no such server found.\n" +
      "Der Server wurde nicht gefunden.\n" +
      "no se ha encontrado el servidor.\n" +
      "ce serveur est introuvable.\n" +
      "impossibile trovare il server indicato.\n" +
      "\u6307\u5b9a\u3055\u308c\u305f\u30b5\u30fc\u30d0\u304c\u898b\u3064" +
      "\u304b\u308a\u307e\u305b\u3093\u3002\n" +
      "\ud574\ub2f9 \uc11c\ubc84\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.\n" +
      "n\u00e5gon s\u00e5dan server kan inte hittas.\n" +
      "\u627e\u4e0d\u5230\u8fd9\u79cd\u670d\u52a1\u5668\u3002\n" +
      "\u627e\u4e0d\u5230\u9019\u500b\u4f3a\u670d\u5668\u3002\n" +
      "no such server found.\n" +
      "Der Server wurde nicht gefunden.\n" +
      "no se ha encontrado el servidor.\n" +
      "ce serveur est introuvable.\n" +
      "impossibile trovare il server indicato.\n" +
      "\u6307\u5b9a\u3055\u308c\u305f\u30b5\u30fc\u30d0\u304c\u898b\u3064" +
      "\u304b\u308a\u307e\u305b\u3093\u3002\n" +
      "\ud574\ub2f9 \uc11c\ubc84\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.\n" +
      "n\u00e5gon s\u00e5dan server kan inte hittas.\n" +
      "\u627e\u4e0d\u5230\u8fd9\u79cd\u670d\u52a1\u5668\u3002\n" +
      "\u627e\u4e0d\u5230\u9019\u500b\u4f3a\u670d\u5668\u3002\n" +
      "no such server found.\n" +
      "Der Server wurde nicht gefunden.\n" +
      "no se ha encontrado el servidor.\n" +
      "ce serveur est introuvable.\n" +
      "impossibile trovare il server indicato.\n" +
      "\u6307\u5b9a\u3055\u308c\u305f\u30b5\u30fc\u30d0\u304c\u898b\u3064" +
      "\u304b\u308a\u307e\u305b\u3093\u3002\n" +
      "\ud574\ub2f9 \uc11c\ubc84\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.\n" +
      "n\u00e5gon s\u00e5dan server kan inte hittas.\n" +
      "\u627e\u4e0d\u5230\u8fd9\u79cd\u670d\u52a1\u5668\u3002\n" +
      "\u627e\u4e0d\u5230\u9019\u500b\u4f3a\u670d\u5668\u3002"
      );

  private static final char[] SPARSE_SNIPPET_CHARS
      = SPARSE_SNIPPET.toCharArray();

  private static final String SPARSE_ASCII_SNIPPET = (
      "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam" +
      " nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam" +
      " erat, sed diam voluptua. At vero eos et accusam et justo duo dolores" +
      " et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est" +
      " Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur" +
      " sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore" +
      " et dolore magna aliquyam erat, sed diam voluptua. At vero eos et" +
      " accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren," +
      " no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum" +
      " dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod" +
      " tempor invidunt ut labore et dolore magna aliquyam erat, sed diam" +
      " voluptua. At vero eos et accusam et justo duo dolores et ea rebum." +
      " Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum" +
      " dolor sit amet.\n" +
      "\n" +
      "Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse" +
      " molestie consequat, vel illum dolore eu feugiat nulla facilisis at" +
      " vero eros et accumsan et iusto odio dignissim qui blandit praesent" +
      " luptatum zzril delenit augue duis dolore te feugait nulla facilisi." +
      " Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam" +
      " nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat" +
      " volutpat.\n" +
      "\n" +
      "Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper" +
      " suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis" +
      " autem vel eum iriure dolor in hendrerit in vulputate velit esse" +
      " molestie consequat, vel illum dolore eu feugiat nulla facilisis at" +
      " vero eros et accumsan et iusto odio dignissim qui blandit praesent" +
      " luptatum zzril delenit augue duis dolore te feugait nulla facilisi.");

  private static final char[] SPARSE_ASCII_SNIPPET_CHARS
      = SPARSE_ASCII_SNIPPET.toCharArray();

  public final void testDenseSnippet() throws Exception {
    doTest(DENSE_SNIPPET, "dense");
  }

  public final void testSparseSnippet() throws Exception {
    doTest(SPARSE_SNIPPET, "sparse");
  }

  public final void testSparseAsciiSnippet() throws Exception {
    doTest(SPARSE_ASCII_SNIPPET, "sparse ascii");
  }

  public final void testDenseSnippetChars() throws Exception {
    doTest(DENSE_SNIPPET_CHARS, "dense");
  }

  public final void testSparseSnippetChars() throws Exception {
    doTest(SPARSE_SNIPPET_CHARS, "sparse");
  }

  public final void testSparseAsciiSnippetChars() throws Exception {
    doTest(SPARSE_ASCII_SNIPPET_CHARS, "sparse ascii");
  }

  private final void doTest(String testString, String name) throws Exception {
    // warmup
    runEscapers(1000, testString);
    // Run the escapers.
    final int N_RUNS = 2000;
    long[] times = runEscapers(N_RUNS, testString);
    Escaper[] escapers = Escaper.values();
    System.out.println(
        "Escapers " + name + " for " + N_RUNS + " runs with "
        + testString.length() + " chars in ns");
    writeTable(escapers, times);
  }

  private final void doTest(char[] testString, String name) throws Exception {
    // warmup
    runEscapers(1000, testString);
    // Run the escapers.
    final int N_RUNS = 2000;
    long[] times = runEscapers(N_RUNS, testString);
    Escaper[] escapers = Escaper.values();
    System.out.println(
        "Escapers " + name + " char[] for " + N_RUNS + " runs with "
        + testString.length + " chars in ns");
    writeTable(escapers, times);
  }

  static long[] runEscapers(int runs, String s) throws Exception {
    int len = s.length();
    Escaper[] escapers = Escaper.values();
    int n = escapers.length;
    long[] times = new long[n];
    // Use DEV_NULL so we're benchmarking the time the encoding takes, not the
    // time spent moving bytes around in buffers.
    HTMLEscapingWriter w = new HTMLEscapingWriter(HTMLEscapingWriter.DEV_NULL);
    try {
      for (int i = 0; i < n; ++i) {
        long t0 = System.nanoTime();
        for (int j = runs; --j >= 0;) {
          w.writeUnsafe(s, 0, len, escapers[i]);
        }
        long t1 = System.nanoTime();
        times[i] = t1 - t0;
      }
    } finally {
      w.close();
    }
    return times;
  }

  static long[] runEscapers(int runs, char[] s) throws Exception {
    int len = s.length;
    Escaper[] escapers = Escaper.values();
    int n = escapers.length;
    long[] times = new long[n];
    // Use DEV_NULL so we're benchmarking the time the encoding takes, not the
    // time spent moving bytes around in buffers.
    HTMLEscapingWriter w = new HTMLEscapingWriter(HTMLEscapingWriter.DEV_NULL);
    try {
      for (int i = 0; i < n; ++i) {
        long t0 = System.nanoTime();
        for (int j = runs; --j >= 0;) {
          w.writeUnsafe(s, 0, len, escapers[i]);
        }
        long t1 = System.nanoTime();
        times[i] = t1 - t0;
      }
    } finally {
      w.close();
    }
    return times;
  }

  private static void writeTable(Object... cols) {
    int nRows = Array.getLength(cols[0]);
    int nCols = cols.length;
    int[] widths = new int[nRows];
    StringBuilder[] sb = new StringBuilder[nRows];
    for (int j = 0; j < nRows; ++j) {
      sb[j] = new StringBuilder();
    }
    for (int i = 0; i < nCols; ++i) {
      Object col = cols[i];
      int maxWidth = 1;
      for (int j = 0; j < nRows; ++j) {
        String s = String.valueOf(Array.get(col, j));
        sb[j].append(s);
        widths[j] = s.length();
        maxWidth = Math.max(maxWidth, s.length());
      }
      if (i+1 < nCols) {
        for (int j = 0; j < nRows; ++j) {
          for (int padding = maxWidth - widths[j] + 1; --padding >= 0;) {
            sb[j].append(' ');
          }
        }
      }
    }
    for (StringBuilder s : sb) {
      System.out.println(s);
    }
  }

  public static void main(String ...args) throws Exception {
    runEscapers(2000, DENSE_SNIPPET_CHARS);
  }
}
