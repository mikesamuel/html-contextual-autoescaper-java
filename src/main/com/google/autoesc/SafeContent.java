package com.google.autoesc;

import javax.annotation.Nonnull;

/**
 * A class whose {@code toString()} method produces a string of content that
 * is known to satisfy the constraints of its
 * {@link SafeContentString#getContentType content type}.
 */
public interface SafeContent {
  /**
   * The type of content produced by {@link #toString}.
   */
  public @Nonnull ContentType getContentType();
  /**
   * A string that meets the criteria for its
   * {@link #getContentType content type}.
   */
  public @Nonnull String toString();
}
