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
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 * An {@link HTMLEscapingWriter} that is more efficient at handling repeated
 * context transitions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class MemoizingHTMLEscapingWriter extends HTMLEscapingWriter {
  // TODO: limit cache size
  private final Map<MemoKey, MemoValue> memoTable = Maps.newHashMap();

  MemoizingHTMLEscapingWriter(Writer out) {
    super(out);
  }

  private static final class MemoKey {
    final int startContext;
    final String safeContent;
    final int hashCode;

    MemoKey(int startContext, String safeContent) {
      this.startContext = startContext;
      this.safeContent = safeContent;
      this.hashCode = safeContent.hashCode() ^ (31 * startContext);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof MemoKey)) { return false; }
      MemoKey that = (MemoKey) o;
      return startContext == that.startContext
          && safeContent.equals(that.safeContent);
    }

    @Override public int hashCode() { return hashCode; }
  }

  private static final class MemoValue {
    final int endContext;
    final String normalizedSafeContent;

    MemoValue(int endContext, String normalizedSafeContent) {
      this.endContext = endContext;
      this.normalizedSafeContent = normalizedSafeContent;
    }
  }

  @Override
  public void writeSafe(String safeContent)
      throws IOException, TemplateException {
    MemoKey key = new MemoKey(getContext(), safeContent);
    MemoValue value = memoTable.get(key);
    if (value == null) {
      StringWriter normalizedSafeContent = new StringWriter(
          safeContent.length() + 16);
      Writer oout = getWriter();
      replaceWriter(normalizedSafeContent);
      super.writeSafe(safeContent);
      replaceWriter(oout);
      value = new MemoValue(getContext(), normalizedSafeContent.toString());
      memoTable.put(key, value);
    }
    getWriter().write(value.normalizedSafeContent);
    setContext(value.endContext);
  }
}
