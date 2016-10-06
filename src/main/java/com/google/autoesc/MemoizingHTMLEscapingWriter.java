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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * An {@link HTMLEscapingWriter} that is more efficient at handling repeated
 * context transitions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class MemoizingHTMLEscapingWriter extends HTMLEscapingWriter {
  private static final boolean USE_GLOBAL_CACHE = false;

  // TODO: profile the two cache implementations and decide which one stays.
  private final Map<MemoTuple, MemoTuple> memoTable = USE_GLOBAL_CACHE
      ? null : new HashMap<MemoTuple, MemoTuple>();
  private static final LoadingCache<MemoTuple, MemoTuple> MEMO_TABLE;
  static {
    MEMO_TABLE = USE_GLOBAL_CACHE ?
      CacheBuilder.newBuilder()
      .concurrencyLevel(2)
      .maximumSize(1000)
      .<MemoTuple, MemoTuple>build(new CacheLoader<MemoTuple, MemoTuple>() {
        @Override
        public MemoTuple load(MemoTuple key)
            throws IOException, TemplateException {
          StringWriter normalizedSafeContent = new StringWriter(
              key.safeContent.length() + 16);
          HTMLEscapingWriter w = new HTMLEscapingWriter(normalizedSafeContent);
          w.setContextAndRtable(key.context, key.rtable);
          w.writeSafe(key.safeContent);
          return new MemoTuple(
              w.getContext(), normalizedSafeContent.toString(), w.getRtable());
        }
      })
      : null;
  }

  /**
   * @param out receives autoescaped HTML.
   */
  public MemoizingHTMLEscapingWriter(Writer out) {
    super(out);
  }

  private static final class MemoTuple {
    final int context;
    final String safeContent;
    final ReplacementTable rtable;

    MemoTuple(int context, String safeContent, ReplacementTable rtable) {
      this.context = context;
      this.safeContent = safeContent;
      this.rtable = rtable;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof MemoTuple)) { return false; }
      MemoTuple that = (MemoTuple) o;
      return context == that.context
          && this.rtable == that.rtable
          && safeContent.equals(that.safeContent);
    }

    @Override public int hashCode() {
      return context
          ^ (31 * (this.safeContent.hashCode()
              ^ (rtable != null ? 31 * rtable.hashCode() : 0)));
    }
  }

  @Override
  public void writeSafe(String safeContent)
      throws IOException, TemplateException {
    MemoTuple key = new MemoTuple(getContext(), safeContent, getRtable());
    if (USE_GLOBAL_CACHE) {
      try {
        MemoTuple value = MEMO_TABLE.get(key);
        getWriter().write(value.safeContent);
        setContextAndRtable(value.context, value.rtable);
      } catch (ExecutionException ex) {
        Throwables.propagateIfPossible(
             ex, IOException.class, TemplateException.class);
        Throwables.propagate(ex);
      }
    } else {
      MemoTuple value = memoTable.get(key);
      if (value == null) {
        StringWriter normalizedSafeContent = new StringWriter(
            safeContent.length() + 16);
        @SuppressWarnings("resource")  // Not allocated here
        Writer oout = getWriter();
        replaceWriter(normalizedSafeContent);
        super.writeSafe(safeContent);
        replaceWriter(oout);
        value = new MemoTuple(
            getContext(), normalizedSafeContent.toString(), getRtable());
        memoTable.put(key, value);
      }
      getWriter().write(value.safeContent);
      setContextAndRtable(value.context, value.rtable);
    }
  }
}
