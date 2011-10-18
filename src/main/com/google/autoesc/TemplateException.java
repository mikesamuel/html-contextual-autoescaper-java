package com.google.autoesc;

/**
 * TemplateException is thrown when broken HTML is encountered that has bad
 * security consequences.
 */
public class TemplateException extends Exception {
  static final long serialVersionUID = -8621232850911102715L;

  public TemplateException(String message) {
    super(message);
  }
}