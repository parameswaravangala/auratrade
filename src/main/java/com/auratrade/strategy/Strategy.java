package com.auratrade.strategy;

import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A code-based strategy (ADR 0038): the rules are Java, the thresholds are {@link Parameters}.
 * Implementations are stateless and deterministic: the same series, context and parameters always
 * give the same assessment. Callers go through {@link StrategyEvaluator}, which applies the rules
 * every strategy shares; a strategy only measures its conditions and proposes a plan.
 */
public interface Strategy {

  Pattern ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

  /** Stable id, lower-case words joined by hyphens, for example {@code momentum-breakout}. */
  String id();

  /** Human-readable name, for example "Momentum breakout". */
  String name();

  /** The investment hypothesis and rules in a few plain sentences. */
  String description();

  /** Declared parameters. Return the same instance on every call. */
  ParameterSchema parameterSchema();

  /** Data the strategy reads; conditions on unavailable data come back UNKNOWN. */
  Set<DataRequirement> requires();

  /** Daily candles needed, including the decision session, for these parameters. */
  int lookback(Parameters parameters);

  /**
   * Measures every condition for one share on {@code context.asOf()}. The evaluator has already
   * checked that the series ends on that session and holds at least {@link #lookback} candles.
   */
  Assessment assess(Series share, StrategyContext context, Parameters parameters);

  /**
   * What a strategy reports for one share and session.
   *
   * @param quality 0 to 100, how strong the setup is when every required condition passes; the
   *     evaluator ignores it otherwise
   * @param plan required when every required condition passes, ignored otherwise
   */
  record Assessment(
      List<ConditionResult> conditions,
      BigDecimal quality,
      String explanation,
      Optional<TradePlan> plan) {
    public Assessment {
      conditions = List.copyOf(conditions);
      Objects.requireNonNull(quality, "quality");
      Objects.requireNonNull(explanation, "explanation");
      Objects.requireNonNull(plan, "plan");
    }
  }
}
