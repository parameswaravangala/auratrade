package com.auratrade.marketdata;

import java.time.Duration;

/** Candle sizes we store. DAY candles are stamped at the start of the trading day in IST. */
public enum Interval {
  DAY(Duration.ofDays(1)),
  MINUTE_1(Duration.ofMinutes(1)),
  MINUTE_5(Duration.ofMinutes(5)),
  MINUTE_15(Duration.ofMinutes(15)),
  HOUR_1(Duration.ofHours(1));

  private final Duration duration;

  Interval(Duration duration) {
    this.duration = duration;
  }

  public Duration duration() {
    return duration;
  }

  public boolean isIntraday() {
    return this != DAY;
  }
}
