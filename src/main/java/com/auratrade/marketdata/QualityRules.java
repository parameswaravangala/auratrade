package com.auratrade.marketdata;

import java.math.BigDecimal;

/**
 * Thresholds for the warning-level checks. Starting values: tune them from real data.
 *
 * @param maxDailyMove largest plausible close-to-close move for DAY candles (0.20 means 20 percent)
 * @param maxIntradayMove the same for intraday candles
 * @param staleRun number of consecutive flat, zero-volume DAY candles that triggers STALE_PRICE
 */
public record QualityRules(BigDecimal maxDailyMove, BigDecimal maxIntradayMove, int staleRun) {

  public static QualityRules defaults() {
    return new QualityRules(new BigDecimal("0.20"), new BigDecimal("0.05"), 5);
  }
}
