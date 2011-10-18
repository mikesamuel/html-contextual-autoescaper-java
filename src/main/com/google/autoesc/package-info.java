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

/**
 * Provides XSS protection to template languages.
 *
 * This provides a writer-like object that provides two methods:
 * <ul>
 *   <li>{@link com.google.autoesc.HTMLEscapingWriter#writeSafe}</li>
 *   <li>{@link com.google.autoesc.HTMLEscapingWriter#write}</li>
 * </ul>
 * so that the sequence of calls
 * <pre>
 *  w.writeSafe("&lt;b&gt;");
 *  w.write("I &lt;3 Ponies!");
 *  w.writeSafe("&lt;/b&gt;\n&lt;button onclick=foo(");
 *  w.writeObject(ImmutableMap.&lt;String, Object&gt;of(
 *      "foo", "bar", "\"baz\"", 42));
 *  w.writeSafe(")&gt;");
 * </pre>
 * results in the output
 * <blockquote>
 * {@code <b>I &lt;3 Ponies!</b>}
 * {@code <button onclick="foo({&#34;foo&#34;:&#34;\x22bar\x22&#34;:42})">}
 * </blockquote>
 * The safe parts are treated as literal chunks of HTML/CSS/JS, and the unsafe
 * parts are escaped to preserve security and least-surprise.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.google.autoesc;
