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

import java.util.Locale;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

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
  private static final Map<String, ContentType> ATTR_TYPE_MAP
    = ImmutableMap.<String, ContentType>builder()
    .put("accept",          ContentType.Plain)
    .put("accept-charset",  ContentType.Unsafe)
    .put("action",          ContentType.URL)
    .put("alt",             ContentType.Plain)
    .put("archive",         ContentType.URL)
    .put("async",           ContentType.Unsafe)
    .put("autocomplete",    ContentType.Plain)
    .put("autofocus",       ContentType.Plain)
    .put("autoplay",        ContentType.Plain)
    .put("background",      ContentType.URL)
    .put("border",          ContentType.Plain)
    .put("checked",         ContentType.Plain)
    .put("cite",            ContentType.URL)
    .put("challenge",       ContentType.Unsafe)
    .put("charset",         ContentType.Unsafe)
    .put("class",           ContentType.Plain)
    .put("classid",         ContentType.URL)
    .put("codebase",        ContentType.URL)
    .put("cols",            ContentType.Plain)
    .put("colspan",         ContentType.Plain)
    .put("content",         ContentType.Unsafe)
    .put("contenteditable", ContentType.Plain)
    .put("contextmenu",     ContentType.Plain)
    .put("controls",        ContentType.Plain)
    .put("coords",          ContentType.Plain)
    .put("crossorigin",     ContentType.Unsafe)
    .put("data",            ContentType.URL)
    .put("datetime",        ContentType.Plain)
    .put("default",         ContentType.Plain)
    .put("defer",           ContentType.Unsafe)
    .put("dir",             ContentType.Plain)
    .put("dirname",         ContentType.Plain)
    .put("disabled",        ContentType.Plain)
    .put("draggable",       ContentType.Plain)
    .put("dropzone",        ContentType.Plain)
    .put("enctype",         ContentType.Unsafe)
    .put("for",             ContentType.Plain)
    .put("form",            ContentType.Unsafe)
    .put("formaction",      ContentType.URL)
    .put("formenctype",     ContentType.Unsafe)
    .put("formmethod",      ContentType.Unsafe)
    .put("formnovalidate",  ContentType.Unsafe)
    .put("formtarget",      ContentType.Plain)
    .put("headers",         ContentType.Plain)
    .put("height",          ContentType.Plain)
    .put("hidden",          ContentType.Plain)
    .put("high",            ContentType.Plain)
    .put("href",            ContentType.URL)
    .put("hreflang",        ContentType.Plain)
    .put("http-equiv",      ContentType.Unsafe)
    .put("icon",            ContentType.URL)
    .put("id",              ContentType.Plain)
    .put("ismap",           ContentType.Plain)
    .put("keytype",         ContentType.Unsafe)
    .put("kind",            ContentType.Plain)
    .put("label",           ContentType.Plain)
    .put("lang",            ContentType.Plain)
    .put("language",        ContentType.Unsafe)
    .put("list",            ContentType.Plain)
    .put("longdesc",        ContentType.URL)
    .put("loop",            ContentType.Plain)
    .put("low",             ContentType.Plain)
    .put("manifest",        ContentType.URL)
    .put("max",             ContentType.Plain)
    .put("maxlength",       ContentType.Plain)
    .put("media",           ContentType.Plain)
    .put("mediagroup",      ContentType.Plain)
    .put("method",          ContentType.Unsafe)
    .put("min",             ContentType.Plain)
    .put("multiple",        ContentType.Plain)
    .put("name",            ContentType.Plain)
    .put("novalidate",      ContentType.Unsafe)
    // Skip handler names from
    // http://www.w3.org/TR/html5/Overview.html#event-handlers-on-elements-document-objects-and-window-objects
    // since we have special handling in attrType.
    .put("open",            ContentType.Plain)
    .put("optimum",         ContentType.Plain)
    .put("pattern",         ContentType.Unsafe)
    .put("placeholder",     ContentType.Plain)
    .put("poster",          ContentType.URL)
    .put("profile",         ContentType.URL)
    .put("preload",         ContentType.Plain)
    .put("pubdate",         ContentType.Plain)
    .put("radiogroup",      ContentType.Plain)
    .put("readonly",        ContentType.Plain)
    .put("rel",             ContentType.Unsafe)
    .put("required",        ContentType.Plain)
    .put("reversed",        ContentType.Plain)
    .put("rows",            ContentType.Plain)
    .put("rowspan",         ContentType.Plain)
    .put("sandbox",         ContentType.Unsafe)
    .put("spellcheck",      ContentType.Plain)
    .put("scope",           ContentType.Plain)
    .put("scoped",          ContentType.Plain)
    .put("seamless",        ContentType.Plain)
    .put("selected",        ContentType.Plain)
    .put("shape",           ContentType.Plain)
    .put("size",            ContentType.Plain)
    .put("sizes",           ContentType.Plain)
    .put("span",            ContentType.Plain)
    .put("src",             ContentType.URL)
    .put("srcdoc",          ContentType.HTML)
    .put("srclang",         ContentType.Plain)
    .put("start",           ContentType.Plain)
    .put("step",            ContentType.Plain)
    .put("style",           ContentType.CSS)
    .put("tabindex",        ContentType.Plain)
    .put("target",          ContentType.Plain)
    .put("title",           ContentType.Plain)
    .put("type",            ContentType.Unsafe)
    .put("usemap",          ContentType.URL)
    .put("value",           ContentType.Unsafe)
    .put("width",           ContentType.Plain)
    .put("wrap",            ContentType.Plain)
    .put("xmlns",           ContentType.URL)
    .build();

  /**
   * attrType returns a conservative (upper-bound on authority) guess at the
   * type of the named attribute.
   */
  static ContentType attrType(String name) {
    name = name.toLowerCase(Locale.ENGLISH);
    if (name.startsWith("data-")) {
      // Strip data- so that custom attribute heuristics below are
      // widely applied.
      // Treat data-action as URL below.
      name = name.substring(5);
    } else {
      int colon = name.indexOf(':');
      if (colon != -1) {
        if (colon == 5 && name.startsWith("xmlns")) {
          return ContentType.URL;
        }
        // Treat svg:href and xlink:href as href below.
        name = name.substring(colon+1);
      }
    }
    ContentType t = ATTR_TYPE_MAP.get(name);
    if (t != null) { return t; }
    // Treat partial event handler names as script.
    if (name.startsWith("on")) {
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
    if (name.contains("src")
        || name.contains("uri")
        || name.contains("url")) {
      return ContentType.URL;
    }
    return ContentType.Plain;
  }
}