#!python

# This generates a java source file by taking each method that has a
# parameters (String s, int off, int end) and generating a copy that
# takes (char[] s, int off, int end).

# Fix emacs syntax highlighting "

src = r"""
// Copyright (C) 2012 Google Inc.
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

/**
 * Classifies Markup languages based on XML content.
 */
final class Doctype {

  static int classify(String s, int off, int end) {
    space_loop:
    while (off < end) {
      switch (s.charAt(off)) {
      case '\t': case '\n': case '\r': case ' ':
        ++off;
        break;
      default: break space_loop;
      }
    }

    Trie<Integer> trie = TOP_LEVEL_TAG_NAME;
    for (; off < end; ++off) {
      char ch = s.charAt(off);
      if (('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z')) {
        trie = trie.getIgnoreCase(ch);
        if (trie == null) { return Context.State.XML; }
      } else {
        break;
      }
    }

    Integer state = trie.value;
    if (state == null) { return Context.State.XML; }
    return state;
  }

  private static final Trie<Integer> TOP_LEVEL_TAG_NAME
    = Trie.<Integer>builder()
      .put("html", Context.State.Text)
      // SVG and MathML are subsets of HTML5 now so use HTML mode.
      .put("svg", Context.State.Text)
      .put("math", Context.State.Text)
      .build();

}
"""  # Fix emacs syntax highlighting "

import dupe_methods
print dupe_methods.dupe(src)
