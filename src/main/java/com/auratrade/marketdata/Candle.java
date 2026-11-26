package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One OHLCV bar for a single instrument and interval. Prices are BigDecimal, never double. The
 * timestamp is the start of the bar; for DAY bars it is midnight IST of the trading date.
 */
public record Candle(
    Instant ts, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {

  public static Candle daily(
      LocalDate date,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      long volume) {
    return new Candle(
        date.atStartOfDay(MarketZones.IST).toInstant(), open, high, low, close, volume);
  }

  public LocalDate tradingDate() {
    return ts.atZone(MarketZones.IST).toLocalDate();
  }
}
