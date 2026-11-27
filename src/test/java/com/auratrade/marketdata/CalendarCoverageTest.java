package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.platform.time.MarketZones;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The coverage view over the fixture calendar (2026 only; not an official NSE list). */
class CalendarCoverageTest {

  private final TradingCalendar calendar = TestCalendars.fixture();

  private static Instant ist(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(MarketZones.IST).toInstant();
  }

  @Test
  void beforeTheOpenTodayIsTheNextSessionAndYesterdayTheLastClosed() {
    var c = CalendarCoverage.of(calendar, ist("2026-11-27T08:00")); // a Friday

    assertThat(c.todayIsTradingDay()).isTrue();
    assertThat(c.nextSession()).isEqualTo(LocalDate.parse("2026-11-27"));
    assertThat(c.lastClosedSession()).isEqualTo(LocalDate.parse("2026-11-26"));
  }

  @Test
  void afterTheCloseTodayIsTheLastClosedSession() {
    var c = CalendarCoverage.of(calendar, ist("2026-11-27T16:00"));

    assertThat(c.lastClosedSession()).isEqualTo(LocalDate.parse("2026-11-27"));
    assertThat(c.nextSession()).isEqualTo(LocalDate.parse("2026-11-30")); // Monday
  }

  @Test
  void aSpecialSessionCountsAndHolidaysCarryTheirNames() {
    var c = CalendarCoverage.of(calendar, ist("2026-03-14T12:00")); // a Saturday session

    assertThat(c.todayIsTradingDay()).isTrue();
    var y = c.years().getFirst();
    assertThat(y.year()).isEqualTo(2026);
    assertThat(y.holidays().getFirst())
        .isEqualTo(
            new TradingCalendar.NamedDay(
                LocalDate.parse("2026-01-26"), "Fixture holiday on a Monday"));
    assertThat(y.specialSessions()).hasSize(1);
    // 261 weekdays in 2026, minus 3 weekday holidays, plus 1 Saturday session.
    assertThat(y.tradingDays()).isEqualTo(259);
  }

  @Test
  void neighboursInAnUncoveredYearAreNullNotGuessed() {
    var c = CalendarCoverage.of(calendar, ist("2026-01-01T08:00"));

    assertThat(c.todayCovered()).isTrue();
    assertThat(c.lastClosedSession()).isNull(); // the day before is in 2025
    assertThat(c.nextSession()).isEqualTo(LocalDate.parse("2026-01-01"));
  }

  @Test
  void anUncoveredTodayIsReportedNotThrown() {
    var c = CalendarCoverage.of(calendar, ist("2027-01-04T10:00"));

    assertThat(c.todayCovered()).isFalse();
    assertThat(c.todayIsTradingDay()).isNull();
    assertThat(c.lastClosedSession()).isNull();
    assertThat(c.nextSession()).isNull();
    assertThat(c.coverageWarning()).contains("no data for 2027");
    assertThat(c.coveredYears()).containsExactly(2026);
  }

  @Test
  void fromDecemberAMissingNextYearIsWarned() {
    assertThat(CalendarCoverage.of(calendar, ist("2026-11-30T10:00")).coverageWarning()).isNull();
    var dec = CalendarCoverage.of(calendar, ist("2026-12-01T10:00"));

    assertThat(dec.nextYearCovered()).isFalse();
    assertThat(dec.coverageWarning()).contains("no data for 2027");
  }
}
