package com.auratrade.paperbroker;

import com.auratrade.marketdata.EodCandleJob;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.platform.jobs.JobRunRepository;
import com.auratrade.platform.jobs.ScheduledJob;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Runs paper trading each evening after the EOD candles (ADR 0024). A session is processed only
 * after the EOD job has completed it (SUCCEEDED or PARTIAL), so the screen never runs on a
 * half-loaded day. Owes every trading day after the last completed one up to that point, processed
 * in date order for every due account; a missed evening is caught up and its proposals simply
 * expire, since nobody approved them.
 */
public class PaperTradingJob implements ScheduledJob {

  static final String NAME = "paper-trading";

  private final PaperTradingService paper;
  private final TradingCalendar calendar;
  private final JobRunRepository runs;

  PaperTradingJob(PaperTradingService paper, TradingCalendar calendar, JobRunRepository runs) {
    this.paper = paper;
    this.calendar = calendar;
    this.runs = runs;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<JobWindow> plan(Optional<LocalDate> lastCompleted) {
    Optional<LocalDate> eodDone = runs.lastCompletedWindowEnd(EodCandleJob.NAME);
    if (eodDone.isEmpty()) {
      return Optional.empty(); // no completed EOD run yet: nothing is safe to process
    }
    LocalDate latest = eodDone.get();
    LocalDate from = lastCompleted.map(calendar::nextTradingDay).orElse(latest);
    if (from.isAfter(latest)) {
      return Optional.empty();
    }
    return Optional.of(new JobWindow(from, latest));
  }

  @Override
  public JobOutcome execute(JobWindow window) {
    int ok = 0;
    StringBuilder detail = new StringBuilder();
    List<LocalDate> days = calendar.tradingDaysBetween(window.from(), window.to());
    for (LocalDate d : days) {
      for (var account : paper.due(d)) {
        var r = paper.process(account.id(), d);
        ok++;
        detail.append(
            String.format(
                "%s #%d: %d filled, %d unfilled, %d expired, %d exits and %d entries proposed%s. ",
                d,
                r.accountId(),
                r.filled(),
                r.unfilled(),
                r.expired(),
                r.exitsProposed(),
                r.entriesProposed(),
                r.note() == null ? "" : " (" + r.note() + ")"));
      }
    }
    return new JobOutcome(
        ok, 0, detail.isEmpty() ? "no active paper accounts" : detail.toString().trim());
  }
}
