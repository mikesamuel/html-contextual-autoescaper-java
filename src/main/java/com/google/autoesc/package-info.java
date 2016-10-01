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
 * <p>
 * This package provides a writer with an extra 
 * {@link com.google.autoesc.HTMLEscapingWriter#writeSafe writeSafe} method
 * and a version of
 * {@link com.google.autoesc.HTMLEscapingWriter#write(Object) write} that takes
 * an arbitrary {@code Object}.
 * <p>
 * A template like
 * <pre>
 * &lt;div style="color: <b>&lt;%=$self.color%&gt;</b>"&gt;
 *   &lt;a href="/<b>&lt;%=$self.color%&gt;</b>?q=<b>&lt;%=$self.world%&gt;</b>"
 *    onclick="alert('<b>&lt;% helper($self) %&gt;</b>');return false"&gt;
 *     <b>&lt;% helper($self) %&gt;</b>
 *   &lt;/a&gt;
 *   &lt;script&gt;(function () {  // Sleepy developers put sensitive info in comments.
 *     var o = <b>&lt;%=$self&gt;</b>,
 *         w = "<b>&lt;%=$self.world%&gt;</b>";
 *   })();&lt;/script&gt;
 * &lt;/div&gt;
 *
 * <b>&lt;% def helper($self) {
 *   %&gt;</b>Hello, <b>&lt;%=$self.world%&gt;</b>
 * <b>&lt;%}%&gt;</b>
 * </pre>
 * might correspond to the sequence of calls
 * <pre>
 *  // Dummy input values.
 *  Map $self = ImmutableMap.&lt;String, Object&gt;of(
 *      "world", "&lt;Cincinatti&gt;", "color", "blue");
 *  Object color = self.get("color"), world = self.get("world");
 *  // Alternating safe and unsafe writes that implement the template.
 *  w.writeSafe(<u>"&lt;div style=\"color: "</u>);
 *  w.write    (<b>color</b>);
 *  w.writeSafe(<u>"\"&gt;\n&lt;a href=\"/"</u>);
 *  w.write    (<b>color</b>);
 *  w.writeSafe(<u>"?q="</u>);
 *  w.write    (<b>world</b>);
 *  w.writeSafe(<u>"\"\n  onclick=\"alert('"</u>);
 *  helper     (<b>w, $self</b>);
 *  w.writeSafe(<u>"');return false\"&gt;\n    "</u>);
 *  helper     (<b>w, $self</b>);
 *  w.writeSafe(<u>"\n  &lt;/a&gt;\n  &lt;script&gt;(function () {\n    var o = "</u>);
 *  w.write    (<b>$self</b>);
 *  w.writeSafe(<u>",\n        w = \""</u>);
 *  w.write    (<b>world</b>);
 *  w.writeSafe(<u>"\";\n  })();&lt;/script&gt;\n&lt;/div&gt;"</u>);
 * </pre>
 * which result in the output
 * <pre>
 * &lt;div style="color: <b>blue</b>"&gt;
 *   &lt;a href="/<b>blue</b>?q=<b>%3cCincinatti%3e</b>"
 *    onclick="alert('Hello, <b>\x3cCincinatti\x3e</b>!');return false"&gt;
 *     Hello, <b>&lt;Cincinatti&gt;</b>!
 *   &lt;/a&gt;
 *   &lt;script&gt;(function () {  
 *     var o = <b>{"Color":"blue","World":"\u003cCincinatti\u003e"}</b>,
 *         w = "<b>\x26lt;Cincinatti\x26gt;</b>";
 *   })();&lt;/script&gt;
 * &lt;/div&gt;
 * </pre>
 * The safe parts are treated as literal chunks of HTML/CSS/JS, and the unsafe
 * parts are escaped to preserve security and least-surprise.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.google.autoesc;
