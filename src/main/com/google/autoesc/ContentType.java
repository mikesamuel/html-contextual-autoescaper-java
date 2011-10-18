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

import javax.annotation.Nullable;

/**
 * Describe strings of content from a known trusted source.
 */
public enum ContentType {

  Plain,
  /**
   * CSS encapsulates known safe content that matches any of:<ul>
   * <li>The CSS3 stylesheet production, such as
   *   <code>p { color: purple }</code>.
   * <li>The CSS3 rule production, such as {@code a[href=~"https:"].foo#bar}.
   * <li>CSS3 declaration productions, such as {@code color: red; margin: 2px}.
   * <li>The CSS3 value production, such as {@code rgba(0, 0, 255, 127)}.
   * </ul>
   * @see <a href="http://www.w3.org/TR/css3-syntax/#style">CSS3 syntax</a>
   */
  CSS,
  /**
   * HTML encapsulates a known safe HTML document fragment.
   * It should not be used for HTML from a third-party, or HTML with
   * unclosed tags or comments. The outputs of a sound HTML sanitizer
   * and a template escaped by this package are fine for use with HTML.
   */
  HTML,
  /**
   * HTMLAttr encapsulates an HTML attribute from a trusted source,
   * for example: {@code  dir="ltr"}.
   */
  HTMLAttr,
  /**
   * JS encapsulates a known safe EcmaScript5 Expression, or example,
   * {@code (x + y * z())}.
   * Template authors are responsible for ensuring that typed expressions
   * do not break the intended precedence and that there is no
   * statement/expression ambiguity as when passing an expression like
   * <code>"{ foo: bar() }\n['foo']()"</code>, which is both a valid
   * Expression and a valid Program with a very different meaning.
   */
  JS,
  /**
   * JSStr encapsulates a sequence of characters meant to be embedded
   * between quotes in a JavaScript expression.
   * The string must match a series of StringCharacters:
   * <pre>
   * StringCharacter :: SourceCharacter but not '\\' or LineTerminator
   *                  | EscapeSequence
   * </pre>
   * Note that LineContinuations are not allowed.
   * {@code "foo\\nbar"} is fine, but {@code "foo\\\nbar"} is not.
   */
  JSStr,
  /**
   * URL encapsulates a known safe URL as defined in RFC 3896.
   * A URL like {@code javascript:checkThatFormNotEditedBeforeLeavingPage()}
   * from a trusted source should go in the page, but by default dynamic
   * {@code javascript:} URLs are filtered out since they are a frequently
   * exploited injection vector.
   */
  URL,
  /**
   * Unsafe is used in attr.go for values that affect how
   * embedded content and network messages are formed, vetted,
   * or interpreted; or which credentials network messages carry.
   */
  Unsafe,
  ;

  String derefSafeContent(@Nullable Object o) {
    if (!(o instanceof SafeContent)) { return null; }
    SafeContent c = (SafeContent) o;
    if (c.getContentType() != this) {
      return null;
    }
    return c.toString();
  }
}
