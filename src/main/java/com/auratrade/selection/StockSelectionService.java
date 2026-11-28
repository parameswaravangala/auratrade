package com.auratrade.selection;

import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.screener.ScreenerService;
import com.auratrade.strategy.DataRequirement;
import com.auratrade.strategy.Strategy;
import com.auratrade.strategy.StrategyContext;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.StrategyEvaluator;
import com.auratrade.strategy.StrategyRegistry;
import com.auratrade.strategy.StrategyVersion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Runs every registered strategy (at its default version) over the NIFTY-style share list for one
 * session and ranks the results (plan slice P1). Reuses the screener's share guards, so stale,
 * short-history, illiquid and split-like shares are skipped the same way as today. Nothing here
 * writes to the database.
 */
@Service
public class StockSelectionService {

  /** The result with the session it was computed for and the source that supplied the candles. */
  public record Run(
      LocalDate asOf,
      String source,
      List<String> approvedStrategies,
      ScoringProfile profile,
      Selection.Result result) {}

  private static final int MIN_HISTORY = 21;

  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final ScreenerService screener;
  private final List<StrategyVersion> versions;
  private final StrategyRegistry registry;
  private final Set<String> approved;

  public StockSelectionService(
      CandleSeriesReader reader,
      MarketDataProvider provider,
      ScreenerService screener,
      StrategyRegistry registry,
      @Value("${auratrade.selection.approved-strategies:}") String approvedStrategies) {
    this.approved = parse(approvedStrategies);
    this.reader = reader;
    this.provider = provider;
    this.screener = screener;
    this.registry = registry;
    this.versions = registry.all().stream().map(s -> StrategyVersion.defaults(s, "1.0.0")).toList();
  }

  /** Every registered strategy at its default version, in registry order. */
  public List<StrategyVersion> versions() {
    return versions;
  }

  public Run run(Optional<LocalDate> asOf, int limit) {
    String source = provider.name();
    LocalDate date =
        asOf.or(() -> reader.latestDate(source, Interval.DAY))
            .orElseThrow(() -> new IllegalStateException("no candles from " + source + " yet"));
    int lookback =
        versions.stream()
            .mapToInt(v -> strategy(v).lookback(v.parameters()))
            .max()
            .orElse(MIN_HISTORY);
    int history = Math.max(lookback, MIN_HISTORY);

    Optional<Series> benchmark = Optional.empty();
    boolean needsBenchmark =
        versions.stream().anyMatch(v -> strategy(v).requires().contains(DataRequirement.BENCHMARK));
    if (needsBenchmark) {
      benchmark = reader.index(source, Interval.DAY, screener.benchmark(), date, history);
      if (benchmark.isEmpty() || !date.equals(benchmark.get().lastDate())) {
        throw new IllegalStateException(
            screener.benchmark()
                + " has no candle for "
                + date
                + "; load the benchmark history first");
      }
    }
    StrategyContext context = new StrategyContext(date, benchmark);

    List<Selection.ShareEvaluations> shares = new ArrayList<>();
    Map<String, Integer> skipped = new TreeMap<>();
    reader.forEachShare(
        source,
        Interval.DAY,
        date,
        history,
        screener.series(),
        share -> {
          String guard = screener.guard(share, date, lookback);
          if (guard != null) {
            skipped.merge(guard, 1, Integer::sum);
            return;
          }
          List<StrategyEvaluation> evaluations = new ArrayList<>(versions.size());
          for (StrategyVersion v : versions) {
            evaluations.add(StrategyEvaluator.evaluate(strategy(v), v, share, context));
          }
          shares.add(
              new Selection.ShareEvaluations(
                  share.instrumentId(), share.symbol(), share.name(), evaluations));
        });
    return new Run(
        date,
        source,
        List.copyOf(approved),
        ScoringProfile.BALANCED_SWING,
        Selection.rank(shares, limit, skipped, approved, ScoringProfile.BALANCED_SWING));
  }

  /** Comma-separated strategy ids whose gate has passed (ADR 0042). Blank means none. */
  static Set<String> parse(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return java.util.Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private Strategy strategy(StrategyVersion v) {
    return registry.get(v.strategyId());
  }
}
