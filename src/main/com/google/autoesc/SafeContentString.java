package com.google.autoesc;

import javax.annotation.Nullable;

/**
 * A string of content that is known to satisfy the constraints of its
 * {@link SafeContentString#getContentType content type}.
 */
public final class SafeContentString implements SafeContent {
  private final String content;
  private final ContentType contentType;

  public SafeContentString(String content, ContentType contentType) {
    this.content = content;
    this.contentType = contentType;
  }

  public ContentType getContentType() {
    return contentType;
  }

  @Override
  public String toString() {
    return content;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == null) { return false; }
    if (getClass() != o.getClass()) { return false; }
    SafeContentString that = (SafeContentString) o;
    return this.contentType == that.contentType
        && this.content.equals(that.content);
  }

  @Override
  public int hashCode() {
    return content.hashCode() + 31 * contentType.hashCode();
  }
}
