package com.auratrade.paperbroker;

import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.platform.jobs.ScheduledJob;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Runs paper trading each evening after the EOD candles (ADR 0024). Owes every trading day after
 * the last completed one up to the newest candle date, processed in date order for every due
 * account, so a missed evening is caught up (its proposals simply expire, since nobody approved
 * them).
 */
public class PaperTradingJob implements ScheduledJob {

  static final String NAME = "paper-trading";

  private final PaperTradingService paper;
  private final TradingCalendar calendar;

  PaperTradingJob(PaperTradingService paper, TradingCalendar calendar) {
    this.paper = paper;
    this.calendar = calendar;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<JobWindow> plan(Optional<LocalDate> lastCompleted) {
    LocalDate latest = paper.latestCandleDate();
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
