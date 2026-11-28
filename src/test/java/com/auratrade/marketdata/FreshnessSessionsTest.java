package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.platform.time.MarketZones;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Freshness is counted in trading sessions, never calendar days. Fixture calendar (2026 only, not
 * an official NSE list): Monday 26 January is a holiday.
 */
class FreshnessSessionsTest {

  private final TradingCalendar calendar = TestCalendars.fixture();
  private static final LocalTime CUTOFF = LocalTime.of(19, 0);

  private static Instant ist(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(MarketZones.IST).toInstant();
  }

  private LocalDate expected(String istDateTime) {
    return DataFreshnessService.expectedSession(calendar, ist(istDateTime), CUTOFF);
  }

  private static LocalDate d(String date) {
    return LocalDate.parse(date);
  }

  @Test
  void mondayMorningAfterAFridaySessionIsNotBehind() {
    LocalDate expected = expected("2026-02-02T08:00"); // Monday, before the cutoff

    assertThat(expected).isEqualTo(d("2026-01-30")); // Friday
    assertThat(DataFreshnessService.sessionsBehind(calendar, d("2026-01-30"), expected)).isZero();
  }

  @Test
  void aHolidayIsSkipped() {
    // Friday 23rd to Tuesday 27th: Monday 26th is a holiday, so one session behind, not two.
    assertThat(DataFreshnessService.sessionsBehind(calendar, d("2026-01-23"), d("2026-01-27")))
        .isEqualTo(1);
    assertThat(expected("2026-01-26T20:00")).isEqualTo(d("2026-01-23"));
  }

  @Test
  void theCutoffDecidesWhetherTodayIsExpected() {
    assertThat(expected("2026-01-23T18:59")).isEqualTo(d("2026-01-22"));
    assertThat(expected("2026-01-23T19:00")).isEqualTo(d("2026-01-23"));
  }

  @Test
  void aWeekendExpectsFriday() {
    assertThat(expected("2026-01-24T12:00")).isEqualTo(d("2026-01-23"));
  }

  @Test
  void aBarNewerThanExpectedIsNotBehind() {
    assertThat(DataFreshnessService.sessionsBehind(calendar, d("2026-01-23"), d("2026-01-22")))
        .isZero();
  }

  @Test
  void aGapIntoAnUncoveredYearIsNull() {
    assertThat(DataFreshnessService.sessionsBehind(calendar, d("2025-12-30"), d("2026-01-05")))
        .isNull();
  }

  @Test
  void anExpectedSessionInAnUncoveredYearFailsClosed() {
    assertThatThrownBy(() -> expected("2027-01-05T20:00"))
        .isInstanceOf(CalendarNotCoveredException.class);
  }
}
