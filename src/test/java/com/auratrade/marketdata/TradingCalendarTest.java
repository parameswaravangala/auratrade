package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.platform.time.MarketZones;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradingCalendarTest {

  private final TradingCalendar calendar = TestCalendars.fixture();

  private static Instant ist(String dateTime) {
    return LocalDateTime.parse(dateTime).atZone(MarketZones.IST).toInstant();
  }

  @Test
  void weekdaysTradeUnlessListedAsHolidays() {
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-01-23"))).isTrue(); // Friday
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-01-26"))).isFalse(); // fixture holiday
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-10-02"))).isFalse();
  }

  @Test
  void weekendsAreClosedExceptSpecialSessions() {
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-01-24"))).isFalse(); // Saturday
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-01-25"))).isFalse(); // Sunday
    assertThat(calendar.isTradingDay(LocalDate.parse("2026-03-14"))).isTrue(); // special Saturday
    assertThat(calendar.isSpecialSession(LocalDate.parse("2026-03-14"))).isTrue();
  }

  @Test
  void nextAndPreviousSkipWeekendsAndHolidays() {
    assertThat(calendar.nextTradingDay(LocalDate.parse("2026-01-23")))
        .isEqualTo(LocalDate.parse("2026-01-27"));
    assertThat(calendar.previousTradingDay(LocalDate.parse("2026-01-27")))
        .isEqualTo(LocalDate.parse("2026-01-23"));
  }

  @Test
  void listsTradingDaysBetweenTwoDates() {
    List<LocalDate> days =
        calendar.tradingDaysBetween(LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-28"));

    assertThat(days)
        .containsExactly(
            LocalDate.parse("2026-01-23"),
            LocalDate.parse("2026-01-27"),
            LocalDate.parse("2026-01-28"));
    assertThat(
            calendar.tradingDaysBetween(
                LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-01")))
        .isEmpty();
  }

  @Test
  void sessionHoursAreNineFifteenToThirtyIst() {
    assertThat(calendar.isWithinSession(ist("2026-01-23T09:15:00"))).isTrue();
    assertThat(calendar.isWithinSession(ist("2026-01-23T09:14:00"))).isFalse();
    assertThat(calendar.isWithinSession(ist("2026-01-23T15:29:00"))).isTrue();
    assertThat(calendar.isWithinSession(ist("2026-01-23T15:30:00"))).isFalse();
    assertThat(calendar.isWithinSession(ist("2026-01-26T10:00:00"))).isFalse(); // holiday
    assertThat(calendar.isWithinSession(ist("2026-03-14T18:00:00"))).isTrue(); // special: any time
  }

  @Test
  void unknownYearsFailClosedInsteadOfGuessing() {
    assertThatThrownBy(() -> calendar.isTradingDay(LocalDate.parse("2027-01-04")))
        .isInstanceOf(CalendarNotCoveredException.class)
        .hasMessageContaining("2027");
    assertThat(calendar.covers(2026)).isTrue();
    assertThat(calendar.covers(2027)).isFalse();
  }

  @Test
  void emptyTemplateCoversNothing() throws Exception {
    var empty = TradingCalendar.fromCsv(new StringReader("# only comments\n\n"));

    assertThatThrownBy(() -> empty.isTradingDay(LocalDate.parse("2026-01-23")))
        .isInstanceOf(CalendarNotCoveredException.class);
  }

  @Test
  void csvErrorsNameTheLine() {
    assertThatThrownBy(
            () -> TradingCalendar.fromCsv(new StringReader("COVERS,2026\nHOLIDAY,not-a-date\n")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 2");
    assertThatThrownBy(
            () -> TradingCalendar.fromCsv(new StringReader("COVERS,2026\nPARTY,2026-01-01\n")))
        .hasMessageContaining("unknown row type");
  }

  @Test
  void entriesInAnUndeclaredYearAreRejected() {
    assertThatThrownBy(() -> TradingCalendar.fromCsv(new StringReader("HOLIDAY,2027-01-26,x\n")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("COVERS,2027");
  }
}
