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
    run(w1);

    StringWriter sw2 = new StringWriter();
    MemoizingHTMLEscapingWriter w2 = new MemoizingHTMLEscapingWriter(sw2);
    run(w2);

    // First time through, are the two the same?
    assertEquals("first pass", sw1.toString(), sw2.toString());

    run(w1);
    run(w2);

    assertEquals("second pass", sw1.toString(), sw2.toString());
  }

  public final void testHTMLEscapeSpeed() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);
      run(w);
    }
    long t1 = System.nanoTime();
    System.err.println(
        "\nnormal:   " + (t1 - t0) + " ns for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
  }

  public final void testHTMLEscapeSpeedMemoizing() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = N_RUNS; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      MemoizingHTMLEscapingWriter w = new MemoizingHTMLEscapingWriter(sw);
      run(w);
    }
    long t1 = System.nanoTime();
    System.err.println(
        "\nmemoized: " + (t1 - t0) + " ns for " + N_RUNS + " runs of "
        + N_ROWS + " rows each");
  }

  private void run(HTMLEscapingWriter w) throws Exception {
    w.writeSafe("<html><head><title>Benchmark</title></head><body><ul>");
    for (int i = 0; i < N_ROWS; ++i) {
      w.writeSafe("<li onclick=\"picked(");
      w.write(i);
      w.writeSafe("\">Lorem Ipsum</li>");
    }
    w.write("</ul>Lorem Ipsum, some boilerplate &copy; blah</body></html>");
  }
}
