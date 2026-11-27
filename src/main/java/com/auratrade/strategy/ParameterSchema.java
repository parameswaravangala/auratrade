package com.auratrade.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The declared parameters of a strategy and the rules across them. It is the only way to create
 * {@link Parameters}: values are checked against their ranges, unknown names are rejected, missing
 * ones take the default, and every problem is reported at once. The Strategy Studio edits values
 * through this schema; no executable code comes from the UI (spec 14.3).
 */
public final class ParameterSchema {

  /** A rule across two parameters: {@code lower} must not exceed {@code upper}. */
  public record LessOrEqual(String lower, String upper) {
    public LessOrEqual {
      Objects.requireNonNull(lower, "lower");
      Objects.requireNonNull(upper, "upper");
    }
  }

  private final Map<String, ParameterSpec> specs;
  private final List<LessOrEqual> constraints;

  private ParameterSchema(Map<String, ParameterSpec> specs, List<LessOrEqual> constraints) {
    this.specs = specs;
    this.constraints = constraints;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Parameters in declaration order. */
  public List<ParameterSpec> specs() {
    return List.copyOf(specs.values());
  }

  public List<LessOrEqual> constraints() {
    return constraints;
  }

  /** Every parameter at its default. */
  public Parameters defaults() {
    return validate(Map.of());
  }

  /**
   * Checks {@code values} and fills in defaults for missing names.
   *
   * @throws InvalidParametersException listing every unknown name, out-of-range or non-integral
   *     value, and broken cross-parameter rule
   */
  public Parameters validate(Map<String, BigDecimal> values) {
    List<String> errors = new ArrayList<>();
    for (String name : values.keySet()) {
      if (!specs.containsKey(name)) {
        errors.add("unknown parameter " + name);
      }
    }
    Map<String, BigDecimal> resolved = new HashMap<>();
    for (ParameterSpec spec : specs.values()) {
      BigDecimal v = values.getOrDefault(spec.name(), spec.defaultValue());
      if (v == null) {
        errors.add(spec.name() + " is null");
        continue;
      }
      if (v.compareTo(spec.min()) < 0 || v.compareTo(spec.max()) > 0) {
        errors.add(
            spec.name()
                + " = "
                + v.toPlainString()
                + " outside "
                + spec.min().toPlainString()
                + " to "
                + spec.max().toPlainString());
      }
      if (spec.integral() && v.stripTrailingZeros().scale() > 0) {
        errors.add(spec.name() + " = " + v.toPlainString() + " must be a whole number");
      }
      resolved.put(spec.name(), v);
    }
    for (LessOrEqual c : constraints) {
      BigDecimal lo = resolved.get(c.lower());
      BigDecimal hi = resolved.get(c.upper());
      if (lo != null && hi != null && lo.compareTo(hi) > 0) {
        errors.add(c.lower() + " must not exceed " + c.upper());
      }
    }
    if (!errors.isEmpty()) {
      throw new InvalidParametersException(errors);
    }
    return new Parameters(resolved);
  }

  public static final class Builder {
    private final Map<String, ParameterSpec> specs = new LinkedHashMap<>();
    private final List<LessOrEqual> constraints = new ArrayList<>();

    private Builder() {}

    public Builder decimal(String name, String description, String min, String max, String dflt) {
      return add(
          new ParameterSpec.DecimalParam(
              name, description, new BigDecimal(min), new BigDecimal(max), new BigDecimal(dflt)));
    }

    public Builder integer(String name, String description, int min, int max, int dflt) {
      return add(new ParameterSpec.IntegerParam(name, description, min, max, dflt));
    }

    public Builder lessOrEqual(String lower, String upper) {
      constraints.add(new LessOrEqual(lower, upper));
      return this;
    }

    private Builder add(ParameterSpec spec) {
      if (specs.putIfAbsent(spec.name(), spec) != null) {
        throw new IllegalArgumentException("duplicate parameter " + spec.name());
      }
      return this;
    }

    /** Builds the schema and checks that the defaults satisfy it. */
    public ParameterSchema build() {
      for (LessOrEqual c : constraints) {
        if (!specs.containsKey(c.lower()) || !specs.containsKey(c.upper())) {
          throw new IllegalArgumentException("constraint names an undeclared parameter: " + c);
        }
      }
      ParameterSchema schema =
          new ParameterSchema(
              java.util.Collections.unmodifiableMap(new LinkedHashMap<>(specs)),
              List.copyOf(constraints));
      schema.defaults();
      return schema;
    }
  }
}
