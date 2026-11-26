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
 * Daily end-of-day candles for every active company share in the configured series (ADR 0017).
 * Plans with {@link EodWindowPlanner}, so missed days are caught up. One provider request per share
 * covers the whole window.
 *
 * <p>Failure handling: an auth or rate-limit failure fails the run (every share would fail; the
 * next trigger retries). A share whose candles fail validation or whose request fails counts as a
 * failed item (PARTIAL run). If most shares have no candle for the window's last day, the data is
 * probably not published yet, so the run fails and the next trigger retries.
 */
public class EodCandleJob implements ScheduledJob {

  static final String NAME = "eod-candles";
  private static final int MAX_LISTED_FAILURES = 20;

  /**
   * @param cutoff IST time after which today's candles are expected to be available
   * @param maxCatchUp most trading days one run catches up; older gaps need a backfill
   * @param series share series to load, for example EQ
   * @param maxInstruments 0 for all, or a limit for short development runs
   * @param minCoverage share of instruments that must have a candle for the last day (0.5 = half)
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

  EodCandleJob(
      CandleIngestionService ingestion, TradingCalendar calendar, Clock clock, Settings settings) {
    this.ingestion = ingestion;
    this.calendar = calendar;
    this.clock = clock;
    this.settings = settings;
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
    List<CandleRepository.Target> universe =
        ingestion.universe(settings.series(), settings.maxInstruments());
    if (universe.isEmpty()) {
      throw new IllegalStateException(
          "no active shares with a " + ingestion.providerName() + " key; load instruments first");
    }
    int ok = 0;
    int withLastDay = 0;
    List<String> failed = new ArrayList<>();
    for (CandleRepository.Target share : universe) {
      try {
        var result = ingestion.ingest(share, Interval.DAY, window.from(), window.to());
        if (result.hasErrors()) {
          failed.add(share.tradingSymbol() + " (validation)");
          continue;
        }
        ok++;
        if (window.to().equals(result.newestDate())) {
          withLastDay++;
        }
      } catch (ProviderException.Auth | ProviderException.RateLimited e) {
        throw e;
      } catch (RuntimeException e) {
        failed.add(share.tradingSymbol() + " (" + e.getClass().getSimpleName() + ")");
      }
    }
    for (String index : settings.benchmarks()) {
      try {
        if (ingestion.ingestIndex(index, window.from(), window.to()).hasErrors()) {
          failed.add(index + " (benchmark validation)");
        }
      } catch (ProviderException.Auth | ProviderException.RateLimited e) {
        throw e;
      } catch (RuntimeException e) {
        failed.add(index + " (benchmark " + e.getClass().getSimpleName() + ")");
      }
    }
    double coverage = (double) withLastDay / universe.size();
    if (coverage < settings.minCoverage()) {
      throw new IllegalStateException(
          String.format(
              "only %d of %d shares have a candle for %s; data probably not published yet",
              withLastDay, universe.size(), window.to()));
    }
    String detail =
        String.format(
            "%d shares, %d with a candle for %s, %d failed%s",
            universe.size(),
            withLastDay,
            window.to(),
            failed.size(),
            failed.isEmpty()
                ? ""
                : ": "
                    + String.join(
                        ", ", failed.subList(0, Math.min(failed.size(), MAX_LISTED_FAILURES)))
                    + (failed.size() > MAX_LISTED_FAILURES ? ", ..." : ""));
    return new JobOutcome(ok, failed.size(), detail);
  }
}
