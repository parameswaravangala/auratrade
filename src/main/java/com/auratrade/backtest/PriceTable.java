package com.auratrade.backtest;

import com.auratrade.marketdata.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * One instrument's daily prices in compact, exact form: prices as whole units of INR 0.0001 in long
 * arrays (the candle table stores four decimals), dates as epoch days. About 50 bytes per day
 * instead of several hundred for Candle objects, so a simulation can hold thousands of shares.
 */
final class PriceTable {

  private static final int SCALE = 4;

  final String symbol;
  private final int[] days;
  private final long[] open;
  private final long[] high;
  private final long[] low;
  private final long[] close;

  PriceTable(String symbol, List<Candle> candles) {
    this.symbol = symbol;
    int n = candles.size();
    days = new int[n];
    open = new long[n];
    high = new long[n];
    low = new long[n];
    close = new long[n];
    for (int i = 0; i < n; i++) {
      Candle c = candles.get(i);
      days[i] = (int) c.tradingDate().toEpochDay();
      open[i] = units(c.open());
      high[i] = units(c.high());
      low[i] = units(c.low());
      close[i] = units(c.close());
    }
  }

  private static long units(BigDecimal price) {
    return price.setScale(SCALE).unscaledValue().longValueExact();
  }

  private static BigDecimal price(long units) {
    return BigDecimal.valueOf(units, SCALE);
  }

  /** Index of the bar on {@code date}, or -1 if there is none (holiday, suspension). */
  int indexOf(LocalDate date) {
    int i = Arrays.binarySearch(days, (int) date.toEpochDay());
    return i >= 0 ? i : -1;
  }

  /** Index of the last bar on or before {@code date}, or -1. */
  int indexAtOrBefore(LocalDate date) {
    int i = Arrays.binarySearch(days, (int) date.toEpochDay());
    return i >= 0 ? i : -i - 2;
  }

  int size() {
    return days.length;
  }

  LocalDate date(int i) {
    return LocalDate.ofEpochDay(days[i]);
  }

  BigDecimal open(int i) {
    return price(open[i]);
  }

  BigDecimal high(int i) {
    return price(high[i]);
  }

  BigDecimal low(int i) {
    return price(low[i]);
  }

  BigDecimal close(int i) {
    return price(close[i]);
  }
}
