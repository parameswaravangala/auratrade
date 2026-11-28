package com.auratrade.backtest;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.Strategy;
import com.auratrade.strategy.StrategyContext;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.StrategyEvaluator;
import com.auratrade.strategy.StrategyVersion;
import com.auratrade.strategy.TradePlan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Replays one strategy version over one share's history (plan slice P2). For every session in
 * {@code dates}, it evaluates the strategy on the window of candles ending on that session, so no
 * later bar is ever visible (no look-ahead). Each PASSED evaluation becomes a signal, decided at
 * that session's close. Mirrors {@link ScreenReplay}, which does the same for screens.
 */
final class StrategyReplay {

  /** The share guards: the screener's {@code guard(series, asOf, needed)}. */
  @FunctionalInterface
  interface Guard {
    String check(Series window, LocalDate asOf, int needed);
  }

  private StrategyReplay() {}

  /**
   * @param benchmark the benchmark series, or null when the strategy does not use one
   * @param benchIndex {@link #indexOf} of the benchmark, built once for all shares
   */
  static List<PlanPortfolioSimulator.Signal> signals(
      Series share,
      Series benchmark,
      Map<LocalDate, Integer> benchIndex,
      StrategyVersion version,
      Strategy strategy,
      Set<LocalDate> dates,
      Guard guard) {
    int lookback = strategy.lookback(version.parameters());
    List<Candle> candles = share.candles();
    List<PlanPortfolioSimulator.Signal> out = new ArrayList<>();
    for (int i = lookback - 1; i < candles.size(); i++) {
      LocalDate date = candles.get(i).tradingDate();
      if (!dates.contains(date)) {
        continue;
      }
      Series window =
          new Series(
              share.instrumentId(),
              share.symbol(),
              share.name(),
              candles.subList(i - lookback + 1, i + 1));
      if (guard.check(window, date, lookback) != null) {
        continue;
      }
      Optional<Series> bench =
          benchmark == null
              ? Optional.empty()
              : benchmarkWindow(benchmark, benchIndex, date, lookback);
      StrategyEvaluation e =
          StrategyEvaluator.evaluate(strategy, version, window, new StrategyContext(date, bench));
      if (e.status() == EvaluationStatus.PASSED) {
        out.add(
            new PlanPortfolioSimulator.Signal(
                date, share.instrumentId(), share.symbol(), e.plan().orElseThrow(), e.score()));
      }
    }
    return out;
  }

  /** The same plan with a different holding limit: the only plan parameter the grid varies here. */
  static TradePlan withMaxHold(TradePlan p, int maxHoldSessions) {
    return new TradePlan(
        p.entry(),
        p.stop(),
        p.stopReason(),
        p.targets(),
        p.trailing(),
        p.invalidation(),
        maxHoldSessions);
  }

  static Map<LocalDate, Integer> indexOf(Series s) {
    Map<LocalDate, Integer> idx = new java.util.HashMap<>();
    List<Candle> c = s.candles();
    for (int i = 0; i < c.size(); i++) {
      idx.put(c.get(i).tradingDate(), i);
    }
    return idx;
  }

  private static Optional<Series> benchmarkWindow(
      Series benchmark, Map<LocalDate, Integer> benchIndex, LocalDate date, int lookback) {
    Integer i = benchIndex.get(date);
    if (i == null) {
      return Optional.empty();
    }
    List<Candle> c = benchmark.candles();
    return Optional.of(
        new Series(
            benchmark.instrumentId(),
            benchmark.symbol(),
            benchmark.name(),
            c.subList(
                Math.max(
                    0, i - Math.max(lookback, com.auratrade.strategy.MarketRegime.MIN_HISTORY) + 1),
                i + 1)));
  }
}
