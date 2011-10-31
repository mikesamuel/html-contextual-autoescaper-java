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

import java.io.Writer;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class BenchmarkHTMLEscapingWriterTest extends TestCase {
  static final int N_RUNS = 100;
  static final int N_ROWS = 10000;

  static final NumberFormat TWO_DEC_PLACES = new DecimalFormat("0.##");

  public final void testEquivalence() throws Exception {
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

  public final void testWriteSafeSpeed() throws Exception {
    // Warm up the JIT.
    timeBaseline();
    timeNormalString();
    timeNormalChars();
    timeMemoized();

    List<Object> bmark = new ArrayList<Object>();
    List<Object> time = new ArrayList<Object>();
    List<Object> ratio = new ArrayList<Object>();
    bmark.add("");
    time.add("Time us");
    ratio.add("t/baseline");

    long bl = timeBaseline();
    long ns = timeNormalString();
    long nc = timeNormalChars();
    long mm = timeMemoized();

    bmark.add("baseline");
    time.add(bl);
    ratio.add(TWO_DEC_PLACES.format(bl / ((double) bl)));
    bmark.add("normal str");
    time.add(ns);
    ratio.add(TWO_DEC_PLACES.format(ns / ((double) bl)));
    bmark.add("normal chars");
    time.add(nc);
    ratio.add(TWO_DEC_PLACES.format(nc / ((double) bl)));
    bmark.add("memoized");
    time.add(mm);
    ratio.add(TWO_DEC_PLACES.format(mm / ((double) bl)));

    System.err.println(
        "\nTesting escape safe in us for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
    TestUtil.writeTable(bmark.toArray(), time.toArray(), ratio.toArray());
  }

  private long timeBaseline() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter w = new StringWriter();
      runBaseline(w);
    }
    long t1 = System.nanoTime();
    return (t1 - t0) / 1000;
  }

  private long timeNormalString() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      runString(w);
    }
    long t1 = System.nanoTime();
    return (t1 - t0) / 1000;
  }

  private long timeNormalChars() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      runChars(w);
    }
    long t1 = System.nanoTime();
    return (t1 - t0) / 1000;
  }

  private long timeMemoized() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      MemoizingHTMLEscapingWriter w = new MemoizingHTMLEscapingWriter(sw);
      runString(w);
    }
    long t1 = System.nanoTime();
    return (t1 - t0) / 1000;
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

  private static void runString(HTMLEscapingWriter w) throws Exception {
    w.writeSafe(HEADER);
    for (int i = 0; i < N_ROWS; ++i) {
      w.writeSafe(ROW_START);
      w.write(Integer.valueOf(i));
      w.writeSafe(ROW_END);
    }
    w.writeSafe(FOOTER);
  }

  private static void runChars(HTMLEscapingWriter w) throws Exception {
    w.writeSafe(HEADER_CHARS, 0, HEADER_CHARS.length);
    for (int i = 0; i < N_ROWS; ++i) {
      w.writeSafe(ROW_START_CHARS, 0, ROW_START_CHARS.length);
      w.write(Integer.valueOf(i));
      w.writeSafe(ROW_END_CHARS, 0, ROW_END_CHARS.length);
    }
    w.writeSafe(FOOTER_CHARS, 0, FOOTER_CHARS.length);
  }

  private static void runBaseline(Writer w) throws Exception {
    w.write(HEADER_CHARS, 0, HEADER_CHARS.length);
    for (int i = 0; i < N_ROWS; ++i) {
      w.write(ROW_START_CHARS, 0, ROW_START_CHARS.length);
      w.write(Integer.valueOf(i));
      w.write(ROW_END_CHARS, 0, ROW_END_CHARS.length);
    }
    w.write(FOOTER_CHARS, 0, FOOTER_CHARS.length);
  }

  /**
   * Main method invoked by make profile
   */
  public static void main(String... argv) throws Exception {
    runString(new HTMLEscapingWriter(new StringWriter(1 << 18)));
    runChars(new HTMLEscapingWriter(new StringWriter(1 << 18)));
  }
}
