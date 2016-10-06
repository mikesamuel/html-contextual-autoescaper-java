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

import java.io.IOException;

/**
 * TemplateException is thrown when broken HTML is encountered that has bad
 * security consequences.
 * It indicates that output written to the underlying channel is not guaranteed
 * to be well-formed under concatenation -- the channel cotnent is not capable
 * of being safely consumed by the far endpoint.
 */
public class TemplateException extends IOException {
  static final long serialVersionUID = -8621232850911102715L;

  /** @see IOException#IOException(String) */
  public TemplateException(String message) {
    super(message);
  }
}
