package com.auratrade.marketdata;

import com.auratrade.platform.jobs.ScheduledJob;
import com.auratrade.platform.time.MarketZones;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Daily end-of-day candles for every active company share in the configured series (ADR 0017,
 * 0025). Plans with {@link EodWindowPlanner}, so missed days are caught up.
 *
 * <p>Where the provider has quote snapshots ({@link SessionBarProvider}), the window's last session
 * comes from them: a handful of calls for the whole universe, and the bar names its own session, so
 * a run after midnight still gets the right day. Earlier days of a catch-up window come from
 * history, one request per share. Snapshot bars are provisional, so a successful run asks for the
 * previous session to be re-read from history in the background ({@link Corrections}). Without
 * snapshots, one history request per share covers the window and the day before it.
 *
 * <p>Failure handling: benchmarks come first and are required. An auth or rate-limit failure fails
 * the run (every share would fail; the next trigger retries). A share whose candles fail validation
 * or whose request fails counts as a failed item (PARTIAL run). If fewer than {@code minCoverage}
 * of the shares have a candle for the last day, the data is probably not published yet, so the run
 * fails and the next trigger retries.
 */
public class EodCandleJob implements ScheduledJob {

  public static final String NAME = "eod-candles";
  private static final int MAX_LISTED_FAILURES = 20;

  /**
   * Re-reads a past session from the provider's history in the background, replacing provisional
   * snapshot bars. Implemented with a backfill request (ADR 0018).
   */
  @FunctionalInterface
  public interface Corrections {
    /**
     * @return a short note for the run detail, for example the backfill id or why none was made
     */
    String request(LocalDate session);
  }

  /**
   * @param cutoff IST time after which today's candles are expected to be available
   * @param maxCatchUp most trading days one run catches up; older gaps need a backfill
   * @param series share series to load, for example EQ
   * @param maxInstruments 0 for all, or a limit for short development runs
   * @param minCoverage share of instruments that must have a candle for the last day (default 0.9;
   *     a run that crossed midnight IST on 2026-11-26 loaded 61% and passed the old 0.5)
   * @param benchmarks indices loaded alongside the shares, for example NIFTY (relative strength)
   */
  public record Settings(
      LocalTime cutoff,
      int maxCatchUp,
      List<String> series,
      int maxInstruments,
      double minCoverage,
      List<String> benchmarks) {}

  private final CandleIngestionService ingestion;
  private final TradingCalendar calendar;
  private final Clock clock;
  private final Settings settings;
  private final Corrections corrections;

  EodCandleJob(
      CandleIngestionService ingestion,
      TradingCalendar calendar,
      Clock clock,
      Settings settings,
      Corrections corrections) {
    this.ingestion = ingestion;
    this.calendar = calendar;
    this.clock = clock;
    this.settings = settings;
    this.corrections = corrections;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<JobWindow> plan(Optional<LocalDate> lastCompleted) {
    return EodWindowPlanner.plan(
        lastCompleted,
        clock.instant().atZone(MarketZones.IST),
        calendar,
        settings.cutoff(),
        settings.maxCatchUp());
  }

  @Override
  public JobOutcome execute(JobWindow window) {
    LocalDate last = window.to();
    List<CandleRepository.Target> universe =
        ingestion.universe(settings.series(), settings.maxInstruments());
    if (universe.isEmpty()) {
      throw new IllegalStateException(
          "no active shares with a " + ingestion.providerName() + " key; load instruments first");
    }
    // Benchmarks first and required: screens such as relative strength cannot run without them.
    List<CandleRepository.Target> indices =
        settings.benchmarks().stream().map(ingestion::indexTarget).toList();

    boolean snapshots = ingestion.supportsSessionBars();
    if (snapshots && !indices.isEmpty()) {
      var probe = ingestion.ingestSessionBars(indices, last);
      boolean nextSessionOpened =
          probe.values().stream()
              .anyMatch(r -> r.newestDate() != null && r.newestDate().isAfter(last));
      if (nextSessionOpened) {
        snapshots = false; // a catch-up after the next open: only history has the session now
      } else {
        probe.forEach((index, r) -> requireDay(index, r, last));
      }
    }
    // History covers the day before the window too: without snapshots, a same-day bar can be
    // provisional and the next evening replaces it with the final historical bar.
    LocalDate historyFrom = calendar.previousTradingDay(window.from());
    LocalDate historyTo = snapshots ? calendar.previousTradingDay(last) : last;
    boolean needHistory = !snapshots || window.from().isBefore(last);

    for (CandleRepository.Target index : indices) {
      if (needHistory) {
        var r = ingestion.ingest(index, Interval.DAY, historyFrom, historyTo);
        if (snapshots) {
          requireClean(index, r, last);
        } else {
          requireDay(index, r, last); // history reached the last day
        }
      }
    }
    int withLastDay = 0;
    List<String> failed = new ArrayList<>();
    java.util.Set<CandleRepository.Target> failedShares = new java.util.HashSet<>();
    if (needHistory) {
      for (CandleRepository.Target share : universe) {
        try {
          var result = ingestion.ingest(share, Interval.DAY, historyFrom, historyTo);
          if (result.hasErrors()) {
            failed.add(share.tradingSymbol() + " (validation)");
            failedShares.add(share);
          } else if (!snapshots && last.equals(result.newestDate())) {
            withLastDay++;
          }
        } catch (ProviderException.Auth | ProviderException.RateLimited e) {
          throw e;
        } catch (RuntimeException e) {
          failed.add(share.tradingSymbol() + " (" + e.getClass().getSimpleName() + ")");
          failedShares.add(share);
        }
      }
    }
    if (snapshots) {
      for (var e : ingestion.ingestSessionBars(universe, last).entrySet()) {
        if (e.getValue().hasErrors()) {
          if (!failedShares.contains(e.getKey())) {
            failed.add(e.getKey().tradingSymbol() + " (validation)");
            failedShares.add(e.getKey());
          }
        } else if (last.equals(e.getValue().newestDate())) {
          withLastDay++;
        }
      }
    }
    int ok = universe.size() - failedShares.size();

    double coverage = (double) withLastDay / universe.size();
    if (coverage < settings.minCoverage()) {
      throw new IllegalStateException(
          String.format(
              "only %d of %d shares have a candle for %s; data probably not published yet",
              withLastDay, universe.size(), last));
    }
    String correction =
        snapshots ? "; " + corrections.request(calendar.previousTradingDay(last)) : "";
    String detail =
        String.format(
            "%d shares, %d with a candle for %s%s, %d failed%s%s",
            universe.size(),
            withLastDay,
            last,
            snapshots ? " (quotes)" : "",
            failed.size(),
            failed.isEmpty()
                ? ""
                : ": "
                    + String.join(
                        ", ", failed.subList(0, Math.min(failed.size(), MAX_LISTED_FAILURES)))
                    + (failed.size() > MAX_LISTED_FAILURES ? ", ..." : ""),
            correction);
    return new JobOutcome(ok, failed.size(), detail);
  }

  private static void requireClean(
      CandleRepository.Target index, CandleIngestionService.Result r, LocalDate last) {
    if (r.hasErrors()) {
      throw new IllegalStateException(
          "benchmark " + index.tradingSymbol() + " failed validation for " + last);
    }
  }

  private static void requireDay(
      CandleRepository.Target index, CandleIngestionService.Result r, LocalDate last) {
    requireClean(index, r, last);
    if (!last.equals(r.newestDate())) {
      throw new IllegalStateException(
          "benchmark " + index.tradingSymbol() + " has no candle for " + last + " yet");
    }
  }
}
