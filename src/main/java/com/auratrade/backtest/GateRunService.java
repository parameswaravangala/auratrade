package com.auratrade.backtest;

import com.auratrade.backtest.GateGrids.Setting;
import com.auratrade.backtest.GateGrids.SignalKey;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.screener.ScreenerService;
import com.auratrade.strategy.PlanExecutor.Bar;
import com.auratrade.strategy.Strategy;
import com.auratrade.strategy.StrategyRegistry;
import com.auratrade.strategy.StrategyVersion;
import com.auratrade.strategy.TradePlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs a strategy's gate (plan slice P2, ADR 0045): replays every grid setting over the history,
 * simulates each on the portfolio with costs, and evaluates the six pass rules with {@link
 * GateEvaluator}. Read-only: nothing is stored. The result is computed on each call.
 *
 * <p>Portfolio settings are the spec defaults for every setting (INR 10 lakh, 5 positions, 0.75%
 * risk, 20% maximum position, 5 bps slippage, Zerodha delivery costs), so the drawdown check is on
 * the spec-default setting by construction.
 */
@Service
public class GateRunService {

  static final BigDecimal CAPITAL = new BigDecimal("1000000");
  static final int MAX_POSITIONS = 5;
  static final BigDecimal RISK_PER_TRADE = new BigDecimal("0.0075");
  static final BigDecimal MAX_POSITION = new BigDecimal("0.20");
  static final int SLIPPAGE_BPS = 5;

  /** Signals kept per session, as in the portfolio simulation (3 x positions). */
  static final int KEEP_PER_DAY = 3 * MAX_POSITIONS;

  static final int HISTORY = 5000;
  static final LocalDate DEFAULT_OOS_START = LocalDate.of(2023, 1, 1);

  /** Caveats printed with every gate report, as the portfolio simulation prints its own. */
  static final List<String> CAVEATS =
      List.of(
          "Survivorship bias: only today's listed shares; delisted shares are missing, which flatters"
              + " results.",
          "Prices are not adjusted for corporate actions; a gap over 40% is screened out, not corrected.",
          "Costs are Zerodha's published delivery charges read 2026-11-26; slippage is an assumption.",
          "Signals are daily; the trade plan is executed on daily bars, so intraday order is unknown and"
              + " a bar touching both stop and target counts as the stop.",
          "Only the top "
              + KEEP_PER_DAY
              + " signals per session are kept, as in the portfolio simulation.");

  public record Request(String strategyId, LocalDate from, LocalDate oosStart, LocalDate to) {
    public Request {
      if (from == null || oosStart == null || to == null) {
        throw new IllegalArgumentException("from, oosStart and to are required");
      }
      if (!from.isBefore(oosStart) || !oosStart.isBefore(to)) {
        throw new IllegalArgumentException("need from < oosStart < to");
      }
    }
  }

  public record Run(
      String strategyRef,
      LocalDate from,
      LocalDate oosStart,
      LocalDate to,
      String source,
      String benchmark,
      int settings,
      int candidateShares,
      long seconds,
      GateEvaluator.Report report,
      List<String> caveats) {}

  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final ScreenerService screener;
  private final TradingCalendar calendar;
  private final StrategyRegistry registry;

  public GateRunService(
      CandleSeriesReader reader,
      MarketDataProvider provider,
      ScreenerService screener,
      TradingCalendar calendar,
      StrategyRegistry registry) {
    this.reader = reader;
    this.provider = provider;
    this.screener = screener;
    this.calendar = calendar;
    this.registry = registry;
  }

