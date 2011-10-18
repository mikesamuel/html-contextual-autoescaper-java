package com.google.autoesc;

import javax.annotation.Nonnull;

/**
 * An object that can be converted to a JSON representation.
 */
public interface JSONMarshaler {
  public @Nonnull String toJSON();
}