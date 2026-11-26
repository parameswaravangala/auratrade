package com.auratrade.backtest;

import com.auratrade.backtest.BacktestStats.HorizonStats;
import com.auratrade.backtest.ScreenReplay.Sample;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.screener.Screen;
import com.auratrade.screener.ScreenerService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backtests the live screens over stored history (ADR 0021): what happened after each match,
 * compared with the benchmark. Shares are replayed in parallel with a bounded queue, so memory
 * stays flat while the reader streams history. Plain Java, no LLM.
 */
public class ScreenBacktestService {

  private static final Logger log = LoggerFactory.getLogger(ScreenBacktestService.class);
  private static final int HISTORY = 5000;

  /** Stated with every report so nobody reads more into the numbers than they support. */
  static final List<String> CAVEATS =
      List.of(
          "Survivorship bias: the universe is today's listed shares; delisted shares are missing,"
              + " which flatters results.",
          "Prices are not adjusted for corporate actions; samples with a one-day move over 40%"
              + " are dropped, smaller adjustments are not.",
          "Returns are before costs unless costBps is set; verify current brokerage, STT and fees"
              + " before choosing a cost.",
          "Holding periods overlap (samples every few sessions), so samples are not independent.",
          "Entry is at the next session's open and exit at the close h sessions after the signal;"
              + " slippage and liquidity limits are not modelled.");

  public record ScreenReport(
      String screen,
      String description,
      List<HorizonStats> allMatches,
      List<HorizonStats> topNPerDate,
      Map<Integer, BigDecimal> meanExcessPctByYear,
      int droppedCorporateAction) {}

  public record Report(
      LocalDate from,
      LocalDate to,
      int stepSessions,
      int sampleDates,
      List<Integer> horizons,
      int topN,
      int costBps,
      String source,
      String benchmark,
      int sharesReplayed,
      long seconds,
      List<ScreenReport> screens,
      List<String> caveats) {}

  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final ScreenerService screener;
  private final TradingCalendar calendar;

  ScreenBacktestService(
      CandleSeriesReader reader,
      MarketDataProvider provider,
      ScreenerService screener,
      TradingCalendar calendar) {
    this.reader = reader;
    this.provider = provider;
    this.screener = screener;
    this.calendar = calendar;
  }

  /**
   * @param step use every {@code step}-th trading day as a signal date
   * @param horizons holding periods in sessions, for example 5, 20, 60
   * @param topN size of the per-date shortlist variant (the live screener returns at most 10)
   * @param costBps round-trip cost in basis points subtracted from every return
   */
  public Report run(
      LocalDate from, LocalDate to, int step, List<Integer> horizons, int topN, int costBps) {
    if (to.isBefore(from) || step < 1 || horizons.isEmpty() || topN < 1 || costBps < 0) {
      throw new IllegalArgumentException("check from, to, step, horizons, topN and costBps");
    }
    long started = System.nanoTime();
    String source = provider.name();
    LocalDate latest =
        reader
            .latestDate(source, Interval.DAY)
            .orElseThrow(() -> new IllegalStateException("no candles from " + source));
    Series bench =
        reader
            .index(source, Interval.DAY, screener.benchmark(), latest, HISTORY)
            .orElseThrow(
                () -> new IllegalStateException("load " + screener.benchmark() + " history first"));

    List<LocalDate> days = calendar.tradingDaysBetween(from, to.isAfter(latest) ? latest : to);
    Set<LocalDate> sampleDates = new HashSet<>();
    for (int i = 0; i < days.size(); i += step) {
      sampleDates.add(days.get(i));
    }
    List<Screen> screens = screener.definitions();
    List<Integer> hs = List.copyOf(horizons);

    List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
    Map<String, Integer> dropped = new ConcurrentHashMap<>();
    AtomicInteger shares = new AtomicInteger();
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
    Semaphore inFlight = new Semaphore(threads * 2);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      reader.forEachShare(
          source,
          Interval.DAY,
          latest,
          HISTORY,
          screener.series(),
          share -> {
            inFlight.acquireUninterruptibly();
            pool.execute(() -> {
              try {
                var outcome =
                    ScreenReplay.replay(share, bench, screens, sampleDates, hs, screener::guard);
                samples.addAll(outcome.samples());
                outcome
                    .droppedCorporateAction()
                    .forEach((k, v) -> dropped.merge(k, v, Integer::sum));
                shares.incrementAndGet();
              } catch (RuntimeException e) {
                log.warn("Backtest skipped {}: {}", share.symbol(), e.toString());
              } finally {
                inFlight.release();
              }
            });
          });
    } finally {
      pool.shutdown();
      try {
        if (!pool.awaitTermination(Duration.ofHours(1).toMinutes(), TimeUnit.MINUTES)) {
          throw new IllegalStateException("backtest did not finish within an hour");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("backtest interrupted", e);
      }
    }

    BigDecimal cost = BigDecimal.valueOf(costBps).movePointLeft(4);
    List<ScreenReport> reports = new ArrayList<>();
    for (Screen screen : screens) {
      List<Sample> own = samples.stream().filter(s -> s.screen().equals(screen.name())).toList();
      List<Sample> top = BacktestStats.topPerDate(own, topN);
      List<HorizonStats> all = new ArrayList<>();
      List<HorizonStats> best = new ArrayList<>();
      for (int h : hs) {
        all.add(BacktestStats.of(h, own.stream().filter(s -> s.horizon() == h).toList(), cost));
        best.add(BacktestStats.of(h, top.stream().filter(s -> s.horizon() == h).toList(), cost));
      }
      int yearlyHorizon = hs.contains(20) ? 20 : hs.get(0);
      reports.add(
          new ScreenReport(
              screen.name(),
              screen.description(),
              all,
              best,
              new TreeMap<>(
                  BacktestStats.meanExcessByYear(
                      top.stream().filter(s -> s.horizon() == yearlyHorizon).toList(), cost)),
              dropped.getOrDefault(screen.name(), 0)));
    }
    long seconds = (System.nanoTime() - started) / 1_000_000_000L;
    log.info(
        "Screen backtest {} to {}: {} shares, {} samples, {} s",
        from,
        to,
        shares.get(),
        samples.size(),
        seconds);
    return new Report(
        from,
        to,
        step,
        sampleDates.size(),
        hs,
        topN,
        costBps,
        source,
        screener.benchmark(),
        shares.get(),
        seconds,
        reports,
        CAVEATS);
  }
}
