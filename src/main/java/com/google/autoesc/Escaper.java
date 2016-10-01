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
 * Escaper describes the escaping strategy to apply.
 */
enum Escaper {
  ELIDE,
  ESCAPE_CSS,
  ESCAPE_HTML,
  ESCAPE_HTML_ATTR,
  ESCAPE_JS_REGEXP,
  ESCAPE_JS_STRING,
  ESCAPE_JS_VALUE,
  ESCAPE_RCDATA,
  ESCAPE_CDATA,
  ESCAPE_URL,
  ESCAPE_XML,
  FILTER_CSS_URL,
  FILTER_CSS_VALUE,
  FILTER_NAME_ONTO,
  FILTER_URL,
  NORMALIZE_HTML,
  NORMALIZE_URL,
  NORMALIZE_XML,
  ONE_SPACE,
  ;
}
