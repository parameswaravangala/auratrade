package com.auratrade.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One condition of a strategy, as evaluated for one share and session: what was measured, the
 * threshold it was held to, and the outcome. {@code required} conditions decide eligibility;
 * optional ones only explain or feed the quality score.
 *
 * @param id stable key, for example {@code close_above_ema20}
 * @param description one plain sentence, for example "Close above the 20-day EMA"
 * @param threshold the rule as text, for example "&gt; 1520.40" or "55 to 70"
 * @param value what was measured, empty when it could not be measured
 */
public record ConditionResult(
    String id,
    String description,
    boolean required,
    Outcome outcome,
    Optional<BigDecimal> value,
    String threshold) {

  public ConditionResult {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(threshold, "threshold");
    if (outcome == Outcome.UNKNOWN && value.isPresent()) {
      throw new IllegalArgumentException("UNKNOWN condition " + id + " cannot carry a value");
    }
    value = value.map(v -> v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros());
  }

  /**
   * A required condition on a measured value: UNKNOWN when the value is missing, otherwise PASS or
   * FAIL by {@code test}.
   */
  public static ConditionResult check(
      String id,
      String description,
      Optional<BigDecimal> value,
      Predicate<BigDecimal> test,
      String threshold) {
    return check(id, description, true, value, test, threshold);
  }

  /** Same as {@link #check(String, String, Optional, Predicate, String)}, optional condition. */
  public static ConditionResult optional(
      String id,
      String description,
      Optional<BigDecimal> value,
      Predicate<BigDecimal> test,
      String threshold) {
    return check(id, description, false, value, test, threshold);
  }

  /** A condition whose data source is not available; always UNKNOWN. */
  public static ConditionResult unavailable(
      String id, String description, boolean required, String threshold) {
    return new ConditionResult(
        id, description, required, Outcome.UNKNOWN, Optional.empty(), threshold);
  }

  private static ConditionResult check(
      String id,
      String description,
      boolean required,
      Optional<BigDecimal> value,
      Predicate<BigDecimal> test,
      String threshold) {
    if (value.isEmpty()) {
      return unavailable(id, description, required, threshold);
    }
    Outcome outcome = test.test(value.get()) ? Outcome.PASS : Outcome.FAIL;
    return new ConditionResult(id, description, required, outcome, value, threshold);
  }
}
