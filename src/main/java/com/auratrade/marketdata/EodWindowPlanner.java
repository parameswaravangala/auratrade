package com.auratrade.marketdata;

import com.auratrade.platform.jobs.ScheduledJob.JobWindow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Works out which trading days of end-of-day candles are still owed (ADR 0017). Pure: no I/O.
 *
 * <p>The latest complete day is today if it is a trading day and the IST time is past the cutoff,
 * otherwise the previous trading day. Everything after the last completed window up to that day is
 * owed, capped at {@code maxCatchUp} trading days; an older gap is left to a deliberate backfill
 * and noted on the window so the runner alerts. Fails closed: an uncovered calendar year throws.
 */
final class EodWindowPlanner {

  private EodWindowPlanner() {}

  static Optional<JobWindow> plan(
      Optional<LocalDate> lastCompleted,
      ZonedDateTime nowIst,
      TradingCalendar calendar,
      LocalTime cutoff,
      int maxCatchUp) {
    LocalDate today = nowIst.toLocalDate();
    LocalDate latest =
        calendar.isTradingDay(today) && !nowIst.toLocalTime().isBefore(cutoff)
            ? today
            : calendar.previousTradingDay(today);
    if (lastCompleted.isPresent() && !lastCompleted.get().isBefore(latest)) {
      return Optional.empty();
    }
    LocalDate from = lastCompleted.map(calendar::nextTradingDay).orElse(latest);
    List<LocalDate> owed = calendar.tradingDaysBetween(from, latest);
    if (owed.isEmpty()) {
      return Optional.empty();
    }
    if (owed.size() > maxCatchUp) {
      LocalDate cappedFrom = owed.get(owed.size() - maxCatchUp);
      return Optional.of(
          new JobWindow(
              cappedFrom,
              latest,
              "catch-up capped at "
                  + maxCatchUp
                  + " trading days; "
                  + from
                  + " to "
                  + calendar.previousTradingDay(cappedFrom)
                  + " needs a backfill"));
    }
    return Optional.of(new JobWindow(owed.get(0), latest));
  }
}
