package com.auratrade.strategy;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Applies the rules every strategy shares (ADR 0038). Pure and stateless.
 *
 * <ol>
 *   <li>The version must belong to the strategy.
 *   <li>A series with any candle after {@code asOf} is rejected (look-ahead).
 *   <li>A series that does not end on {@code asOf}, or is shorter than the lookback, is
 *       NOT_EVALUATED with one UNKNOWN condition saying why. Missing data is never a pass.
 *   <li>Status comes from the required conditions only ({@link EvaluationStatus}).
 *   <li>Score: the strategy's quality (0 to 100) when PASSED, otherwise 0. A partial pass earns
 *       nothing (plan decision D4).
 *   <li>A PASSED evaluation must come with a trade plan; a strategy that passes without one is a
 *       bug and fails loudly.
 * </ol>
 */
public final class StrategyEvaluator {

  static final String FRESH_DATA = "fresh_data";
  static final String HISTORY = "history";

  private StrategyEvaluator() {}

  public static StrategyEvaluation evaluate(
      Strategy strategy, StrategyVersion version, Series share, StrategyContext context) {
    if (!version.strategyId().equals(strategy.id())) {
      throw new IllegalArgumentException(
          "version " + version.ref() + " does not belong to " + strategy.id());
    }
    LocalDate asOf = context.asOf();
    List<Candle> candles = share.candles();
    if (!candles.isEmpty() && share.lastDate().isAfter(asOf)) {
      throw new IllegalArgumentException(
          share.symbol() + " has candles after " + asOf + " (look-ahead)");
    }
    if (candles.isEmpty() || !share.lastDate().equals(asOf)) {
      return notEvaluated(
          version,
          share,
          asOf,
          ConditionResult.unavailable(
              FRESH_DATA,
              "A daily candle for the decision session",
              true,
              "last candle on " + asOf + ", have " + share.lastDate()));
    }
    int lookback = strategy.lookback(version.parameters());
    if (candles.size() < lookback) {
      return notEvaluated(
          version,
          share,
          asOf,
          ConditionResult.unavailable(
              HISTORY,
              "Enough daily history",
              true,
              "at least " + lookback + " sessions, have " + candles.size()));
    }

    Strategy.Assessment a = strategy.assess(share, context, version.parameters());
    int required = 0;
    int failed = 0;
    int unknown = 0;
    for (ConditionResult c : a.conditions()) {
      if (c.required()) {
        required++;
        if (c.outcome() == Outcome.FAIL) {
          failed++;
        } else if (c.outcome() == Outcome.UNKNOWN) {
          unknown++;
        }
      }
    }
    EvaluationStatus status = EvaluationStatus.of(required, failed, unknown);
    if (status != EvaluationStatus.PASSED) {
      return new StrategyEvaluation(
          version.ref(),
          share.instrumentId(),
          share.symbol(),
          asOf,
          status,
          a.conditions(),
          BigDecimal.ZERO,
          a.explanation(),
          Optional.empty());
    }
    if (a.plan().isEmpty()) {
      throw new IllegalStateException(
          strategy.id() + " passed " + share.symbol() + " on " + asOf + " without a trade plan");
    }
    if (a.quality().signum() < 0 || a.quality().compareTo(StrategyEvaluation.MAX_SCORE) > 0) {
      throw new IllegalStateException(
          strategy.id() + " returned quality " + a.quality() + " outside 0 to 100");
    }
    return new StrategyEvaluation(
        version.ref(),
        share.instrumentId(),
        share.symbol(),
        asOf,
        status,
        a.conditions(),
        a.quality(),
        a.explanation(),
        a.plan());
  }

  private static StrategyEvaluation notEvaluated(
      StrategyVersion version, Series share, LocalDate asOf, ConditionResult why) {
    return new StrategyEvaluation(
        version.ref(),
        share.instrumentId(),
        share.symbol(),
        asOf,
        EvaluationStatus.NOT_EVALUATED,
        List.of(why),
        BigDecimal.ZERO,
        why.description() + ": " + why.threshold(),
        Optional.empty());
  }
}
