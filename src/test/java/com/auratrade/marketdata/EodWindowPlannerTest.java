package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.platform.jobs.ScheduledJob.JobWindow;
import com.auratrade.platform.time.MarketZones;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Uses the fixture calendar: 2026-01-26 (Mon) is a holiday, 2026-03-14 (Sat) a special session. */
class EodWindowPlannerTest {

  private final TradingCalendar calendar = TestCalendars.fixture();
  private static final LocalTime CUTOFF = LocalTime.of(19, 0);

  private static ZonedDateTime ist(String dateTime) {
    return LocalDateTime.parse(dateTime).atZone(MarketZones.IST);
  }

  private Optional<JobWindow> plan(String last, String now, int maxCatchUp) {
    return EodWindowPlanner.plan(
        Optional.ofNullable(last).map(LocalDate::parse), ist(now), calendar, CUTOFF, maxCatchUp);
  }

  private static JobWindow window(String from, String to) {
    return new JobWindow(LocalDate.parse(from), LocalDate.parse(to));
  }

  @Test
  void afterTheCutoffTodayIsOwed() {
    assertThat(plan("2026-01-22", "2026-01-23T19:30", 30))
        .contains(window("2026-01-23", "2026-01-23"));
  }

  @Test
  void beforeTheCutoffOnlyThePreviousTradingDayIsOwed() {
    assertThat(plan("2026-01-21", "2026-01-23T15:00", 30))
        .contains(window("2026-01-22", "2026-01-22"));
    assertThat(plan("2026-01-22", "2026-01-23T15:00", 30)).isEmpty();
  }

  @Test
  void missedRunsAreCaughtUpOverWeekendsAndHolidays() {
    // Last completed Wed 21st; now Tue 27th evening. Owed: Thu 22, Fri 23, Tue 27 (Mon 26 holiday).
    assertThat(plan("2026-01-21", "2026-01-27T20:00", 30))
        .contains(window("2026-01-22", "2026-01-27"));
  }

  @Test
  void onAWeekendTheLastTradingDayIsOwedOnce() {
    assertThat(plan("2026-01-22", "2026-01-24T10:00", 30))
        .contains(window("2026-01-23", "2026-01-23"));
    assertThat(plan("2026-01-23", "2026-01-25T10:00", 30)).isEmpty();
  }

  @Test
  void aSpecialSessionOnASaturdayIsOwed() {
    assertThat(plan("2026-03-13", "2026-03-14T20:00", 30))
        .contains(window("2026-03-14", "2026-03-14"));
  }

  @Test
  void withNoHistoryOnlyTheLatestDayIsOwed() {
    assertThat(plan(null, "2026-01-27T20:00", 30)).contains(window("2026-01-27", "2026-01-27"));
  }

  @Test
  void longGapsAreCappedAndNoted() {
    var w = plan("2026-01-02", "2026-01-23T20:00", 3).orElseThrow();

    assertThat(w.from()).isEqualTo(LocalDate.parse("2026-01-21"));
    assertThat(w.to()).isEqualTo(LocalDate.parse("2026-01-23"));
    assertThat(w.note()).contains("capped at 3", "2026-01-05 to 2026-01-20", "backfill");
  }

  @Test
  void anUncoveredYearFailsClosed() {
    assertThatThrownBy(() -> plan("2026-12-31", "2027-01-05T20:00", 30))
        .isInstanceOf(CalendarNotCoveredException.class);
  }
}
