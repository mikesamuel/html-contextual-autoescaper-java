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
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

/** JS contains utilities for dealing with JavaScript contexts. */
class JS {

  /**
   * nextJSCtx returns the context that determines whether a slash after the
   * given run of tokens tokens starts a regular expression instead of a
   * division operator: / or /=.
   * <p>
   * This assumes that the token run does not include any string tokens, comment
   * tokens, regular expression literal tokens, or division operators.
   * <p>
   * This fails on some valid but nonsensical JavaScript programs like
   * "x = ++/foo/i" which is quite different than "x++/foo/i", but is not known
   * to fail on any known useful programs. It is based on the draft
   * JavaScript 2.0 lexical grammar and requires one token of lookbehind:
   * http://www.mozilla.org/js/language/js20-2000-07/rationale/syntax.html
   *
   * @param precJSCtx one of the {@clink Context.JSCtx} values which is used
   *    when there are no tokens on the end of s.
   */
  static int nextJSCtx(String s, int off, int end, int precJSCtx) {
    while (end > off) {
      char ch = s.charAt(end - 1);
      switch (ch) {
        case '\t': case '\n': case '\r': case ' ':
        case '\u2028': case '\u2029':
          --end;
          continue;
      }
      break;
    }
    if (off == end) {
      return precJSCtx;
    }

    // All cases below are in the single-byte UTF-8 group.
    char c = s.charAt(end - 1);
    switch (c) {
      case '+': case '-':
        // ++ and -- are not regexp preceders, but + and - are whether
        // they are used as infix or prefix operators.
        int start = end - 1;
        // Count the number of adjacent dashes or pluses.
        while (start > off && s.charAt(start-1) == c) {
          start--;
        }
        if (((end - start) & 1) == 1) {
          // Reached for trailing minus signs since "---" is the
          // same as "-- -".
          return Context.JSCtx.Regexp;
        }
        return Context.JSCtx.DivOp;
      case '.':
        // Handle "42."
        if (end-off >= 2 && '0' <= s.charAt(end-2) && s.charAt(end-2) <= '9') {
          return Context.JSCtx.DivOp;
        }
        return Context.JSCtx.Regexp;
      // Suffixes for all punctuators from section 7.7 of the language spec
      // that only end binary operators not handled above.
      case ',': case '<': case '>': case '=': case '*': case '%': case '&':
      case '|': case '^': case '?':
        return Context.JSCtx.Regexp;
      // Suffixes for all punctuators from section 7.7 of the language spec
      // that are prefix operators not handled above.
      case '!': case '~':
        return Context.JSCtx.Regexp;
      // Matches all the punctuators from section 7.7 of the language spec
      // that are open brackets not handled above.
      case '(': case '[':
        return Context.JSCtx.Regexp;
      // Matches all the punctuators from section 7.7 of the language spec
      // that precede expression starts.
      case ':': case ';': case '{':
        return Context.JSCtx.Regexp;
      // CAVEAT: the close punctuators ('}', ']', ')') precede div ops and
      // are handled in the default except for '}' which can precede a
      // division op as in
      //    ({ valueOf: function () { return 42 } } / 2
      // which is valid, but, in practice, developers don't divide object
      // literals, so our heuristic works well for code like
      //    function () { ... }  /foo/.test(x) && sideEffect();
      // The ')' punctuator can precede a regular expression as in
      //     if (b) /foo/.test(x) && ...
      // but this is much less likely than
      //     (a + b) / c
      case '}':
        return Context.JSCtx.Regexp;
      default:
        // Look for an IdentifierName and see if it is a keyword that
        // can precede a regular expression.
        int j = end;
        while (j > off && isJSIdentPart(s.charAt(j - 1))) {
          j--;
        }
        if (REGEXP_PRECEDER_KEYWORDS.contains(s.substring(j, end))) {
          return Context.JSCtx.Regexp;
        }
        // Otherwise is a punctuator not listed above, or
        // a string which precedes a div op, or an identifier
        // which precedes a div op.
        return Context.JSCtx.DivOp;
    }
  }

