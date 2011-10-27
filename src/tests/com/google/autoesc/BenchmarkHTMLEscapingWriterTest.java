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

import junit.framework.TestCase;

public class BenchmarkHTMLEscapingWriterTest extends TestCase {
  private static final int N_RUNS = 100;
  private static final int N_ROWS = 10000;

  public final void testMemoizedVersionEquivalent() throws Exception {
    StringWriter sw1 = new StringWriter();
    HTMLEscapingWriter w1 = new HTMLEscapingWriter(sw1);
    runString(w1);

    StringWriter sw2 = new StringWriter();
    MemoizingHTMLEscapingWriter w2 = new MemoizingHTMLEscapingWriter(sw2);
    runString(w2);

    // First time through, are the two the same?
    assertEquals("first pass", sw1.toString(), sw2.toString());

    runString(w1);
    runString(w2);

    assertEquals("second pass", sw1.toString(), sw2.toString());
  }

  public final void testHTMLEscapeSpeedString() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      runString(w);
    }
    long t1 = System.nanoTime();
    System.err.println(
        "\nnormal string:   " + (t1 - t0) + " ns for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
  }

  public final void testHTMLEscapeSpeedChars() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      runChars(w);
    }
    long t1 = System.nanoTime();
    System.err.println(
        "\nnormal chars:    " + (t1 - t0) + " ns for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
  }

  public final void testHTMLEscapeSpeedMemoizing() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      MemoizingHTMLEscapingWriter w = new MemoizingHTMLEscapingWriter(sw);
      runString(w);
    }
    long t1 = System.nanoTime();
    System.err.println(
        "\nmemoized string: " + (t1 - t0) + " ns for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
  }

  private static final String
      HEADER = "<html><head><title>Benchmark</title></head><body><ul>",
      ROW_START = "<li onclick=picked(",
      ROW_END = ")>Lorem Ipsum</li>",
      FOOTER = "</ul>Lorem Ipsum, some boilerplate &copy; blah</body></html>";

  private static final char[]
      HEADER_CHARS = HEADER.toCharArray(),
      ROW_START_CHARS = ROW_START.toCharArray(),
      ROW_END_CHARS = ROW_END.toCharArray(),
      FOOTER_CHARS = FOOTER.toCharArray();

  private void runString(HTMLEscapingWriter w) throws Exception {
    w.writeSafe(HEADER);
    for (int i = 0; i < N_ROWS; ++i) {
      w.writeSafe(ROW_START);
      w.write(Integer.valueOf(i));
      w.writeSafe(ROW_END);
    }
    w.writeSafe(FOOTER);
  }

  private void runChars(HTMLEscapingWriter w) throws Exception {
    w.writeSafe(HEADER_CHARS, 0, HEADER_CHARS.length);
    for (int i = 0; i < N_ROWS; ++i) {
      w.writeSafe(ROW_START_CHARS, 0, ROW_START_CHARS.length);
      w.write(Integer.valueOf(i));
      w.writeSafe(ROW_END_CHARS, 0, ROW_END_CHARS.length);
    }
    w.writeSafe(FOOTER_CHARS, 0, FOOTER_CHARS.length);
  }

}
