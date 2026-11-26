package com.auratrade.llmgateway;

import com.auratrade.platform.time.MarketZones;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Day and month boundaries for budgeting. They follow IST, not UTC. */
final class Periods {

  private Periods() {}

  private static ZonedDateTime today(Clock clock) {
    return ZonedDateTime.now(clock.withZone(MarketZones.IST)).truncatedTo(ChronoUnit.DAYS);
  }

  static OffsetDateTime dayStart(Clock clock) {
    return today(clock).toOffsetDateTime();
  }

  static OffsetDateTime monthStart(Clock clock) {
    return today(clock).withDayOfMonth(1).toOffsetDateTime();
  }

  static YearMonth month(Clock clock) {
    return YearMonth.from(today(clock));
  }
}
