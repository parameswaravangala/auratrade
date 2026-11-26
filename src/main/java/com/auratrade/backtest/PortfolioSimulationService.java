package com.auratrade.backtest;

import com.auratrade.backtest.PortfolioSimulator.Signal;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.screener.Screen;
import com.auratrade.screener.ScreenerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates an account trading one screen over stored history (ADR 0022), in two passes so memory
 * stays bounded: (1) replay the screen on every session for every share and keep the best
 * candidates per day; (2) load compact price tables only for shares that ever became a candidate,
 * then run {@link PortfolioSimulator}. Plain Java, no LLM.
 */
public class PortfolioSimulationService {

  private static final Logger log = LoggerFactory.getLogger(PortfolioSimulationService.class);
  private static final int HISTORY = 5000;

  static final List<String> CAVEATS =
      List.of(
          "Survivorship bias: only today's listed shares; delisted shares are missing, which"
              + " flatters results.",
          "Prices are not adjusted for corporate actions; a held share that gaps more than 40% is"
              + " closed at the previous close and reported as CORPORATE_ACTION.",
          "Costs are Zerodha's published equity-delivery charges read 2026-11-26 (a broker's"
              + " schedule, not checked against NSE, SEBI or the Stamp Act); slippage is an"
              + " assumption.",
          "No liquidity limits or partial fills; exits and entries on the same open share cash.",
          "Parameters are conventional defaults, not optimised; results on one history are not a"
              + " forecast.");

  public record Request(
      String screen,
      LocalDate from,
      LocalDate to,
      BigDecimal initialCapital,
      int maxPositions,
      BigDecimal riskPerTradePct,
      BigDecimal maxPositionPct,
      BigDecimal atrMultiple,
      int maxHoldSessions,
      int slippageBps,
      boolean costs) {}

  public record Report(
      Request request,
      String source,
      String benchmark,
      int signalDays,
      int candidateShares,
      long seconds,
      PortfolioMetrics.Summary summary,
      List<PortfolioSimulator.Trade> worstTrades,
      List<PortfolioSimulator.Trade> bestTrades,
      List<PortfolioSimulator.Day> equityCurveWeekly,
      List<String> caveats) {}

  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final ScreenerService screener;
  private final TradingCalendar calendar;

  PortfolioSimulationService(
      CandleSeriesReader reader,
      MarketDataProvider provider,
      ScreenerService screener,
      TradingCalendar calendar) {
    this.reader = reader;
    this.provider = provider;
    this.screener = screener;
    this.calendar = calendar;
  }

  public Report run(Request r) {
    Screen screen =
        screener.definitions().stream()
            .filter(s -> s.name().equals(r.screen()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown screen '" + r.screen() + "'"));
    if (r.to().isBefore(r.from()) || r.maxPositions() < 1 || r.initialCapital().signum() <= 0) {
      throw new IllegalArgumentException("check from, to, maxPositions and initialCapital");
    }
    long started = System.nanoTime();
    String source = provider.name();
    LocalDate latest =
        reader
            .latestDate(source, Interval.DAY)
            .orElseThrow(() -> new IllegalStateException("no candles from " + source));
    LocalDate to = r.to().isAfter(latest) ? latest : r.to();
    Series bench =
        reader
            .index(source, Interval.DAY, screener.benchmark(), latest, HISTORY)
            .orElseThrow(
                () -> new IllegalStateException("load " + screener.benchmark() + " first"));
    List<LocalDate> sessions = calendar.tradingDaysBetween(r.from(), to);
    Set<LocalDate> dates = new HashSet<>(sessions);

    // Pass 1: signals, keeping the best few per day.
    List<Signal> all = Collections.synchronizedList(new ArrayList<>());
    parallelOverShares(
        source,
        latest,
        share -> all.addAll(ScreenReplay.signals(share, bench, screen, dates, screener::guard)));
    int keep = r.maxPositions() * 3;
    Map<LocalDate, List<Signal>> byDate = new TreeMap<>();
    all.stream()
        .collect(Collectors.groupingBy(Signal::date))
        .forEach((d, list) ->
            byDate.put(
                d,
                list.stream()
                    .sorted(
                        Comparator.comparing(Signal::score)
                            .reversed()
                            .thenComparing(Signal::symbol))
                    .limit(keep)
                    .toList()));
    Set<Long> candidates =
        byDate.values().stream()
            .flatMap(List::stream)
            .map(Signal::instrumentId)
            .collect(Collectors.toSet());

    // Pass 2: compact prices for candidates only.
    Map<Long, PriceTable> prices = new HashMap<>();
    reader.forEachInstrument(
        source,
        Interval.DAY,
        latest,
        HISTORY,
        new ArrayList<>(candidates),
        s -> prices.put(s.instrumentId(), new PriceTable(s.symbol(), s.candles())));
    PriceTable benchTable = new PriceTable(bench.symbol(), bench.candles());

    var settings =
        new PortfolioSimulator.Settings(
            r.initialCapital(),
            r.maxPositions(),
            r.riskPerTradePct().movePointLeft(2),
            r.maxPositionPct().movePointLeft(2),
            14,
            r.atrMultiple(),
            r.maxHoldSessions(),
            r.slippageBps(),
            r.costs() ? CostModel.zerodhaDelivery() : CostModel.free());
    var outcome = PortfolioSimulator.run(sessions, prices, byDate, benchTable, settings);
    var summary = PortfolioMetrics.of(outcome, r.initialCapital());

    List<PortfolioSimulator.Trade> byPnl =
        outcome.trades().stream()
            .sorted(Comparator.comparing(PortfolioSimulator.Trade::netPnl))
            .toList();
    List<PortfolioSimulator.Day> weekly = new ArrayList<>();
    for (int i = 0; i < outcome.days().size(); i += 5) {
      weekly.add(outcome.days().get(i));
    }
    if (!outcome.days().isEmpty()) {
      weekly.add(outcome.days().get(outcome.days().size() - 1));
    }
    long seconds = (System.nanoTime() - started) / 1_000_000_000L;
    log.info(
        "Portfolio simulation {} {} to {}: {} trades, end equity {}, {} s",
        r.screen(),
        r.from(),
        to,
        outcome.trades().size(),
        summary.endEquity(),
        seconds);
    return new Report(
        r,
        source,
        screener.benchmark(),
        byDate.size(),
        candidates.size(),
        seconds,
        summary,
        byPnl.subList(0, Math.min(5, byPnl.size())),
        byPnl.subList(Math.max(0, byPnl.size() - 5), byPnl.size()).reversed(),
        weekly,
        CAVEATS);
  }

  private void parallelOverShares(
      String source, LocalDate latest, java.util.function.Consumer<Series> work) {
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
    Semaphore inFlight = new Semaphore(threads * 2);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    Map<String, String> errors = new ConcurrentHashMap<>();
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
                work.accept(share);
              } catch (RuntimeException e) {
                errors.put(share.symbol(), e.toString());
              } finally {
                inFlight.release();
              }
            });
          });
    } finally {
      pool.shutdown();
      try {
        if (!pool.awaitTermination(60, TimeUnit.MINUTES)) {
          throw new IllegalStateException("simulation did not finish within an hour");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("simulation interrupted", e);
      }
    }
    if (!errors.isEmpty()) {
      log.warn(
          "Signal replay skipped {} shares, for example {}",
          errors.size(),
          errors.entrySet().iterator().next());
    }
  }
}
