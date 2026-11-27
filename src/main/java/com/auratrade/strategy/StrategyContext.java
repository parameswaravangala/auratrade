package com.auratrade.strategy;

import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What a strategy can see besides the share: the decision session and the benchmark series, both
 * ending at {@code asOf}. Nothing later than {@code asOf} may be passed in (no look-ahead).
 */
public record StrategyContext(LocalDate asOf, Optional<Series> benchmark) {

  public StrategyContext {
    Objects.requireNonNull(asOf, "asOf");
    Objects.requireNonNull(benchmark, "benchmark");
    benchmark.ifPresent(b -> {
      if (b.lastDate() != null && b.lastDate().isAfter(asOf)) {
        throw new IllegalArgumentException("benchmark ends after asOf " + asOf);
      }
    });
  }
}
