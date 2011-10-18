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
