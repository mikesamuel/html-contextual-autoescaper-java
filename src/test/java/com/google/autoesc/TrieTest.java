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

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TrieTest extends TestCase {
  public static final void testTrie() {
    Trie<Integer> t = Trie.<Integer>builder()
        .put("one", 1).put("two", 2).put("three", 3)
        .build();

    assertEquals(
        (
         "null {\n" +
         "  'o':null {\n" +
         "    'n':null {\n" +
         "      'e':1 {}}}\n" +
         "  't':null {\n" +
         "    'h':null {\n" +
         "      'r':null {\n" +
         "        'e':null {\n" +
         "          'e':3 {}}}}\n" +
         "    'w':null {\n" +
         "      'o':2 {}}}}"),
        t.toString());
    assertEquals(null, t.get("one", 0, 0));
    assertEquals(null, t.get("one", 0, 1));
    assertEquals(null, t.get("one", 0, 2));
    assertEquals(null, t.get("one", 1, 3));
    assertEquals(Integer.valueOf(1), t.get("one", 0, 3));
    assertEquals(Integer.valueOf(1), t.get("none", 1, 4));
    assertEquals(Integer.valueOf(1), t.get("oney", 0, 3));
    assertEquals(null, t.get("NONE", 1, 4));
    assertEquals(Integer.valueOf(1), t.getIgnoreCase("one", 0, 3));
    assertEquals(Integer.valueOf(1), t.getIgnoreCase("ONE", 0, 3));
    assertEquals(Integer.valueOf(1), t.getIgnoreCase("NONE", 1, 4));
    assertEquals(Integer.valueOf(2), t.get("two", 0, 3));
    assertEquals(Integer.valueOf(3), t.get("three", 0, 5));
    assertEquals(Integer.valueOf(3), t.get("three ", 0, 5));
    assertEquals(null, t.get("three ", 0, 6));
  }
}
