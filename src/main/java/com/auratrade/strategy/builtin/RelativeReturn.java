package com.auratrade.strategy.builtin;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.StrategyContext;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The share's return over the last {@code sessions} sessions minus the benchmark's return over the
 * same dates, matched by date (not by position, so a missing benchmark bar makes it unknown rather
 * than misaligned). Empty if the share or benchmark is too short, the benchmark has no bar on
 * either date, or the start price is not positive.
 */
final class RelativeReturn {

  private static final MathContext MC = MathContext.DECIMAL64;

  private RelativeReturn() {}

  static Optional<BigDecimal> excess(Series share, StrategyContext context, int sessions) {
    if (context.benchmark().isEmpty()) {
      return Optional.empty();
    }
    List<Candle> c = share.candles();
    int n = c.size();
    if (n <= sessions) {
      return Optional.empty();
    }
    Candle start = c.get(n - 1 - sessions);
    Candle end = c.get(n - 1);
    Map<java.time.LocalDate, BigDecimal> bench = new HashMap<>();
    for (Candle k : context.benchmark().get().candles()) {
      bench.put(k.tradingDate(), k.close());
    }
    BigDecimal benchStart = bench.get(start.tradingDate());
    BigDecimal benchEnd = bench.get(context.asOf());
    if (benchStart == null
        || benchEnd == null
        || benchStart.signum() <= 0
        || start.close().signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal shareReturn = end.close().divide(start.close(), MC).subtract(BigDecimal.ONE);
    BigDecimal benchReturn = benchEnd.divide(benchStart, MC).subtract(BigDecimal.ONE);
    return Optional.of(shareReturn.subtract(benchReturn));
  }
}
