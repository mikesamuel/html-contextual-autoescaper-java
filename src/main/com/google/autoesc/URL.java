package com.google.autoesc;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

class URL {
  /**
   * escapeURLOnto normalizes (when norm is true) or escapes its input to
   * produce a valid hierarchical or opaque URL part.
   */
  static void escapeOnto(boolean norm, Object o, Writer out)
    throws IOException {
    String s;
    String safe = ContentType.URL.derefSafeContent(o);
    if (safe != null) {
      s = safe;
    } else {
      s = ReplacementTable.toString(o);
    }
    int off = 0, end = s.length();
    for (int i = 0, nc; i < end; i += nc) {
      int cp = s.codePointAt(i);
      nc = Character.charCount(cp);
      switch (cp) {
        // Single quote and parens are sub-delims in RFC 3986, but we
        // escape them so the output can be embedded in in single
        // quoted attributes and unquoted CSS url(...) constructs.
        // Single quotes are reserved in URLs, but are only used in
        // the obsolete "mark" rule in an appendix in RFC 3986
        // so can be safely encoded.
        case '!': case '#': case '$': case '&': case '*': case '+': case ',':
        case '/': case ':': case ';': case '=': case '?': case '@': case '[':
        case ']':
          if (norm) {
            continue;
          }
          break;
        // Unreserved according to RFC 3986 sec 2.3
        // "For consistency, percent-encoded octets in the ranges of
        // ALPHA (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen (%2D),
        // period (%2E), underscore (%5F), or tilde (%7E) should not be
        // created by URI producers
        case '-': case '.': case '_': case '~':
          continue;
        case '%':
          // When normalizing do not re-encode valid escapes.
          if (norm && i+2 < end && isHex(s.charAt(i+1))
              && isHex(s.charAt(i+2))) {
            continue;
          }
          break;
        default:
          // Unreserved according to RFC 3986 sec 2.3
          if ('a' <= cp && cp <= 'z') { continue; }
          if ('A' <= cp && cp <= 'Z') { continue; }
          if ('0' <= cp && cp <= '9') { continue; }
      }
      out.write(s, off, i - off);
      off = i + nc;
      // This assumes that all URLs use UTF-8 as the content-encoding.
      // This is similar to the URI to IRI encoding scheme defined in
      // section 3.1 of RFC 3987, and behaves the same as the EcmaScript builtin
      // encodeURIComponent.
      // It should not cause any misencoding of URLs in pages with
      // Content-type: text/html;charset=UTF-8.
      if (cp < 0x800) {
        if (cp < 0x80) {
          emitPctOctet((byte)(cp & 0x7f), out);
        } else {
          emitPctOctet((byte)(0xc0 | (cp >> 6)), out);
          emitPctOctet((byte)(0x80 | (cp & 0x3f)), out);
        }
      } else {
        if (cp < 0x10000) {
          emitPctOctet((byte)(0xe0 | (cp >> 12)), out);
        } else {
          emitPctOctet((byte)(0xf0 | (cp >> 18)), out);
          emitPctOctet((byte)(0x80 | ((cp >> 12) & 0x3f)), out);
        }
        emitPctOctet((byte)(0x80 | ((cp >> 6) & 0x3f)), out);
        emitPctOctet((byte)(0x80 | (cp & 0x3f)), out);
      }
    }
    out.write(s, off, end - off);
  }

  private static boolean isHex(int cp) {
    return ('0' <= cp && cp <= '9') || ('A' <= cp && cp <='F')
      || ('a' <= cp && cp <= 'f');
  }

  private static void emitPctOctet(byte octet, Writer out) throws IOException {
    out.write('%');
    out.write("0123456789abcdef".charAt(octet >>> 4));
    out.write("0123456789abcdef".charAt(octet & 0xf));
  }

  static String filterURL(Object o) {
    String s;
    String safe = ContentType.URL.derefSafeContent(o);
    if (safe != null) {
      s = safe;
    } else {
      s = ReplacementTable.toString(o);
    }
    int colon = s.indexOf(':');
    if (colon >= 0) {
      int slash = s.indexOf('/');
      if (slash < 0 || slash >= colon) {
        String protocol = s.substring(0, colon).toLowerCase(Locale.ENGLISH);
        if (!("http".equals(protocol) || "https".equals(protocol)
              || "mailto".equals(protocol))) {
          return "#ZautoescZ";
        }
      }
    }
    return s;
  }
}
