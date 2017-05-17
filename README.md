A runtime contextual autoescaper written in Java.

This provides a writer-like object that provides two methods:

```java
  writeSafe(String)
  write(Object)
```

so that the sequence of calls

```java
 w.writeSafe("<b>");
 w.write("I <3 Ponies!");
 w.writeSafe("</b>\n<button onclick=foo(");
 w.writeObject(ImmutableMap.<String, Object>of(
     "foo", "bar", "\"baz\"", 42));
 w.writeSafe(")>");
```

results in the output

```html
  <b>I &lt;3 Ponies!</b>
  <button onclick="foo({&#34;foo&#34;:&#34;\x22bar\x22&#34;:42})">
```

The safe parts are treated as literal chunks of HTML/CSS/JS, and the unsafe
parts are escaped to preserve security and least-surprise.

For a more comprehensive example, a template like

```html
<div style="color: <%=$self.color%>">
  <a href="/<%=$self.color%>?q=<%=$self.world%>"
   onclick="alert('<% helper($self) %>');return false">
    <% helper($self) %>
  </a>
  <script>(function () {  // Sleepy developers put sensitive info in comments.
    var o = <%=$self>,
        w = "<%=$self.world%>";
  })();</script>
</div>

<% def helper($self) {
  %>Hello, <%=$self.world%>
<%}%>
```

might correspond to the sequence of calls

```java
 // Dummy input values.
 Map $self = ImmutableMap.<String, Object>of(
     "world", "<Cincinatti>", "color", "blue");
 Object color = self.get("color"), world = self.get("world");
 // Alternating safe and unsafe writes that implement the template.
 w.writeSafe("<div style=\"color: ");
 w.write    (color);
 w.writeSafe("\">\n<a href=\"/");
 w.write    (color);
 w.writeSafe("?q=");
 w.write    (world);
 w.writeSafe("\"\n  onclick=\"alert('");
 helper     (w, $self);
 w.writeSafe("');return false\">\n    ");
 helper     (w, $self);
 w.writeSafe("\n  </a>\n  <script>(function () {\n    var o = ");
 w.write    ($self);
 w.writeSafe(",\n        w = \"");
 w.write    (world);
 w.writeSafe("\";\n  })();</script>\n</div>");
```

which result in the output

```html
<div style="color: blue">
  <a href="/blue?q=%3cCincinatti%3e"
   onclick="alert('Hello, \x3cCincinatti\x3e!');return false">
    Hello, <Cincinatti>!
  </a>
  <script>(function () {
    var o = {"Color":"blue","World":"\u003cCincinatti\u003e"},
        w = "\x26lt;Cincinatti\x26gt;";
  })();</script>
</div>
```
