package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the real calendar file (not the test fixture). The dates below were reconciled on
 * 2026-11-26 against NSE's daily bhavcopy archive and the NSE circulars named in the file, so a
 * later edit cannot silently undo them.
 */
class NseCalendarFileTest {

  private static TradingCalendar calendar;

  @BeforeAll
  static void load() throws Exception {
    try (var reader =
        new InputStreamReader(
            NseCalendarFileTest.class.getResourceAsStream("/calendar/nse-calendar.csv"),
            StandardCharsets.UTF_8)) {
      calendar = TradingCalendar.fromCsv(reader);
    }
  }

  private static boolean trades(String date) {
    return calendar.isTradingDay(LocalDate.parse(date));
  }

  @Test
  void coversTheReconciledYears() {
    assertThat(calendar.covers(2024)).isTrue();
    assertThat(calendar.covers(2025)).isTrue();
    assertThat(calendar.covers(2026)).isTrue();
  }

  @Test
  void amendmentsMissingFromTheAnnualCircularsAreIncluded() {
    assertThat(trades("2024-01-20")).as("Saturday session").isTrue();
    assertThat(trades("2024-01-22")).as("declared public holiday").isFalse();
    assertThat(trades("2024-03-02")).as("DR live session").isTrue();
    assertThat(trades("2024-05-18")).as("DR live session").isTrue();
    assertThat(trades("2024-05-20")).as("election holiday").isFalse();
    assertThat(trades("2024-11-20")).as("assembly election holiday").isFalse();
    assertThat(trades("2025-02-01")).as("Budget Saturday").isTrue();
    assertThat(trades("2026-01-15")).as("municipal election holiday").isFalse();
    assertThat(trades("2026-02-01")).as("Budget Sunday").isTrue();
  }

  @Test
  void muhuratSessionsTradeEvenOnAHoliday() {
    assertThat(trades("2024-11-01")).isTrue();
    assertThat(trades("2025-10-21")).isTrue();
    assertThat(trades("2026-11-08")).as("Sunday Muhurat").isTrue();
    assertThat(trades("2025-10-22")).as("Balipratipada").isFalse();
  }

  @Test
  void tradingDayCountsMatchTheArchive() {
    // Days with an NSE CM bhavcopy file, counted from the archive on 2026-11-26.
    assertThat(
            calendar
                .tradingDaysBetween(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"))
                .size())
        .isEqualTo(ARCHIVE_DAYS_2024);
    assertThat(
            calendar
                .tradingDaysBetween(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"))
                .size())
        .isEqualTo(ARCHIVE_DAYS_2025);
  }

  private static final int ARCHIVE_DAYS_2024 = 249;
  private static final int ARCHIVE_DAYS_2025 = 249;
}
