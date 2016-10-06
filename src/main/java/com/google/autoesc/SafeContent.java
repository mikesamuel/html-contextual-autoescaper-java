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
  @Override
  public @Nonnull String toString();
}
