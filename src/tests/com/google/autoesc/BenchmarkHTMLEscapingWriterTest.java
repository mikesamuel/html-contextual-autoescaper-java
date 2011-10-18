package com.google.autoesc;

import java.io.StringWriter;

import junit.framework.TestCase;

public class BenchmarkHTMLEscapingWriterTest extends TestCase {
  public final void testHTMLEscapeSpeed() throws Exception {
    long t0 = System.nanoTime();
    for (int runs = 100; --runs >= 0;) {
      StringWriter sw = new StringWriter();
      HTMLEscapingWriter w = new HTMLEscapingWriter(sw);

      w.writeSafe("<html><head><title>Benchmark</title></head><body><ul>");
      for (int i = 0; i < 10000; ++i) {
        w.writeSafe("<li onclick=\"picked(");
        w.write(i);
        w.writeSafe("\">Lorem Ipsum</li>");
      }
      w.write("</ul>Lorem Ipsum, some boilerplate &copy; blah</body></html>");
    }
    long t1 = System.nanoTime();
    System.err.println(t1 - t0 + " ns");
  }
}
