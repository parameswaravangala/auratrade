package com.auratrade.strategy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Validated, immutable parameter values for one strategy version. Only {@link ParameterSchema}
 * creates them, so every value is declared, in range and complete (defaults filled in).
 */
public final class Parameters {

  private final SortedMap<String, BigDecimal> values;

  Parameters(Map<String, BigDecimal> values) {
    this.values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
  }

  public BigDecimal decimal(String name) {
    BigDecimal v = values.get(name);
    if (v == null) {
      throw new IllegalArgumentException("undeclared parameter: " + name);
    }
    return v;
  }

  public int integer(String name) {
    return decimal(name).intValueExact();
  }

  /** All values, sorted by name; this is what a strategy version stores. */
  public SortedMap<String, BigDecimal> asMap() {
    return values;
  }

  /** Equal when the same names have numerically equal values (1.50 equals 1.5). */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Parameters other) || other.values.size() != values.size()) {
      return false;
    }
    return values.entrySet().stream()
        .allMatch(e -> {
          BigDecimal v = other.values.get(e.getKey());
          return v != null && v.compareTo(e.getValue()) == 0;
        });
  }

  @Override
  public int hashCode() {
    int h = 0;
    for (var e : values.entrySet()) {
      h += e.getKey().hashCode() ^ e.getValue().stripTrailingZeros().hashCode();
    }
    return h;
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
