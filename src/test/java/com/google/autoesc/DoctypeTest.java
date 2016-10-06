// Copyright (C) 2012 Google Inc.
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

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DoctypeTest extends TestCase {

  static final class Sample {
    public final String doctype;
    public final int state;

    Sample(String doctype, int state) {
      this.doctype = doctype;
      this.state = state;
    }
  }

  public static final void testClassify() {
    Sample[] samples = new Sample[] {
      // From http://www.w3.org/QA/2002/04/valid-dtd-list.html
      new Sample(
          "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\""
          + " \"http://www.w3.org/TR/html4/strict.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\""
          + " \"http://www.w3.org/TR/html4/loose.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\""
          + "\"http://www.w3.org/TR/html4/frameset.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\""
          + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\""
          + " \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML Basic 1.1//EN\""
          + "\"http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd\">",
          Context.State.Text),
      new Sample("<!DOCTYPE HTML>", Context.State.Text),
      new Sample("<!DOCTYPE html>", Context.State.Text),
      new Sample("<!DOCTYPE\thtml >", Context.State.Text),
      new Sample("<!DOCTYPE html5>", Context.State.Text),
      new Sample(
          "<!DOCTYPE math PUBLIC \"-//W3C//DTD MathML 2.0//EN\""
          + " \"http://www.w3.org/Math/DTD/mathml2/mathml2.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE math SYSTEM"
          + " \"http://www.w3.org/Math/DTD/mathml1/mathml.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC"
          + " \"-//W3C//DTD XHTML 1.1 plus MathML 2.0 plus SVG 1.1//EN\""
          + " \"http://www.w3.org/2002/04/xhtml-math-svg/xhtml-math-svg.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE svg:svg PUBLIC"
          + " \"-//W3C//DTD XHTML 1.1 plus MathML 2.0 plus SVG 1.1//EN\""
          + " \"http://www.w3.org/2002/04/xhtml-math-svg/xhtml-math-svg.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\""
          + " \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.0//EN\""
          + " \"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1 Basic//EN\""
          + " \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-basic.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1 Tiny//EN\""
          + " \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-tiny.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//IETF//DTD HTML 2.0//EN\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML Basic 1.0//EN\""
          + " \"http://www.w3.org/TR/xhtml-basic/xhtml-basic10.dtd\">",
          Context.State.Text),
      new Sample(
          "<!DOCTYPE bogus>",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE rss PUBLIC"
          + " \"-//Netscape Communications//DTD RSS 0.91//EN\""
          + " \"http://my.netscape.com/publish/formats/rss-0.91.dtd\">",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE rss [<!ENTITY % HTMLspec PUBLIC"
          + " \"-//W3C//ENTITIES Latin 1 for XHTML//EN\""
          + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml-special.ent\">",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE project PUBLIC \"-//ANT//DTD project//EN\" \"ant.dtd\" [\n"
          + "   <!ENTITY include SYSTEM \"header.xml\">\n"
          + "]>",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE schema PUBLIC \"-//W3C//DTD XMLSCHEMA 200010//EN\""
          + " \"http://www.w3.org/2000/10/XMLSchema.dtd\" >",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE spec SYSTEM"
          + " \"http://www.w3.org/2002/xmlspec/dtd/2.10/xmlspec.dtd\" [...]>",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE xsl:stylesheet [ <!ENTITY nbsp \"&#x00A0;\"> ]>",
          Context.State.XML),
      new Sample(
          "<!DOCTYPE atom:feed PUBLIC"
          + " \"-//W3C//DTD XHTML 1.0 Transitional//EN\""
          + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">",
          Context.State.XML),
    };

    int doctypeLen = "<!DOCTYPE".length();
    for (Sample s : samples) {
      assertTrue(s.doctype, s.doctype.startsWith("<!DOCTYPE"));
      assertEquals(
          s.doctype, s.state,
          Doctype.classify(
              s.doctype.substring(doctypeLen),
              0, s.doctype.length() - doctypeLen));
      assertEquals(
          s.doctype, s.state,
          Doctype.classify(s.doctype, doctypeLen, s.doctype.length()));
      assertEquals(
          s.doctype, s.state,
          Doctype.classify(
              s.doctype.toLowerCase(Locale.ENGLISH),
              doctypeLen, s.doctype.length()));
      assertEquals(
          s.doctype, s.state,
          Doctype.classify(
              s.doctype.toUpperCase(Locale.ENGLISH),
              doctypeLen, s.doctype.length()));
    }
  }
}