  /**
   * REGEXP_PRECEDER_KEYWORDS is a set of reserved JS keywords that can
   * precede a regular expression in JS source.
   */
  private static final Set<String> REGEXP_PRECEDER_KEYWORDS = ImmutableSet.of(
      "break", "case", "continue", "delete", "do", "else", "finally", "in",
      "instanceof", "return", "throw", "try", "typeof", "void");

  /**
   * isJSIdentPart returns whether the given rune is a JS identifier part.
   * It does not handle all the non-Latin letters, joiners, and combining marks,
   * but it does handle every codepoint that can occur in a numeric literal or
   * a keyword.
   */
  static boolean isJSIdentPart(char c) {
    return c == '$' || c == '_'
        || ('0' <= c && c <= '9')
        || ('A' <= c && c <= 'Z')
        || ('a' <= c && c <= 'z');
  }

  static final ReplacementTable STR_REPLACEMENT_TABLE
      = new ReplacementTable()
      .add((char) 0, "\\0")
      .add('\t', "\\t")
      .add('\n', "\\n")
      .add('\u000b', "\\x0b") // "\v" == "v" on IE 6.
      .add('\f', "\\f")
      .add('\r', "\\r")
      // Encode HTML specials as hex so the output can be embedded
      // in HTML attributes without further encoding.
      .add('"', "\\x22")
      .add('&', "\\x26")
      .add('\'', "\\x27")
      .add('+', "\\x2b")
      .add('/', "\\/")
      .add('<', "\\x3c")
      .add('>', "\\x3e")
      .add('\\', "\\\\")
      .replaceNonAscii(new int[] { 0x2028, 0x2029 },
                       new String[] { "\\u2028", "\\u2029" });
  /**
   * STR_NORM_REPLACEMENT_TABLE is like STR_REPLACEMENT_TABLE but does not
   * overencode existing escapes since this table has no entry for "\\".
   */
  private static final ReplacementTable STR_NORM_REPLACEMENT_TABLE
      = new ReplacementTable(STR_REPLACEMENT_TABLE)
      .add('\\', null);

  private static final ReplacementTable REGEX_REPLACEMENT_TABLE
      = new ReplacementTable(STR_REPLACEMENT_TABLE) {
        /**
         * Ensures that {@code /$x/} does not become a line comment when x is
         * empty.
         */
        @Override
        protected void writeEmpty(Writer out) throws IOException {
          out.write("(?:)");
        }
      }
      .add('$', "\\$")
      .add('(', "\\(")
      .add(')', "\\)")
      .add('*', "\\*")
      .add('-', "\\-")
      .add('.', "\\.")
      .add('?', "\\?")
      .add('[', "\\[")
      .add(']', "\\]")
      .add('^', "\\^")
      .add('{', "\\{")
      .add('|', "\\|")
      .add('}', "\\}");

