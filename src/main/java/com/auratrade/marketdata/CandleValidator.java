package com.auratrade.marketdata;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a series of candles (one instrument, one interval, oldest first) before it is used.
 *
 * <p>ERROR codes: NON_POSITIVE_PRICE, OHLC_INCONSISTENT, NEGATIVE_VOLUME, DUPLICATE_TIMESTAMP,
 * OUT_OF_ORDER, NOT_TRADING_DAY, OUTSIDE_SESSION, DAY_NOT_AT_MIDNIGHT. WARNING codes:
 * MISSING_TRADING_DAY (DAY only), PLACEHOLDER_BAR_DROPPED (from {@link #dropPlaceholderBars}),
 * EXTREME_MOVE, STALE_PRICE (DAY only).
 *
 * <p>An EXTREME_MOVE is expected around splits and bonuses until corporate-action adjustment exists
 * (see the backlog), so it is a warning, not an error. Throws CalendarNotCoveredException if the
 * data touches a year the calendar does not cover.
 */
public final class CandleValidator {

  private final TradingCalendar calendar;
  private final QualityRules rules;

  public CandleValidator(TradingCalendar calendar, QualityRules rules) {
    this.calendar = calendar;
    this.rules = rules;
  }

  /** Candles kept after removing provider placeholders, and a warning for each one removed. */
  public record Cleaned(List<Candle> kept, List<QualityIssue> dropped) {}

  /**
   * Removes daily placeholder bars: a bar on a day the calendar says was closed, with zero volume
   * and open = high = low = close. Such a bar cannot be a trade; Upstox returns them on some closed
   * days (seen on Saturday 2025-04-26). Each removal is a PLACEHOLDER_BAR_DROPPED warning. A bar on
   * a closed day that has volume is kept, so {@link #validate} still reports NOT_TRADING_DAY.
   */
  public Cleaned dropPlaceholderBars(Interval interval, List<Candle> candles) {
    if (interval != Interval.DAY) {
      return new Cleaned(candles, List.of());
    }
    List<Candle> kept = new ArrayList<>(candles.size());
    List<QualityIssue> dropped = new ArrayList<>();
    for (Candle c : candles) {
      if (isFlatAndEmpty(c) && !calendar.isTradingDay(c.tradingDate())) {
        dropped.add(
            warning(
                "PLACEHOLDER_BAR_DROPPED",
                c.ts(),
                "flat zero-volume bar on closed day " + c.tradingDate() + " removed"));
      } else {
        kept.add(c);
      }
    }
    return new Cleaned(List.copyOf(kept), List.copyOf(dropped));
  }

  public QualityReport validate(Interval interval, List<Candle> candles) {
    List<QualityIssue> issues = new ArrayList<>();
    Candle prev = null;
    int flatRun = 0;

    for (Candle c : candles) {
      checkPrices(c, issues);
      if (c.volume() < 0) {
        issues.add(error("NEGATIVE_VOLUME", c.ts(), "volume " + c.volume()));
      }

      LocalDate date = c.tradingDate();
      if (interval == Interval.DAY
          && !c.ts()
              .equals(date.atStartOfDay(com.auratrade.platform.time.MarketZones.IST).toInstant())) {
        issues.add(
            error("DAY_NOT_AT_MIDNIGHT", c.ts(), "daily candle not stamped at IST midnight"));
      }
      if (!calendar.isTradingDay(date)) {
        issues.add(error("NOT_TRADING_DAY", c.ts(), date + " is not a trading day"));
      } else if (interval.isIntraday() && !calendar.isWithinSession(c.ts())) {
        issues.add(error("OUTSIDE_SESSION", c.ts(), "candle outside 09:15-15:30 IST"));
      }

      if (prev != null) {
        int order = c.ts().compareTo(prev.ts());
        if (order == 0) {
          issues.add(error("DUPLICATE_TIMESTAMP", c.ts(), "same timestamp as previous candle"));
        } else if (order < 0) {
          issues.add(error("OUT_OF_ORDER", c.ts(), "earlier than the previous candle"));
        } else {
          if (interval == Interval.DAY) {
            checkMissingDays(prev, c, issues);
          }
          checkMove(interval, prev, c, issues);
        }
      }

      if (interval == Interval.DAY) {
        flatRun = isFlatAndEmpty(c) ? flatRun + 1 : 0;
        if (flatRun == rules.staleRun()) {
          issues.add(
              warning(
                  "STALE_PRICE",
                  c.ts(),
                  flatRun + " consecutive flat candles with no volume (suspended or bad feed?)"));
        }
      }
      prev = c;
    }
    return new QualityReport(issues);
  }

  private void checkPrices(Candle c, List<QualityIssue> issues) {
    if (c.open().signum() <= 0
        || c.high().signum() <= 0
        || c.low().signum() <= 0
        || c.close().signum() <= 0) {
      issues.add(error("NON_POSITIVE_PRICE", c.ts(), "a price is zero or negative"));
      return;
    }
    BigDecimal top = c.open().max(c.close());
    BigDecimal bottom = c.open().min(c.close());
    if (c.high().compareTo(top) < 0
        || c.low().compareTo(bottom) > 0
        || c.high().compareTo(c.low()) < 0) {
      issues.add(error("OHLC_INCONSISTENT", c.ts(), "high/low do not bracket open and close"));
    }
  }

  private void checkMissingDays(Candle prev, Candle c, List<QualityIssue> issues) {
    LocalDate from = prev.tradingDate().plusDays(1);
    LocalDate to = c.tradingDate().minusDays(1);
    int missing = calendar.tradingDaysBetween(from, to).size();
    if (missing > 0) {
      issues.add(
          warning(
              "MISSING_TRADING_DAY",
              c.ts(),
              missing + " trading day(s) missing before " + c.tradingDate()));
    }
  }

  private void checkMove(Interval interval, Candle prev, Candle c, List<QualityIssue> issues) {
    if (prev.close().signum() <= 0 || c.close().signum() <= 0) {
      return;
    }
    BigDecimal move =
        c.close().divide(prev.close(), MathContext.DECIMAL64).subtract(BigDecimal.ONE).abs();
    BigDecimal limit = interval == Interval.DAY ? rules.maxDailyMove() : rules.maxIntradayMove();
    if (move.compareTo(limit) > 0) {
      issues.add(
          warning(
              "EXTREME_MOVE",
              c.ts(),
              "close moved "
                  + move.movePointRight(2).setScale(1, java.math.RoundingMode.HALF_UP)
                  + "%"));
    }
  }

  private static boolean isFlatAndEmpty(Candle c) {
    return c.volume() == 0
        && c.open().compareTo(c.high()) == 0
        && c.open().compareTo(c.low()) == 0
        && c.open().compareTo(c.close()) == 0;
  }

  private static QualityIssue error(String code, Instant ts, String message) {
    return new QualityIssue(Severity.ERROR, code, ts, message);
  }

  private static QualityIssue warning(String code, Instant ts, String message) {
    return new QualityIssue(Severity.WARNING, code, ts, message);
  }
}
