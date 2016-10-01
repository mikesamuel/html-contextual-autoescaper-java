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

import java.lang.reflect.Array;
import java.util.regex.Pattern;

/**
 * Utilities for presenting benchmark and test output.
 */
class TestUtil {

  static final Pattern NUMBER = Pattern.compile(
      "^[+-]?\\d+(?:,\\d+)*(?:[.](?:\\d+(?:,\\d+)*)?)?(?:[eE][+-]?\\d+)?$");

  static void writeTable(Object... cols) {
    int nRows = Array.getLength(cols[0]);
    int nCols = cols.length;
    int[] widths = new int[nRows];
    StringBuilder[] sb = new StringBuilder[nRows];
    for (int j = 0; j < nRows; ++j) {
      sb[j] = new StringBuilder();
    }
    String[] colCoerced = new String[nRows];
    for (int i = 0; i < nCols; ++i) {
      Object col = cols[i];
      int maxWidth = 1;
      for (int j = 0; j < nRows; ++j) {
        String s = String.valueOf(Array.get(col, j));
        s = addCommasToDecimal(s);
        colCoerced[j] = s;
        widths[j] = s.length();
        maxWidth = Math.max(maxWidth, s.length());
      }
      boolean padAtEnd = i+1 < nCols;
      for (int j = 0; j < nRows; ++j) {
        int padding = maxWidth - widths[j];
        String s = colCoerced[j];
        boolean isNumber = NUMBER.matcher(s).matches();
        if (isNumber) {
          while (--padding >= 0) { sb[j].append(' '); }
        }
        sb[j].append(s);
        if (padAtEnd) {
          while (--padding >= 0) { sb[j].append(' '); }
          sb[j].append(" | ");
        }
      }
    }
    for (StringBuilder s : sb) {
      System.out.println(s);
    }
  }

  static String addCommasToDecimal(String s) {
    int n = s.length(), start = 0, end;
    if (n == 0) { return s; }
    if (s.charAt(0) == '+' || s.charAt(0) == '-') {
      ++start;
    }
    end = start;
    while (end < n) {
      char ch = s.charAt(end);
      if ('0' <= ch && ch <= '9') {
        ++end;
      } else {
        break;
      }
    }
    if (end - start <= 3) { return s; }  // No commas to insert.
    if (end < n && '.' == s.charAt(end)) {
      for (int i = end+1; i < n; ++i) {
        char ch = s.charAt(i);
        if (!('0' <= ch && ch <= '9')) { return s; }
      }
    }
    int numDigitsInFirstGroup = (end - start) % 3;
    if (numDigitsInFirstGroup == 0) { numDigitsInFirstGroup = 3; }
    StringBuilder sb = new StringBuilder(n + 16);
    sb.append(s, 0, start + numDigitsInFirstGroup);
    for (int i = start + numDigitsInFirstGroup; i < end; i += 3) {
      sb.append(',').append(s, i, i+3);
    }
    return sb.append(s, end, n).toString();
  }
}