  public Run run(Request r) {
    long started = System.nanoTime();
    Strategy strategy = registry.get(r.strategyId());
    List<Setting> grid = GateGrids.forStrategy(r.strategyId());
    String defaultId = GateGrids.defaultSettingId(r.strategyId());

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
    Map<LocalDate, Integer> benchIndex = StrategyReplay.indexOf(bench);
    List<LocalDate> sessions = calendar.tradingDaysBetween(r.from(), to);
    Set<LocalDate> dates = new HashSet<>(sessions);

    // Pass 1: replay each distinct signal-generating setting once.
    Map<SignalKey, Map<LocalDate, List<PlanPortfolioSimulator.Signal>>> byKey =
        new LinkedHashMap<>();
    for (SignalKey key : grid.stream().map(GateGrids::signalKey).distinct().toList()) {
      StrategyVersion version =
          StrategyVersion.of(
              strategy,
              "1.0.0",
              Map.of(
                  "resistanceLookback",
                  BigDecimal.valueOf(key.resistanceLookback()),
                  "minVolumeRatio",
                  new BigDecimal(key.minVolumeRatio())));
      List<PlanPortfolioSimulator.Signal> all = Collections.synchronizedList(new ArrayList<>());
      parallelOverShares(
          source,
          latest,
          share ->
              all.addAll(
                  StrategyReplay.signals(
                      share, bench, benchIndex, version, strategy, dates, screener::guard)));
      byKey.put(key, keepTop(all));
    }

    // Pass 2: compact bars for every instrument that was ever a candidate.
    Set<Long> candidates = new HashSet<>();
    byKey
        .values()
        .forEach(byDate ->
            byDate.values().forEach(list -> list.forEach(s -> candidates.add(s.instrumentId()))));
    Map<Long, List<Bar>> bars = new HashMap<>();
    reader.forEachInstrument(
        source,
        Interval.DAY,
        latest,
        HISTORY,
        new ArrayList<>(candidates),
        s -> bars.put(s.instrumentId(), bars(s)));

    // Pass 3: each grid setting on the portfolio, then the gate.
    var settings =
        new PlanPortfolioSimulator.Settings(
            CAPITAL,
            MAX_POSITIONS,
            RISK_PER_TRADE,
            MAX_POSITION,
            SLIPPAGE_BPS,
            CostModel.zerodhaDelivery());
    List<GateEvaluator.SettingResult> results = new ArrayList<>();
    for (Setting s : grid) {
      List<PlanPortfolioSimulator.Signal> signals =
          byKey.get(GateGrids.signalKey(s)).values().stream()
              .flatMap(List::stream)
              .map(sig -> withHold(sig, s.maxHold()))
              .sorted(Comparator.comparing(PlanPortfolioSimulator.Signal::date))
              .toList();
      PlanPortfolioSimulator.Outcome outcome =
          PlanPortfolioSimulator.run(sessions, bars, signals, settings);
      results.add(new GateEvaluator.SettingResult(s.id(), outcome.trades(), outcome.days()));
    }

    var input =
        new GateEvaluator.Input(
            strategy.id() + "@1.0.0",
            r.oosStart(),
            to,
            CAPITAL,
            results,
            defaultId,
            niftyCagr(bench, r.oosStart()));
    GateEvaluator.Report report = GateEvaluator.evaluate(input);
    long seconds = (System.nanoTime() - started) / 1_000_000_000L;
    return new Run(
        input.strategyRef(),
        r.from(),
        r.oosStart(),
        to,
        source,
        screener.benchmark(),
        grid.size(),
        candidates.size(),
        seconds,
        report,
        CAVEATS);
  }

  private static PlanPortfolioSimulator.Signal withHold(PlanPortfolioSimulator.Signal s, int hold) {
    TradePlan plan = StrategyReplay.withMaxHold(s.plan(), hold);
    return new PlanPortfolioSimulator.Signal(
        s.date(), s.instrumentId(), s.symbol(), plan, s.score());
  }

  /**
   * The best {@link #KEEP_PER_DAY} signals on each session, highest score first, ties by symbol.
   */
  static Map<LocalDate, List<PlanPortfolioSimulator.Signal>> keepTop(
      Collection<PlanPortfolioSimulator.Signal> all) {
    Map<LocalDate, List<PlanPortfolioSimulator.Signal>> byDate = new java.util.TreeMap<>();
    all.stream()
        .collect(Collectors.groupingBy(PlanPortfolioSimulator.Signal::date))
        .forEach((d, list) ->
            byDate.put(
                d,
                list.stream()
                    .sorted(
                        Comparator.comparing(PlanPortfolioSimulator.Signal::score)
                            .reversed()
                            .thenComparing(PlanPortfolioSimulator.Signal::symbol))
                    .limit(KEEP_PER_DAY)
                    .toList()));
    return byDate;
  }

  static List<Bar> bars(Series s) {
    List<Bar> out = new ArrayList<>(s.candles().size());
    for (Candle c : s.candles()) {
      out.add(new Bar(c.tradingDate(), c.open(), c.high(), c.low(), c.close()));
    }
    return out;
  }

  /**
   * Nifty's buy-and-hold CAGR from the first bar on or after {@code oosStart} to the last bar, as a
   * fraction (0.09 is 9%). Empty series or a zero-length period give NaN, which the gate treats as
   * not beating Nifty.
   */
  static double niftyCagr(Series bench, LocalDate oosStart) {
    List<Candle> c = bench.candles();
    Optional<Candle> start =
        c.stream().filter(k -> !k.tradingDate().isBefore(oosStart)).findFirst();
    if (start.isEmpty() || c.isEmpty()) {
      return Double.NaN;
    }
    Candle first = start.get();
    Candle last = c.get(c.size() - 1);
    double years = ChronoUnit.DAYS.between(first.tradingDate(), last.tradingDate()) / 365.25;
    if (years <= 0) {
      return Double.NaN;
    }
    return Math.pow(last.close().doubleValue() / first.close().doubleValue(), 1.0 / years) - 1.0;
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
        pool.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while replaying shares", e);
      }
    }
    if (!errors.isEmpty()) {
      // Fail closed: a dropped share would bias the gate, so the whole run fails.
      throw new IllegalStateException(
          errors.size()
              + " shares failed in the gate replay; first: "
              + errors.entrySet().iterator().next());
    }
  }
}
