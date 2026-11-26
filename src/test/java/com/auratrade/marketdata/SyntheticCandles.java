package com.auratrade.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic made-up daily candles for tests and replay. Same seed, same series. Never real
 * exchange data, so it can live in the repository.
 */
final class SyntheticCandles {

  private SyntheticCandles() {}

  /** Valid daily candles on consecutive trading days starting at or after {@code from}. */
  static List<Candle> daily(TradingCalendar calendar, LocalDate from, int days, long seed) {
    Random random = new Random(seed);
    List<Candle> out = new ArrayList<>();
    double previousClose = 1000.0;
    LocalDate date = calendar.isTradingDay(from) ? from : calendar.nextTradingDay(from);

    for (int i = 0; i < days; i++) {
      double open = previousClose * (1 + (random.nextDouble() - 0.5) * 0.01);
      double close = open * (1 + (random.nextDouble() - 0.5) * 0.04);
      double high = Math.max(open, close) * (1 + random.nextDouble() * 0.01);
      double low = Math.min(open, close) * (1 - random.nextDouble() * 0.01);
      long volume = 100_000 + random.nextInt(900_000);
      out.add(Candle.daily(date, price(open), price(high), price(low), price(close), volume));
      previousClose = close;
      date = calendar.nextTradingDay(date);
    }
    return out;
  }

  private static BigDecimal price(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
