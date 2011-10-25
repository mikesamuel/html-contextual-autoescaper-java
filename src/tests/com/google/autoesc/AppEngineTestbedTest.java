package com.google.autoesc;

import java.io.IOException;
import java.io.StringWriter;

import junit.framework.TestCase;

public class AppEngineTestbedTest extends TestCase {
  public final void testTestbed() throws IOException {
    StringWriter resp = new StringWriter();
    AppEngineTestbed.handle("foo{{.}}bar", "\"baz\"", resp);
    String html = resp.toString();
    assertTrue(html, html.contains("foobazbar"));
  }

  public final void testTestbedComplexInput() throws IOException {
    StringWriter resp = new StringWriter();
    AppEngineTestbed.handle("<script>({{.}})</script>", "{\"baz\":42}", resp);
    String html = resp.toString();
    assertTrue(
        html,
        html.contains("&lt;script&gt;({&#39;baz&#39;:42.0})&lt;/script&gt;"));
  }
}