  static void escapeStrOnto(@Nullable Object o, Writer out) throws IOException {
    String safe = ContentType.JSStr.derefSafeContent(o);
    if (safe != null) {
      STR_NORM_REPLACEMENT_TABLE.escapeOnto(safe, out);
      return;
    }
    STR_REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  static void escapeRegexpOnto(@Nullable Object o, Writer out)
      throws IOException {
    REGEX_REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  static void escapeValueOnto(@Nullable Object o, Writer out)
      throws IOException {
    new JSValueEscaper(out).escape(o, true);
  }
}

class JSValueEscaper {
  private final Writer out;
  private IdentityHashMap<Object, ?> seen;

  JSValueEscaper(Writer out) { this.out = out; }

  /** A sequence of one or more valid JSON tokens as defined in RFC 4627. */
  private static final Pattern JSON_TOKENS = Pattern.compile(
     // Ignore leading whitespace.
     "[\t\n\r ]*"
     + "(?:(?:[\\[\\]{}:,]|"
     // A keyword.
     + "(?:false|null|true|"
     // A number
     + "-?(?:0|[1-9][0-9]*)(?:[.][0-9]+)?(?:[eE][+-]?[0-9]+)?"
     // Keywords and numbers cannot be followed by identifier chars.
     + "(?![a-zA-Z0-9_$])"
     + ")|"
     // A string
     + "\"(?:[^\\\\\"\\u0000-\\u001f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*\""
     + ")"
     // Any token can be followed by trailing whitespace.
     + "[\t\n\r ]*)+");

  /**
   * Sanity check JSON to make sure it preserves string boundaries and does
   * not contain free variables.
   */
  static String sanityCheckJSON(String json) {
    // This does not match brackets.
    // TODO: match brackets to exclude all invalid JSON.
    if (JSON_TOKENS.matcher(json).matches()) {
      // Fixup U+2028 and U+2029 which are allowed in JSON string literals
      // unencoded but not in JS.
      return json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
    // Report an error message as a comment.
    Matcher m = JSON_TOKENS.matcher(json);
    String problemText;
    if (m.find()) {
      if (m.start() != 0) {  // Invalid content at the front.
        problemText = json.substring(0, m.start());
      } else {
        problemText = json.substring(m.end());
      }
    } else {  // Not a single valid JSON token in the entire string.
      problemText = json;
    }
    if (problemText.length() > 40) {
      problemText = problemText.substring(0, 37) + "...";
    }
    // Space before comment prevents it from combining with a div op to form
    // a line comment.
    return " /* json: " + problemText.replace("*", "* ") + " */ null ";
  }

  void escape(@Nullable Object o, boolean protectBoundaries)
      throws IOException {
    // Escape maps and collections to java object and array constructors.
    if (o == null || (seen != null && seen.containsKey(o))) {
      // We surround keyword and numeric values with spaces so they do not
      // merge into other tokens.
      // Surrounding with parentheses might introduce call operators.
      out.write(protectBoundaries ? " null " : "null");
    } else if (o instanceof JSONMarshaler) {
      String json = sanityCheckJSON(((JSONMarshaler) o).toJSON());
      char ch0 = json.charAt(0);  // sanityCheckJSON does not allow empty.
      if (protectBoundaries && JS.isJSIdentPart(ch0)) { out.write(' '); }
      out.write(json);
      char chn = json.charAt(json.length() - 1);
      if (protectBoundaries && JS.isJSIdentPart(chn)) { out.write(' '); }
    } else if (o instanceof Number || o instanceof Boolean) {
      // This might result in free variables NaN and Infinity being read.
      if (protectBoundaries) { out.write(' '); }
      out.write(o.toString());
      if (protectBoundaries) { out.write(' '); }
    } else if (o instanceof Iterable<?>) {
      markSeen(o);
      char pre = '[';
      for (Object el : (Iterable<?>) o) {
        out.write(pre);
        pre = ',';
        escape(el, false);
      }
      if (pre == '[') {
        out.write("[]");
      } else {
        out.write(']');
      }
    } else if (o instanceof Map<?, ?>) {
      markSeen(o);
      char pre = '{';
      for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
        out.write(pre);
        pre = ',';
        Object k = e.getKey();
        Object v = e.getValue();
        out.write('\'');
        JS.STR_REPLACEMENT_TABLE.escapeOnto(k, out);
        out.write("\':");
        escape(v, false);
      }
      if (pre == '{') {
        out.write("{}");
      } else {
        out.write('}');
      }
    } else if (o.getClass().isArray() && !(o instanceof char[])) {
      markSeen(o);
      int len = Array.getLength(o);
      if (len == 0) {
        out.write("[]");
      } else {
        char pre = '[';
        for (int i = 0; i < len; ++i) {
          out.write(pre);
          pre = ',';
          escape(Array.get(o, i), false);
        }
        out.write(']');
      }
    } else {
      out.write('\'');
      JS.STR_REPLACEMENT_TABLE.escapeOnto(o, out);
      out.write('\'');
    }
  }

  private void markSeen(Object o) {
    if (seen == null) { seen = new IdentityHashMap<Object, Object>(); }
    seen.put(o, null);
  }
}

// TODO: stop mucking around with char[]s.