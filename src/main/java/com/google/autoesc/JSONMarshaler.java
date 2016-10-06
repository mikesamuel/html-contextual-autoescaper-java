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
 * An object that can be converted to a JSON representation.
 */
public interface JSONMarshaler {
  /**
   * @return a string of JSON that can be embedded in an HTML script element.
   *     Should not contain the case-insensitive substring {@code "</script"}.
   */
  public @Nonnull String toJSON();
}