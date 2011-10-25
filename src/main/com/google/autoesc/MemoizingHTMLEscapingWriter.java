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
import java.util.concurrent.ExecutionException;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

/**
 * An {@link HTMLEscapingWriter} that is more efficient at handling repeated
 * context transitions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class MemoizingHTMLEscapingWriter extends HTMLEscapingWriter {
  private static final Cache<MemoKey, MemoValue> MEMO_TABLE
      = CacheBuilder.newBuilder()
      .concurrencyLevel(2)
      .maximumSize(1000)
      .build(new CacheLoader<MemoKey, MemoValue>() {
        @Override
        public MemoValue load(MemoKey key)
            throws IOException, TemplateException {
          StringWriter normalizedSafeContent = new StringWriter(
              key.safeContent.length() + 16);
          HTMLEscapingWriter w = new HTMLEscapingWriter(normalizedSafeContent);
          w.setContext(key.startContext);
          w.writeSafe(key.safeContent);
          return new MemoValue(
              w.getContext(), normalizedSafeContent.toString());
        }
      });

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
    try {
      MemoValue value = MEMO_TABLE.get(key);
      getWriter().write(value.normalizedSafeContent);
      setContext(value.endContext);
    } catch (ExecutionException ex) {
      Throwables.propagateIfPossible(
          ex, IOException.class, TemplateException.class);
      Throwables.propagate(ex);
    }
  }
}
