#!python

# This generates a java source file by taking each method that has a
# parameters (String s, int off, int end) and generating a copy that
# takes (char[] s, int off, int end).

src = r"""// Copyright (C) 2011 Google Inc.
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

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
    int e = end;
    while (e > off) {
      char ch = s.charAt(e - 1);
      switch (ch) {
        case '\t': case '\n': case '\r': case ' ':
        case '\u2028': case '\u2029':
          --e;
          continue;
      }
      break;
    }
    if (off == e) {
      return precJSCtx;
    }

    // All cases below are in the single-byte UTF-8 group.
    char c = s.charAt(e - 1);
    switch (c) {
      case '+': case '-':
        // ++ and -- are not regexp preceders, but + and - are whether
        // they are used as infix or prefix operators.
        int start = e - 1;
        // Count the number of adjacent dashes or pluses.
        while (start > off && s.charAt(start-1) == c) {
          start--;
        }
        if (((e - start) & 1) == 1) {
          // Reached for trailing minus signs since "---" is the
          // same as "-- -".
          return Context.JSCtx.Regexp;
        }
        return Context.JSCtx.DivOp;
      case '.':
        // Handle "42."
        if (e-off >= 2 && '0' <= s.charAt(e-2) && s.charAt(e-2) <= '9') {
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
        int j = e;
        while (j > off && isJSIdentPart(s.charAt(j - 1))) {
          j--;
        }
        if (isRegexpPrecederKeyword(s, j, e)) {
          return Context.JSCtx.Regexp;
        }
        // Otherwise is a punctuator not listed above, or
        // a string which precedes a div op, or an identifier
        // which precedes a div op.
        return Context.JSCtx.DivOp;
    }
  }

  static boolean isRegexpPrecederKeyword(String s, int off, int end) {
    // Below is a set of reserved JS keywords that can
    // precede a regular expression in JS source.
    switch (end - off) {
      case 2:
        return CharsUtil.startsWith(s, off, end, "do")
            || CharsUtil.startsWith(s, off, end, "in");
      case 3:
        return CharsUtil.startsWith(s, off, end, "try");
      case 4:
        return CharsUtil.startsWith(s, off, end, "case")
            || CharsUtil.startsWith(s, off, end, "else")
            || CharsUtil.startsWith(s, off, end, "void");
      case 5:
        return CharsUtil.startsWith(s, off, end, "break")
            || CharsUtil.startsWith(s, off, end, "throw");
      case 6:
        return CharsUtil.startsWith(s, off, end, "delete")
            || CharsUtil.startsWith(s, off, end, "return")
            || CharsUtil.startsWith(s, off, end, "typeof");
      case 7:
        return CharsUtil.startsWith(s, off, end, "finally");
      case 8:
        return CharsUtil.startsWith(s, off, end, "continue");
      case 10:
        return CharsUtil.startsWith(s, off, end, "instanceof");
    }
    return false;
  }

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
      // Encode HTML specials as hex so the output can be embedded
      // in HTML attributes without further encoding.
      .add('`', "\\x60")
      .add('"', "\\x22")
      .add('&', "\\x26")
      .add('\'', "\\x27")
      // JS strings cannot contain embedded newlines.  Escape all space chars.
      // U+2028 and U+2029 handled below.
      .add('\t', "\\t")
      .add('\n', "\\n")
      .add('\u000b', "\\x0b") // "\v" == "v" on IE 6.
      .add('\f', "\\f")
      .add('\r', "\\r")
      // Prevent function calls even if they escape, and handle capturing
      // groups when inherited by regex below.
      .add('(', "\\(")
      .add(')', "\\)")
      // UTF-7 attack vector
      .add('+', "\\x2b")
      // Prevent embedded "</script"
      .add('/', "\\/")
      // Prevent embedded <!-- and -->
      .add('<', "\\x3c")
      .add('>', "\\x3e")
      // Correctness.
      .add('\\', "\\\\")
      // JavaScript specific newline chars.
      .replaceNonAscii(new int[] { 0x2028, 0x2029 },
                       new String[] { "\\u2028", "\\u2029" });
  /**
   * STR_NORM_REPLACEMENT_TABLE is like STR_REPLACEMENT_TABLE but does not
   * overencode existing escapes since this table has no entry for "\\".
   */
  static final ReplacementTable STR_NORM_REPLACEMENT_TABLE
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
      .add('{', "\\{")
      .add('|', "\\|")
      .add('}', "\\}")
      .add('$', "\\$")
      .add('*', "\\*")
      .add('-', "\\-")
      .add('.', "\\.")
      .add('?', "\\?")
      .add('[', "\\[")
      .add(']', "\\]")
      .add('^', "\\^");

  static void escapeStrOnto(@Nullable Object o, Writer out) throws IOException {
    String safe = ContentType.JSStr.derefSafeContent(o);
    if (safe != null) {
      STR_NORM_REPLACEMENT_TABLE.escapeOnto(safe, out);
      return;
    }
    STR_REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  static void escapeStrOnto(String s, int off, int end, Writer out)
      throws IOException {
    STR_REPLACEMENT_TABLE.escapeOnto(s, off, end, out);
  }

  static void escapeRegexpOnto(@Nullable Object o, Writer out)
      throws IOException {
    REGEX_REPLACEMENT_TABLE.escapeOnto(o, out);
  }

  static void escapeRegexpOnto(String s, int off, int end, Writer out)
      throws IOException {
    REGEX_REPLACEMENT_TABLE.escapeOnto(s, off, end, out);
  }

  static void escapeValueOnto(@Nullable Object o, Writer out)
      throws IOException {
    new JSValueEscaper(out).escape(o, true);
  }

  static void escapeValueOnto(String s, int off, int end, Writer out)
      throws IOException {
    out.write('\'');
    STR_REPLACEMENT_TABLE.escapeOnto(s, off, end, out);
    out.write('\'');
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

  void escape(@Nullable Object obj, boolean protectBoundaries)
      throws IOException {
    // Escape maps and collections to java object and array constructors.
    Object o = obj;
    if (o == null || (seen != null && seen.containsKey(o))) {
      // We surround keyword and numeric values with spaces so they do not
      // merge into other tokens.
      // Surrounding with parentheses might introduce call operators.
      out.write(protectBoundaries ? " null " : "null");
      return;
    }
    if (o instanceof SafeContent) {
      SafeContent ct = (SafeContent) o;
      ContentType t = ct.getContentType();
      switch (t) {
        case JS:
          if (protectBoundaries) { out.write(' '); }
          out.write(ct.toString());
          if (protectBoundaries) { out.write(' '); }
          return;
        case JSStr:
          String s = ct.toString();
          int trailingSlashes = 0;
          for (int i = s.length(); --i >= 0; ++trailingSlashes) {
            if (s.charAt(i) != '\\') { break; }
          }
          out.write('\'');
          JS.STR_NORM_REPLACEMENT_TABLE.escapeOnto(s, out);
          if ((trailingSlashes & 1) != 0) {
            out.write('\\');
          }
          // If s ends with an incomplete escape sequence, complete it.
          out.write('\'');
          return;
        default:
          // Fall through to cases below.
          o = ct.toString();
      }
    }
    if (o instanceof JSONMarshaler) {
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
    } else if (!beanToJS(o)) {
      out.write('\'');
      JS.STR_REPLACEMENT_TABLE.escapeOnto(o, out);
      out.write('\'');
    }
  }

  private void markSeen(Object o) {
    if (seen == null) { seen = new IdentityHashMap<>(); }
    seen.put(o, null);
  }

  /**
   * Converts a Java bean object into a JS object constructor by reflectively
   * examining its public fields and getter methods.
   */
  private boolean beanToJS(Object o) throws IOException {
    // CharSequences should be treated as strings, and enum values should
    // not have fields serialized since they are better identified by name
    // or ordinal.
    if (o instanceof CharSequence || o instanceof Enum) { return false; }
    Class<?> c = o.getClass();
    ClassSchema schema = ClassSchema.forClass(c);
    if (schema == null) { return false; }
    markSeen(o);
    char pre = '{';
    for (Field f : schema.fields) {
      String name = f.getName();
      Object v;
      try {
        v = f.get(o);
      } catch (IllegalAccessException e) {
        // Should not occur since we checked public-ness.
        // TODO: does the declaring class and any containing class also have
        // to be public?
        throw (AssertionError)
            new AssertionError(name + " of " + o.getClass()).initCause(e);
      }
      out.write(pre);
      pre = ',';
      out.write('\'');
      out.write(name);  // Name is a valid JavaScript identifier.
      out.write("\':");
      escape(v, false);
    }
    for (int i = 0, n = schema.getters.length; i < n; ++i) {
      Method m = schema.getters[i];
      String name = schema.getterFieldNames[i];
      Object v = null;
      try {
        v = m.invoke(o);
      } catch (IllegalAccessException e) {
        // TODO: same caveats to check as above.
        throw (AssertionError)
            new AssertionError(name + " of " + o.getClass()).initCause(e);
      } catch (@SuppressWarnings("unused") InvocationTargetException e) {
        // Getter failed.  Treat as a non-existant property.
        continue;
      }
      out.write(pre);
      pre = ',';
      out.write('\'');
      out.write(name);
      out.write("\':");
      escape(v, false);
    }
    if (pre == '{') {
      out.write("{}");
    } else {
      out.write('}');
    }
    return true;
  }
}

/**
 * Collects reflective information about the properties of a class that can
 * be used to turn it into a JS representation.
 */
class ClassSchema {
  final String className;
  final Field[] fields;
  final Method[] getters;
  final String[] getterFieldNames;

  private static final ClassSchema NOT_A_BEAN = new ClassSchema();
  private static final Map<Class<?>, ClassSchema> CLASS_TO_SCHEMA
      = Collections.synchronizedMap(
          new IdentityHashMap<Class<?>, ClassSchema>());
  static {
    CLASS_TO_SCHEMA.put(Class.class, NOT_A_BEAN);
    try {
      CLASS_TO_SCHEMA.put(
          GregorianCalendar.class,
          new ClassSchema(GregorianCalendar.class,
                          new String[0],
                          new String[] { "getClass", "getTimeInMillis" }));
      CLASS_TO_SCHEMA.put(
          Date.class,
          new ClassSchema(Date.class,
                          new String[0],
                          new String[] { "getClass", "getTime" }));
      CLASS_TO_SCHEMA.put(
          java.sql.Date.class,
          new ClassSchema(Date.class,
                          new String[0],
                          new String[] { "getClass", "getTime" }));
    } catch (NoSuchFieldException e) {
      throw new AssertionError(e);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static ClassSchema forClass(Class<?> c) {
    ClassSchema s = CLASS_TO_SCHEMA.get(c);
    if (s == null) {
      s = new ClassSchema(c);
      if (s.fields.length == 0 && s.getters.length == 1 /* getClass */) {
        s = NOT_A_BEAN;
      }
      CLASS_TO_SCHEMA.put(c, s);
    }
    return s == NOT_A_BEAN ? null : s;
  }

  private ClassSchema() {
    this.className = null;
    this.fields = null;
    this.getters = null;
    this.getterFieldNames = null;
  }

  private ClassSchema(Class<?> c, String[] fieldNames, String[] methodNames)
      throws NoSuchFieldException, NoSuchMethodException {
    this.className = c.getName();
    this.fields = new Field[fieldNames.length];
    for (int i = 0; i < fieldNames.length; ++i) {
      fields[i] = c.getField(fieldNames[i]);
    }
    this.getterFieldNames = new String[methodNames.length];
    this.getters = new Method[methodNames.length];
    Class<?>[] none = new Class<?>[0];
    for (int i = 0; i < methodNames.length; ++i) {
      String name = methodNames[i];
      if (name.startsWith("get")) {
        getterFieldNames[i] = Character.toLowerCase(name.charAt(3))
             + name.substring(4);
      } else {
        getterFieldNames[i] = name;
      }
      getters[i] = c.getMethod(name, none);
    }
  }

  private ClassSchema(Class<?> c) {
    this.className = c.getName();
    List<Field> fieldList = new ArrayList<>();
    List<Method> getterList = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (Field f : c.getFields()) {
      int mods = f.getModifiers();
      if (Modifier.isPublic(mods) && !Modifier.isStatic(mods)
          && !Modifier.isVolatile(mods) && names.add(f.getName())) {
        fieldList.add(f);
      }
    }
    for (Method m : c.getMethods()) {
      if (Void.TYPE.equals(m.getReturnType())) { continue; }
      String name = m.getName();
      if (!(name.startsWith("get") && m.getParameterTypes().length == 0)) {
        continue;
      }
      int mods = m.getModifiers();
      if (!(Modifier.isPublic(mods) && !Modifier.isStatic(mods))) {
        continue;
      }
      if (names.add(methodNameToFieldName(name))) {
        getterList.add(m);
      }
    }
    this.fields = fieldList.toArray(new Field[fieldList.size()]);
    // TODO: Create one comparator for members and make it singleton.
    // TODO: Find a JSR305 annotation or something similar to exempt fields
    // from serialization.  Maybe JPA @javax.persistence.Transient.
    Arrays.sort(this.fields, new Comparator<Field>() {
        @Override
        public int compare(Field a, Field b) {
          return a.getName().compareTo(b.getName());
        }
      });
    this.getters = getterList.toArray(new Method[getterList.size()]);
    Arrays.sort(this.getters, new Comparator<Method>() {
        @Override
        public int compare(Method a, Method b) {
          return a.getName().compareTo(b.getName());
        }
      });
    this.getterFieldNames = new String[this.getters.length];
    for (int i = 0; i < this.getters.length; ++i) {
      this.getterFieldNames[i] = methodNameToFieldName(
          this.getters[i].getName());
    }
  }

  static String methodNameToFieldName(String name) {
    if (name.startsWith("get")) {
      // getFoo -> foo
      return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }
    return name;
  }
}
"""  # Fix emacs syntax highlighting "

import dupe_methods
print dupe_methods.dupe(src)
