package com.auratrade.screener;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.marketdata.Interval;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.screener.indicators.Indicators;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a screen over every active company share and returns a ranked shortlist of at most {@link
 * #MAX_SHORTLIST} (the screener's documented limit). Plain Java, no LLM.
 *
 * <p>Shared guards, applied before a screen sees a share, each counted in the result:
 *
 * <ul>
 *   <li>STALE: no candle on the as-of date (suspended, delisted or missing data);
 *   <li>SHORT_HISTORY: fewer candles than the screen needs;
 *   <li>ILLIQUID: 20-day average traded value (close x volume) below the minimum;
 *   <li>POSSIBLE_CORPORATE_ACTION: a one-day move over 40% within the lookback, likely an
 *       unadjusted split or bonus (backlog B-020), which would fake breakouts and crashes.
 * </ul>
 */
public class ScreenerService {

  private static final Logger log = LoggerFactory.getLogger(ScreenerService.class);

  public static final int MAX_SHORTLIST = 10;
  static final BigDecimal MAX_ONE_DAY_MOVE = new BigDecimal("0.40");

  /** Outcome of one run, with the numbers behind every exclusion. */
  public record Result(
      String screen,
      String description,
      LocalDate asOf,
      String source,
      int evaluated,
      int matched,
      Map<String, Integer> skipped,
      List<Screen.Hit> shortlist) {}

  public record ScreenInfo(String name, String description, int lookback) {}

  private final CandleSeriesReader reader;
  private final MarketDataProvider provider;
  private final Map<String, Screen> screens = new LinkedHashMap<>();
  private final List<String> series;
  private final BigDecimal minTradedValue;
  private final String benchmark;

  ScreenerService(
      CandleSeriesReader reader,
      MarketDataProvider provider,
      List<Screen> screens,
      List<String> series,
      BigDecimal minTradedValue,
      String benchmark) {
    this.reader = reader;
    this.provider = provider;
    screens.forEach(s -> this.screens.put(s.name(), s));
    this.series = List.copyOf(series);
    this.minTradedValue = minTradedValue;
    this.benchmark = benchmark;
  }

  public List<ScreenInfo> screens() {
    return screens.values().stream()
        .map(s -> new ScreenInfo(s.name(), s.description(), s.lookback()))
        .toList();
  }

  /**
   * @param asOf trading date to screen; empty means the newest candle date
   * @param limit shortlist size, capped at {@link #MAX_SHORTLIST}
   */
  public Result run(String screenName, Optional<LocalDate> asOf, int limit) {
    Screen screen = screens.get(screenName);
    if (screen == null) {
      throw new IllegalArgumentException(
          "unknown screen '" + screenName + "'; known: " + screens.keySet());
    }
    String source = provider.name();
    LocalDate date =
        asOf.or(() -> reader.latestDate(source, Interval.DAY))
            .orElseThrow(() -> new IllegalStateException("no candles from " + source + " yet"));
    Optional<Series> bench = Optional.empty();
    if (screen.needsBenchmark()) {
      bench = reader.index(source, Interval.DAY, benchmark, date, screen.lookback());
      if (bench.isEmpty() || !date.equals(bench.get().lastDate())) {
        throw new IllegalStateException(
            benchmark + " has no candle for " + date + "; load the benchmark history first");
      }
    }
    Screen.Context context = new Screen.Context(date, bench);
    List<Screen.Hit> hits = new ArrayList<>();
    Map<String, Integer> skipped = new TreeMap<>();
    int[] evaluated = {0};
    int lookback = Math.max(screen.lookback(), 21);
    reader.forEachShare(
        source,
        Interval.DAY,
        date,
        lookback,
        series,
        s -> {
          String guard = guard(s, date, screen.lookback());
          if (guard != null) {
            skipped.merge(guard, 1, Integer::sum);
            return;
          }
          evaluated[0]++;
          screen.evaluate(s, context).ifPresent(hits::add);
        });
    hits.sort(Comparator.comparing(Screen.Hit::score).reversed());
    int n = Math.max(1, Math.min(limit, MAX_SHORTLIST));
    log.info(
        "Screen {} as of {}: {} evaluated, {} matched, skipped {}",
        screenName,
        date,
        evaluated[0],
        hits.size(),
        skipped);
    return new Result(
        screen.name(),
        screen.description(),
        date,
        source,
        evaluated[0],
        hits.size(),
        skipped,
        List.copyOf(hits.subList(0, Math.min(n, hits.size()))));
  }

  /** The reason to skip a share, or null if it may be screened. */
  String guard(Series s, LocalDate asOf, int needed) {
    if (!asOf.equals(s.lastDate())) {
      return "STALE";
    }
    if (s.candles().size() < needed) {
      return "SHORT_HISTORY";
    }
    List<Candle> c = s.candles();
    List<Candle> last20 = c.subList(c.size() - 20, c.size());
    BigDecimal traded = BigDecimal.ZERO;
    for (Candle k : last20) {
      traded = traded.add(k.close().multiply(BigDecimal.valueOf(k.volume())));
    }
    if (traded.divide(BigDecimal.valueOf(20), Indicators.MC).compareTo(minTradedValue) < 0) {
      return "ILLIQUID";
    }
    List<BigDecimal> closes = c.stream().map(Candle::close).toList();
    BigDecimal move = Indicators.largestMove(closes, needed - 1).orElse(BigDecimal.ZERO);
    if (move.compareTo(MAX_ONE_DAY_MOVE) > 0) {
      return "POSSIBLE_CORPORATE_ACTION";
    }
    return null;
  }
}
