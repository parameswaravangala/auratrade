package com.auratrade.screener;

import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A rule-based stock screen (plain Java, no LLM). It looks at one share's recent daily candles and
 * either matches, with a score used for ranking and the numbers that justify it, or does not. The
 * shared guards (fresh data, enough history, liquidity, split-sized moves) run before a screen sees
 * a share, in {@link ScreenerService}.
 */
public interface Screen {

  /** Stable id used in URLs, for example 52w-high-breakout. */
  String name();

  String description();

  /** Daily candles the screen needs, including today. */
  int lookback();

  /** Whether the screen needs the benchmark index series. */
  default boolean needsBenchmark() {
    return false;
  }

  Optional<Hit> evaluate(Series share, Context context);

  /** What a screen can see besides the share. */
  record Context(LocalDate asOf, Optional<Series> benchmark) {}

  /**
   * A match. {@code score} ranks matches (higher first); {@code metrics} are the numbers behind it,
   * rounded for display; {@code reason} is one plain sentence.
   */
  record Hit(
      String symbol,
      String name,
      BigDecimal score,
      Map<String, BigDecimal> metrics,
      String reason) {

    static Hit of(Series s, BigDecimal score, Map<String, BigDecimal> metrics, String reason) {
      Map<String, BigDecimal> rounded = new LinkedHashMap<>();
      metrics.forEach(
          (k, v) -> rounded.put(k, v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()));
      return new Hit(
          s.symbol(), s.name(), score.setScale(4, RoundingMode.HALF_UP), rounded, reason);
    }
  }
}
