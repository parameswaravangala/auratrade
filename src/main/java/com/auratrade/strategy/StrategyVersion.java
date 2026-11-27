package com.auratrade.strategy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One configured, immutable version of a strategy: its code id, a version number and validated
 * parameters. Changing any parameter means a new version (spec 14.10). Storage, status and approval
 * come in a later slice (plan slice 9).
 */
public final class StrategyVersion {

  private static final Pattern SEMVER = Pattern.compile("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}");

  private final String strategyId;
  private final String version;
  private final Parameters parameters;

  private StrategyVersion(String strategyId, String version, Parameters parameters) {
    this.strategyId = strategyId;
    this.version = version;
    this.parameters = parameters;
  }

  /**
   * Validates {@code values} against the strategy's schema.
   *
   * @throws InvalidParametersException if the values break the schema
   */
  public static StrategyVersion of(
      Strategy strategy, String version, Map<String, BigDecimal> values) {
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(version, "version");
    if (!SEMVER.matcher(version).matches()) {
      throw new IllegalArgumentException("version must look like 1.0.0: " + version);
    }
    return new StrategyVersion(strategy.id(), version, strategy.parameterSchema().validate(values));
  }

  /** The strategy at its default parameters. */
  public static StrategyVersion defaults(Strategy strategy, String version) {
    return of(strategy, version, Map.of());
  }

  public String strategyId() {
    return strategyId;
  }

  public String version() {
    return version;
  }

  public Parameters parameters() {
    return parameters;
  }

  /** For example {@code momentum-breakout@1.0.0}. */
  public String ref() {
    return strategyId + "@" + version;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StrategyVersion v
        && v.strategyId.equals(strategyId)
        && v.version.equals(version)
        && v.parameters.equals(parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(strategyId, version, parameters);
  }

  @Override
  public String toString() {
    return ref() + " " + parameters;
  }
}
