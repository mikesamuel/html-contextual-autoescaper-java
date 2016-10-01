#!python

# This generates a java source file by taking each method that has a
# parameters (String s, int off, int end) and generating a copy that
# takes (char[] s, int off, int end).

src = r"""
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

/**
 * Utilities for dealing with HTML attribute contexts.
 */
class Attr {
  /**
   * attrTypeMap[n] describes the value of the given attribute.
   * If an attribute affects (or can mask) the encoding or interpretation of
   * other content, or affects the contents, idempotency, or credentials of a
   * network message, then the value in this map is contentTypeUnsafe.
   * This map is derived from HTML5, specifically
   * http://www.w3.org/TR/html5/Overview.html#attributes-1
   * as well as "%URI"-typed attributes from
   * http://www.w3.org/TR/html4/index/attributes.html
   */
  private static final Trie<ContentType> ATTR_TYPE_MAP;
  static {
    Trie.Builder<ContentType> b = Trie.builder();
    b.put("accept",          ContentType.Plain);
    b.put("accept-charset",  ContentType.Unsafe);
    b.put("action",          ContentType.URL);
    b.put("alt",             ContentType.Plain);
    b.put("archive",         ContentType.URL);
    b.put("async",           ContentType.Unsafe);
    b.put("attributename",   ContentType.Unsafe); // From <svg:set attributeName>
    b.put("autocomplete",    ContentType.Plain);
    b.put("autofocus",       ContentType.Plain);
    b.put("autoplay",        ContentType.Plain);
    b.put("background",      ContentType.URL);
    b.put("border",          ContentType.Plain);
    b.put("checked",         ContentType.Plain);
    b.put("cite",            ContentType.URL);
    b.put("challenge",       ContentType.Unsafe);
    b.put("charset",         ContentType.Unsafe);
    b.put("class",           ContentType.Plain);
    b.put("classid",         ContentType.URL);
    b.put("codebase",        ContentType.URL);
    b.put("cols",            ContentType.Plain);
    b.put("colspan",         ContentType.Plain);
    b.put("content",         ContentType.Unsafe);
    b.put("contenteditable", ContentType.Plain);
    b.put("contextmenu",     ContentType.Plain);
    b.put("controls",        ContentType.Plain);
    b.put("coords",          ContentType.Plain);
    b.put("crossorigin",     ContentType.Unsafe);
    b.put("data",            ContentType.URL);
    b.put("datetime",        ContentType.Plain);
    b.put("default",         ContentType.Plain);
    b.put("defer",           ContentType.Unsafe);
    b.put("dir",             ContentType.Plain);
    b.put("dirname",         ContentType.Plain);
    b.put("disabled",        ContentType.Plain);
    b.put("draggable",       ContentType.Plain);
    b.put("dropzone",        ContentType.Plain);
    b.put("enctype",         ContentType.Unsafe);
    b.put("for",             ContentType.Plain);
    b.put("form",            ContentType.Unsafe);
    b.put("formaction",      ContentType.URL);
    b.put("formenctype",     ContentType.Unsafe);
    b.put("formmethod",      ContentType.Unsafe);
    b.put("formnovalidate",  ContentType.Unsafe);
    b.put("formtarget",      ContentType.Plain);
    b.put("headers",         ContentType.Plain);
    b.put("height",          ContentType.Plain);
    b.put("hidden",          ContentType.Plain);
    b.put("high",            ContentType.Plain);
    b.put("href",            ContentType.URL);
    b.put("hreflang",        ContentType.Plain);
    b.put("http-equiv",      ContentType.Unsafe);
    b.put("icon",            ContentType.URL);
    b.put("id",              ContentType.Plain);
    b.put("ismap",           ContentType.Plain);
    b.put("keytype",         ContentType.Unsafe);
    b.put("kind",            ContentType.Plain);
    b.put("label",           ContentType.Plain);
    b.put("lang",            ContentType.Plain);
    b.put("language",        ContentType.Unsafe);
    b.put("list",            ContentType.Plain);
    b.put("longdesc",        ContentType.URL);
    b.put("loop",            ContentType.Plain);
    b.put("low",             ContentType.Plain);
    b.put("manifest",        ContentType.URL);
    b.put("max",             ContentType.Plain);
    b.put("maxlength",       ContentType.Plain);
    b.put("media",           ContentType.Plain);
    b.put("mediagroup",      ContentType.Plain);
    b.put("method",          ContentType.Unsafe);
    b.put("min",             ContentType.Plain);
    b.put("multiple",        ContentType.Plain);
    b.put("name",            ContentType.Plain);
    b.put("novalidate",      ContentType.Unsafe);
    // Skip handler names from
    // http://www.w3.org/TR/html5/Overview.html#event-handlers-on-elements-document-objects-and-window-objects
    // since we have special handling in attrType.
    b.put("open",            ContentType.Plain);
    b.put("optimum",         ContentType.Plain);
    b.put("pattern",         ContentType.Unsafe);
    b.put("placeholder",     ContentType.Plain);
    b.put("poster",          ContentType.URL);
    b.put("profile",         ContentType.URL);
    b.put("preload",         ContentType.Plain);
    b.put("pubdate",         ContentType.Plain);
    b.put("radiogroup",      ContentType.Plain);
    b.put("readonly",        ContentType.Plain);
    b.put("rel",             ContentType.Unsafe);
    b.put("required",        ContentType.Plain);
    b.put("reversed",        ContentType.Plain);
    b.put("rows",            ContentType.Plain);
    b.put("rowspan",         ContentType.Plain);
    b.put("sandbox",         ContentType.Unsafe);
    b.put("spellcheck",      ContentType.Plain);
    b.put("scope",           ContentType.Plain);
    b.put("scoped",          ContentType.Plain);
    b.put("seamless",        ContentType.Plain);
    b.put("selected",        ContentType.Plain);
    b.put("shape",           ContentType.Plain);
    b.put("size",            ContentType.Plain);
    b.put("sizes",           ContentType.Plain);
    b.put("span",            ContentType.Plain);
    b.put("src",             ContentType.URL);
    b.put("srcdoc",          ContentType.Markup);
    b.put("srchtml",         ContentType.Unsafe);
    b.put("srclang",         ContentType.Plain);
    b.put("start",           ContentType.Plain);
    b.put("step",            ContentType.Plain);
    b.put("style",           ContentType.CSS);
    b.put("tabindex",        ContentType.Plain);
    b.put("target",          ContentType.Plain);
    b.put("title",           ContentType.Plain);
    b.put("type",            ContentType.Unsafe);
    b.put("usemap",          ContentType.URL);
    b.put("value",           ContentType.Unsafe);
    b.put("width",           ContentType.Plain);
    b.put("wrap",            ContentType.Plain);
    b.put("xmlns",           ContentType.URL);
    ATTR_TYPE_MAP = b.build();
  }

  /**
   * attrType returns a conservative (upper-bound on authority) guess at the
   * type of the named attribute.
   */
  static ContentType attrType(String name) {
    return attrType(name, 0, name.length());
  }

  static ContentType attrType(String s, int off, int end) {
    if (CharsUtil.startsWithIgnoreCase(s, off, end, "data-")) {
      // Strip data- so that custom attribute heuristics below are
      // widely applied.
      // Treat data-action as URL below.
      off += 5;
    } else {
      int colon = CharsUtil.indexOf(s, off, end, ':');
      if (colon >= 0) {
        if (colon == off+5 && CharsUtil.startsWith(s, off, end, "xmlns")) {
          return ContentType.URL;
        }
        // Treat svg:href and xlink:href as href below.
        off = colon + 1;
      }
    }
    ContentType t = ATTR_TYPE_MAP.getIgnoreCase(s, off, end);
    if (t != null) { return t; }
    // Treat partial event handler names as script.
    if (CharsUtil.startsWithIgnoreCase(s, off, end, "on")) {
      return ContentType.JS;
    }

    // Heuristics to prevent "javascript:..." injection in custom
    // data attributes and custom attributes like g:tweetUrl.
    // http://www.w3.org/TR/html5/elements.html#embedding-custom-non-visible-data-with-the-data-attributes:
    // "Custom data attributes are intended to store custom data
    //  private to the page or application, for which there are no
    //  more appropriate attributes or elements."
    // Developers seem to store URL content in data URLs that start
    // or end with "URI" or "URL".
    if (CharsUtil.containsIgnoreCase(s, off, end, "src")
        || CharsUtil.containsIgnoreCase(s, off, end, "uri")
        || CharsUtil.containsIgnoreCase(s, off, end, "url")) {
      return ContentType.URL;
    }
    return ContentType.Unsafe;
  }
}
"""

import dupe_methods
print dupe_methods.dupe(src)
