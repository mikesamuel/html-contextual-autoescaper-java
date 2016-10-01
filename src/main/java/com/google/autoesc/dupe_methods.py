# Find each method we want to duplicate.
import re

# Assumes proper indentation.
# Methods end with a curly bracket indented by two spaces.
signature_and_body = (
  r'(?s)'  # . matches newlines.
  # Signature like (String s, int off, int end).
  # Portion of sig before "String" in group 1 incl. return type name and flags.
  # Portion of sig after "String" in group 2 incl. extra params and exceptions.
  # Body of method in group 3.
  r'([^\n]*\(\s*)String(\s*s\s*,\s*int\s*off\s*,\s*int\s*end[^)]*\)[^{};]*)'
  r'(\{(.*?)\n  \})')

def dupe_method(m):
  sig = '%schar[]%s' % (m.group(1), m.group(2))
  body = m.group(3)
  body = re.sub(r'(?<![a-zA-Z_$])s\s*\.\s*(charAt|codePointAt)\s*\(',
                r'CharsUtil.\1(s, ', body)
  return '%s\n\n%s%s' % (m.group(0), sig, body)

def dupe(src):
    return re.sub(signature_and_body, dupe_method, src.strip())
