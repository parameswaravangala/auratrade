package com.auratrade.backtest;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.screener.Screen;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Replays screens over one share's history. Pure: no I/O.
 *
 * <p>No look-ahead: on a sample date the screen sees only candles up to that date's close, cut to
 * the same window length the live screener uses. A match is entered at the next session's open and
 * exited at the close {@code h} sessions after the signal. A sample whose holding period contains a
 * one-day move over 40% (likely an unadjusted split or bonus) is dropped and counted.
 */
final class ScreenReplay {

  static final BigDecimal MAX_ONE_DAY_MOVE = new BigDecimal("0.40");

  /** Decides whether a share may be screened on a date; null means yes (see ScreenerService). */
  @FunctionalInterface
  interface Guard {
    String check(Series window, LocalDate asOf, int needed);
  }

  /**
   * One match and what happened after it. Returns are fractions; excess is versus the benchmark.
   */
  record Sample(
      String screen,
      LocalDate date,
      String symbol,
      BigDecimal score,
      int horizon,
      BigDecimal ret,
      BigDecimal excess) {}

  /** Samples from one share, plus how many forward windows were dropped as corporate actions. */
  record Outcome(List<Sample> samples, Map<String, Integer> droppedCorporateAction) {}

  private ScreenReplay() {}

  static Outcome replay(
      Series share,
      Series benchmark,
      List<Screen> screens,
      Set<LocalDate> sampleDates,
      List<Integer> horizons,
      Guard guard) {
    List<Candle> c = share.candles();
    Map<LocalDate, Integer> benchIndex = indexByDate(benchmark.candles());
    List<Sample> samples = new ArrayList<>();
    Map<String, Integer> dropped = new HashMap<>();
    for (int i = 0; i < c.size(); i++) {
      LocalDate date = c.get(i).tradingDate();
      if (!sampleDates.contains(date)) {
        continue;
      }
      for (Screen screen : screens) {
        int need = screen.lookback();
        if (i + 1 < need) {
          continue;
        }
        Series window = slice(share, i + 1 - need, i + 1);
        if (guard.check(window, date, need) != null) {
          continue;
        }
        Optional<Series> benchWindow = Optional.empty();
        if (screen.needsBenchmark()) {
          Integer b = benchIndex.get(date);
          if (b == null || b + 1 < need) {
            continue;
          }
          benchWindow = Optional.of(slice(benchmark, b + 1 - need, b + 1));
        }
        Optional<Screen.Hit> hit = screen.evaluate(window, new Screen.Context(date, benchWindow));
        if (hit.isEmpty()) {
          continue;
        }
        for (int h : horizons) {
          if (i + h >= c.size()) {
            continue;
          }
          List<BigDecimal> closes = c.subList(i, i + h + 1).stream().map(Candle::close).toList();
          BigDecimal move = Indicators.largestMove(closes, h).orElse(BigDecimal.ZERO);
          if (move.compareTo(MAX_ONE_DAY_MOVE) > 0) {
            dropped.merge(screen.name(), 1, Integer::sum);
            continue;
          }
          Candle entry = c.get(i + 1);
          Candle exit = c.get(i + h);
          Integer be = benchIndex.get(entry.tradingDate());
          Integer bx = benchIndex.get(exit.tradingDate());
          if (be == null || bx == null || entry.open().signum() <= 0) {
            continue;
          }
          BigDecimal ret =
              exit.close().divide(entry.open(), Indicators.MC).subtract(BigDecimal.ONE);
          BigDecimal benchRet =
              benchmark
                  .candles()
                  .get(bx)
                  .close()
                  .divide(benchmark.candles().get(be).open(), Indicators.MC)
                  .subtract(BigDecimal.ONE);
          samples.add(
              new Sample(
                  screen.name(),
                  date,
                  share.symbol(),
                  hit.get().score(),
                  h,
                  ret,
                  ret.subtract(benchRet)));
        }
      }
    }
    return new Outcome(samples, dropped);
  }

  /**
   * Dates on which {@code screen} matched this share, with the score, using the same no-look-ahead
   * window and guard as {@link #replay}. For the portfolio simulation.
   */
  static List<PortfolioSimulator.Signal> signals(
      Series share, Series benchmark, Screen screen, Set<LocalDate> dates, Guard guard) {
    List<Candle> c = share.candles();
    Map<LocalDate, Integer> benchIndex = indexByDate(benchmark.candles());
    List<PortfolioSimulator.Signal> out = new ArrayList<>();
    int need = screen.lookback();
    for (int i = need - 1; i < c.size(); i++) {
      LocalDate date = c.get(i).tradingDate();
      if (!dates.contains(date)) {
        continue;
      }
      Series window = slice(share, i + 1 - need, i + 1);
      if (guard.check(window, date, need) != null) {
        continue;
      }
      Optional<Series> benchWindow = Optional.empty();
      if (screen.needsBenchmark()) {
        Integer b = benchIndex.get(date);
        if (b == null || b + 1 < need) {
          continue;
        }
        benchWindow = Optional.of(slice(benchmark, b + 1 - need, b + 1));
      }
      screen
          .evaluate(window, new Screen.Context(date, benchWindow))
          .ifPresent(h ->
              out.add(
                  new PortfolioSimulator.Signal(
                      date, share.instrumentId(), share.symbol(), h.score())));
    }
    return out;
  }

  private static Series slice(Series s, int from, int to) {
    return new Series(s.instrumentId(), s.symbol(), s.name(), s.candles().subList(from, to));
  }

  private static Map<LocalDate, Integer> indexByDate(List<Candle> candles) {
    Map<LocalDate, Integer> index = new HashMap<>(candles.size() * 2);
    for (int i = 0; i < candles.size(); i++) {
      index.put(candles.get(i).tradingDate(), i);
    }
    return index;
  }
}
