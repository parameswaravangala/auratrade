package com.auratrade.backtest;

import com.auratrade.backtest.PortfolioSimulator.Day;
import com.auratrade.backtest.PortfolioSimulator.Trade;
import com.auratrade.indicators.Indicators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Account-level statistics from a simulation. Money amounts stay BigDecimal; ratios that need
 * powers or square roots (CAGR, volatility, Sharpe) are computed in double and rounded, because
 * they are statistics, not money.
 */
final class PortfolioMetrics {

  record Summary(
      BigDecimal startEquity,
      BigDecimal endEquity,
      BigDecimal totalReturnPct,
      BigDecimal cagrPct,
      BigDecimal maxDrawdownPct,
      BigDecimal annualVolatilityPct,
      BigDecimal sharpe,
      BigDecimal benchmarkTotalReturnPct,
      BigDecimal benchmarkCagrPct,
      BigDecimal benchmarkMaxDrawdownPct,
      int trades,
      BigDecimal winRatePct,
      BigDecimal avgWinPct,
      BigDecimal avgLossPct,
      BigDecimal profitFactor,
      BigDecimal avgSessionsHeld,
      BigDecimal avgPositions,
      BigDecimal totalCosts,
      Map<String, Long> exitsByReason,
      Map<Integer, BigDecimal> returnPctByYear,
      Map<Integer, BigDecimal> benchmarkReturnPctByYear) {}

  private PortfolioMetrics() {}

  static Summary of(PortfolioSimulator.Outcome o, BigDecimal initial) {
    List<Day> days = o.days();
    BigDecimal end = days.isEmpty() ? initial : days.get(days.size() - 1).equity();
    double years =
        days.size() < 2
            ? 0
            : ChronoUnit.DAYS.between(days.get(0).date(), days.get(days.size() - 1).date())
                / 365.25;

    List<Trade> t = o.trades();
    List<Trade> wins = t.stream().filter(x -> x.netPnl().signum() > 0).toList();
    List<Trade> losses = t.stream().filter(x -> x.netPnl().signum() <= 0).toList();
    BigDecimal grossWin = wins.stream().map(Trade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal grossLoss =
        losses.stream().map(Trade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add).abs();

    BigDecimal benchEnd = days.isEmpty() ? null : days.get(days.size() - 1).benchmark();
    return new Summary(
        initial,
        end,
        pct(end.divide(initial, Indicators.MC).subtract(BigDecimal.ONE)),
        cagr(initial, end, years),
        maxDrawdown(days, Day::equity),
        volatility(days),
        sharpe(days),
        benchEnd == null
            ? null
            : pct(benchEnd.divide(initial, Indicators.MC).subtract(BigDecimal.ONE)),
        benchEnd == null ? null : cagr(initial, benchEnd, years),
        maxDrawdown(days.stream().filter(d -> d.benchmark() != null).toList(), Day::benchmark),
        t.size(),
        t.isEmpty() ? null : pct(ratio(wins.size(), t.size())),
        averageReturnPct(wins),
        averageReturnPct(losses),
        grossLoss.signum() == 0 ? null : grossWin.divide(grossLoss, 2, RoundingMode.HALF_UP),
        t.isEmpty()
            ? null
            : BigDecimal.valueOf(t.stream().mapToInt(Trade::sessionsHeld).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP),
        days.isEmpty()
            ? null
            : BigDecimal.valueOf(days.stream().mapToInt(Day::positions).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP),
        o.totalCosts(),
        t.stream()
            .collect(Collectors.groupingBy(Trade::exitReason, TreeMap::new, Collectors.counting())),
        yearly(days, Day::equity),
        yearly(days.stream().filter(d -> d.benchmark() != null).toList(), Day::benchmark));
  }

  /** Average of (net P&L / entry value) per trade, in percent. */
  private static BigDecimal averageReturnPct(List<Trade> trades) {
    if (trades.isEmpty()) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (Trade x : trades) {
      BigDecimal value = x.entryPrice().multiply(BigDecimal.valueOf(x.quantity()));
      sum = sum.add(x.netPnl().divide(value, Indicators.MC));
    }
    return pct(sum.divide(BigDecimal.valueOf(trades.size()), Indicators.MC));
  }

  static BigDecimal maxDrawdown(List<Day> days, Function<Day, BigDecimal> value) {
    if (days.isEmpty()) {
      return null;
    }
    BigDecimal peak = value.apply(days.get(0));
    BigDecimal worst = BigDecimal.ZERO;
    for (Day d : days) {
      BigDecimal v = value.apply(d);
      peak = peak.max(v);
      BigDecimal dd = v.divide(peak, Indicators.MC).subtract(BigDecimal.ONE);
      worst = worst.min(dd);
    }
    return pct(worst);
  }

  private static Map<Integer, BigDecimal> yearly(List<Day> days, Function<Day, BigDecimal> value) {
    Map<Integer, BigDecimal> out = new TreeMap<>();
    BigDecimal startOfYear = null;
    int year = -1;
    BigDecimal last = null;
    for (Day d : days) {
      BigDecimal v = value.apply(d);
      if (d.date().getYear() != year) {
        if (year != -1) {
          out.put(year, pct(last.divide(startOfYear, Indicators.MC).subtract(BigDecimal.ONE)));
        }
        year = d.date().getYear();
        startOfYear = last == null ? v : last; // previous year's last value
      }
      last = v;
    }
    if (year != -1) {
      out.put(year, pct(last.divide(startOfYear, Indicators.MC).subtract(BigDecimal.ONE)));
    }
    return out;
  }

  private static BigDecimal cagr(BigDecimal start, BigDecimal end, double years) {
    if (years <= 0) {
      return null;
    }
    double r = Math.pow(end.doubleValue() / start.doubleValue(), 1 / years) - 1;
    return BigDecimal.valueOf(r * 100).setScale(2, RoundingMode.HALF_UP);
  }

  private static double[] dailyReturns(List<Day> days) {
    double[] r = new double[Math.max(0, days.size() - 1)];
    for (int i = 1; i < days.size(); i++) {
      r[i - 1] = days.get(i).equity().doubleValue() / days.get(i - 1).equity().doubleValue() - 1;
    }
    return r;
  }

  private static BigDecimal volatility(List<Day> days) {
    double[] r = dailyReturns(days);
    return r.length < 2
        ? null
        : BigDecimal.valueOf(stdev(r) * Math.sqrt(252) * 100).setScale(2, RoundingMode.HALF_UP);
  }

  /** Annualised Sharpe with a zero risk-free rate. */
  private static BigDecimal sharpe(List<Day> days) {
    double[] r = dailyReturns(days);
    if (r.length < 2) {
      return null;
    }
    double sd = stdev(r);
    if (sd == 0) {
      return null;
    }
    double mean = java.util.Arrays.stream(r).average().orElse(0);
    return BigDecimal.valueOf(mean / sd * Math.sqrt(252)).setScale(2, RoundingMode.HALF_UP);
  }

  private static double stdev(double[] r) {
    double mean = java.util.Arrays.stream(r).average().orElse(0);
    double ss = 0;
    for (double x : r) {
      ss += (x - mean) * (x - mean);
    }
    return Math.sqrt(ss / (r.length - 1));
  }

  private static BigDecimal ratio(long a, long b) {
    return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), Indicators.MC);
  }

  private static BigDecimal pct(BigDecimal fraction) {
    return fraction.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
  }
}
