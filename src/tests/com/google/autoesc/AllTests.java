package com.google.autoesc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.runner.RunWith;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

@RunWith(org.junit.runners.AllTests.class)
public class AllTests {

  public static Test suite() {
    String[] allTests;
    InputStream in = AllTests.class.getResourceAsStream("alltests");
    if (in == null) {
      throw new AssertionError("Failed to load list of tests");
    }
    try {
      try {
        allTests = CharStreams.toString(
            new InputStreamReader(in, Charsets.UTF_8)).split("\r\n?|\n");
      } finally {
        in.close();
      }
    } catch (IOException ex) {
      Throwables.propagate(ex);
      return null;
    }

    TestSuite suite = new TestSuite();
    ClassLoader loader = AllTests.class.getClass().getClassLoader();
    if (loader == null) { loader = ClassLoader.getSystemClassLoader(); }
    for (String test : allTests) {
      try {
        suite.addTestSuite(loader.loadClass(test).asSubclass(TestCase.class));
      } catch (ClassNotFoundException ex) {
        Throwables.propagate(ex);
      }
    }
    return suite;
  }

}
