package com.auratrade.strategy;

import java.util.List;

/** Parameter values that do not satisfy a strategy's schema. Lists every problem found. */
public final class InvalidParametersException extends IllegalArgumentException {

  private final transient List<String> errors;

  InvalidParametersException(List<String> errors) {
    super(String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}
