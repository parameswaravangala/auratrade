package com.auratrade.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of one strategy version for one share on one session (spec 17: stored separately from
 * any combined score). Created by {@link StrategyEvaluator}, which enforces the invariants below.
 *
 * @param strategyRef strategy id and version, for example {@code momentum-breakout@1.0.0}
 * @param score 0 to 100 with two decimals; above 0 only when {@code status} is PASSED
 * @param plan present exactly when {@code status} is PASSED
 */
public record StrategyEvaluation(
    String strategyRef,
    long instrumentId,
    String symbol,
    LocalDate asOf,
    EvaluationStatus status,
    List<ConditionResult> conditions,
    BigDecimal score,
    String explanation,
    Optional<TradePlan> plan) {

  static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

  public StrategyEvaluation {
    Objects.requireNonNull(strategyRef, "strategyRef");
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(asOf, "asOf");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(explanation, "explanation");
    Objects.requireNonNull(plan, "plan");
    conditions = List.copyOf(conditions);
    var ids = new HashSet<String>();
    for (ConditionResult c : conditions) {
      if (!ids.add(c.id())) {
        throw new IllegalArgumentException("duplicate condition id " + c.id());
      }
    }
    Objects.requireNonNull(score, "score");
    if (score.signum() < 0 || score.compareTo(MAX_SCORE) > 0) {
      throw new IllegalArgumentException("score outside 0 to 100: " + score);
    }
    score = score.setScale(2, RoundingMode.HALF_UP);
    if (status != EvaluationStatus.PASSED && score.signum() != 0) {
      throw new IllegalArgumentException("only a PASSED evaluation has a score");
    }
    if ((status == EvaluationStatus.PASSED) != plan.isPresent()) {
      throw new IllegalArgumentException("a plan is present exactly when PASSED");
    }
  }

  /** Required conditions that failed or were unknown: why the share did not qualify. */
  public List<ConditionResult> blockers() {
    return conditions.stream().filter(c -> c.required() && c.outcome() != Outcome.PASS).toList();
  }
}
