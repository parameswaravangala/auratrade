package com.auratrade.backtest;

import com.auratrade.backtest.ScreenReplay.Sample;
import com.auratrade.indicators.Indicators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Summary statistics over backtest samples. Pure; percentages are rounded to two decimals. */
final class BacktestStats {

  /**
   * @param hitRatePct share of samples that beat the benchmark (net of costs)
   * @param meanExcessPct average return minus benchmark return, net of costs
   * @param medianExcessPct median of the same
   * @param meanReturnPct average raw return, net of costs
   * @param p10ReturnPct 10th percentile raw return (a bad but not worst case)
   * @param worstReturnPct worst raw return
   */
  record HorizonStats(
      int horizon,
      int samples,
      BigDecimal hitRatePct,
      BigDecimal meanExcessPct,
      BigDecimal medianExcessPct,
      BigDecimal meanReturnPct,
      BigDecimal p10ReturnPct,
      BigDecimal worstReturnPct) {}

  private BacktestStats() {}

  static HorizonStats of(int horizon, List<Sample> samples, BigDecimal cost) {
    if (samples.isEmpty()) {
      return new HorizonStats(horizon, 0, null, null, null, null, null, null);
    }
    List<BigDecimal> excess =
        samples.stream().map(s -> s.excess().subtract(cost)).sorted().toList();
    List<BigDecimal> ret = samples.stream().map(s -> s.ret().subtract(cost)).sorted().toList();
    long wins = excess.stream().filter(e -> e.signum() > 0).count();
    return new HorizonStats(
        horizon,
        samples.size(),
        pct(BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(samples.size()), Indicators.MC)),
        pct(mean(excess)),
        pct(median(excess)),
        pct(mean(ret)),
        pct(ret.get((int) Math.floor(0.10 * (ret.size() - 1)))),
        pct(ret.get(0)));
  }

  /** Keeps only the {@code n} highest-scoring samples per screen, date and horizon. */
  static List<Sample> topPerDate(List<Sample> samples, int n) {
    Map<String, List<Sample>> groups =
        samples.stream()
            .collect(Collectors.groupingBy(s -> s.screen() + "|" + s.date() + "|" + s.horizon()));
    List<Sample> out = new ArrayList<>();
    for (List<Sample> g : groups.values()) {
      g.stream()
          .sorted(Comparator.comparing(Sample::score).reversed().thenComparing(Sample::symbol))
          .limit(n)
          .forEach(out::add);
    }
    return out;
  }

  /** Mean excess return per calendar year of the signal date, net of costs. */
  static Map<Integer, BigDecimal> meanExcessByYear(List<Sample> samples, BigDecimal cost) {
    Map<Integer, List<BigDecimal>> byYear = new TreeMap<>();
    for (Sample s : samples) {
      byYear
          .computeIfAbsent(s.date().getYear(), y -> new ArrayList<>())
          .add(s.excess().subtract(cost));
    }
    Map<Integer, BigDecimal> out = new TreeMap<>();
    byYear.forEach((y, values) -> out.put(y, pct(mean(values))));
    return out;
  }

  static BigDecimal mean(List<BigDecimal> values) {
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal v : values) {
      sum = sum.add(v);
    }
    return sum.divide(BigDecimal.valueOf(values.size()), Indicators.MC);
  }

  /** Median of an already sorted list. */
  static BigDecimal median(List<BigDecimal> sorted) {
    int n = sorted.size();
    return n % 2 == 1
        ? sorted.get(n / 2)
        : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), Indicators.MC);
  }

  private static BigDecimal pct(BigDecimal fraction) {
    return fraction.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
  }
}
