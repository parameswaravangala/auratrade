package com.auratrade.strategy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** One declared parameter of a strategy, with its allowed range and default. */
public sealed interface ParameterSpec {

  Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9]{0,39}");

  String name();

  String description();

  BigDecimal min();

  BigDecimal max();

  BigDecimal defaultValue();

  /** Whether values must be whole numbers. */
  boolean integral();

  record DecimalParam(
      String name, String description, BigDecimal min, BigDecimal max, BigDecimal defaultValue)
      implements ParameterSpec {
    public DecimalParam {
      checkSpec(name, description, min, max, defaultValue);
    }

    @Override
    public boolean integral() {
      return false;
    }
  }

  record IntegerParam(String name, String description, int minInt, int maxInt, int defaultInt)
      implements ParameterSpec {
    public IntegerParam {
      checkSpec(
          name,
          description,
          BigDecimal.valueOf(minInt),
          BigDecimal.valueOf(maxInt),
          BigDecimal.valueOf(defaultInt));
    }

    @Override
    public BigDecimal min() {
      return BigDecimal.valueOf(minInt);
    }

    @Override
    public BigDecimal max() {
      return BigDecimal.valueOf(maxInt);
    }

    @Override
    public BigDecimal defaultValue() {
      return BigDecimal.valueOf(defaultInt);
    }

    @Override
    public boolean integral() {
      return true;
    }
  }

  private static void checkSpec(
      String name, String description, BigDecimal min, BigDecimal max, BigDecimal defaultValue) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(min, "min");
    Objects.requireNonNull(max, "max");
    Objects.requireNonNull(defaultValue, "defaultValue");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("parameter name must be lowerCamelCase: " + name);
    }
    if (min.compareTo(max) > 0
        || defaultValue.compareTo(min) < 0
        || defaultValue.compareTo(max) > 0) {
      throw new IllegalArgumentException("parameter " + name + ": need min <= default <= max");
    }
  }
}
