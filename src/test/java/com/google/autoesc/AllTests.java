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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.runner.RunWith;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

@RunWith(org.junit.runners.AllTests.class)
public class AllTests {

  public static Test suite() {
    List<String> allTests = new ArrayList<String>();

    {
      InputStream in = AllTests.class.getResourceAsStream("alltests");
      if (in == null) {
        throw new AssertionError("Failed to load list of tests");
      }
      try {
        BufferedReader r = new BufferedReader(
            new InputStreamReader(in, "UTF-8"));
        try {
          for (String line; (line = r.readLine()) != null;) {
            allTests.add(line);
          }
        } finally {
          r.close();
        }
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    // If test.nodeps is specified, run only the tests that do not depend on
    // external JARs.
    boolean skipDependencies = Boolean.valueOf(
        System.getProperty("test.nodeps", "false"));

    TestSuite suite = new TestSuite();
    ClassLoader loader = AllTests.class.getClass().getClassLoader();
    if (loader == null) { loader = ClassLoader.getSystemClassLoader(); }
    for (String test : allTests) {
      if (skipDependencies && REQUIRE_DEPENDENCIES.contains(test)) {
        continue;
      }
      try {
        suite.addTestSuite(loader.loadClass(test).asSubclass(TestCase.class));
      } catch (ClassNotFoundException ex) {
        throw new RuntimeException(ex);
      }
    }
    return suite;
  }

  private static final Set<String> REQUIRE_DEPENDENCIES = new HashSet<String>(
      Arrays.asList(
          // Depends on JSDK.
          "com.google.autoesc.AppEngineTestbedTest",
          // Don't run slow tests twice.
          "com.google.autoesc.BenchmarkHTMLEscapingWriterTest",
          "com.google.autoesc.BenchmarkEscapersTest"));

}
