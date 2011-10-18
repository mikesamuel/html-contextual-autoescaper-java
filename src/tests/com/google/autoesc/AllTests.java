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
