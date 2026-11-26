package com.auratrade.screener.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Technical indicators over a price series, oldest value first. Pure functions with BigDecimal
 * arithmetic (DECIMAL64 for divisions); no I/O. Each returns empty when the series is too short,
 * never a guessed value. Conventions are the common textbook ones and are stated per method.
 */
public final class Indicators {

  public static final MathContext MC = MathContext.DECIMAL64;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private Indicators() {}

  /** Simple moving average of the last {@code n} values. */
  public static Optional<BigDecimal> sma(List<BigDecimal> values, int n) {
    if (n <= 0 || values.size() < n) {
      return Optional.empty();
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal v : values.subList(values.size() - n, values.size())) {
      sum = sum.add(v);
    }
    return Optional.of(sum.divide(BigDecimal.valueOf(n), MC));
  }

  /**
   * Exponential moving average, seeded with the SMA of the first {@code n} values, smoothing 2 / (n
   * + 1). Needs at least {@code n} values.
   */
  public static Optional<BigDecimal> ema(List<BigDecimal> values, int n) {
    if (n <= 0 || values.size() < n) {
      return Optional.empty();
    }
    BigDecimal k = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(n + 1L), MC);
    BigDecimal ema = sma(values.subList(0, n), n).orElseThrow();
    for (BigDecimal v : values.subList(n, values.size())) {
      ema = v.subtract(ema).multiply(k, MC).add(ema, MC);
    }
    return Optional.of(ema);
  }

  /**
   * Relative Strength Index with Wilder's smoothing: the first average gain and loss are simple
   * averages over {@code n} changes, then avg = (prev * (n - 1) + current) / n. Needs n + 1 values.
   * Returns 100 when there are no losses.
   */
  public static Optional<BigDecimal> rsi(List<BigDecimal> closes, int n) {
    if (n <= 0 || closes.size() < n + 1) {
      return Optional.empty();
    }
    BigDecimal gain = BigDecimal.ZERO;
    BigDecimal loss = BigDecimal.ZERO;
    for (int i = 1; i <= n; i++) {
      BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
      if (change.signum() > 0) {
        gain = gain.add(change);
      } else {
        loss = loss.add(change.negate());
      }
    }
    BigDecimal bn = BigDecimal.valueOf(n);
    BigDecimal avgGain = gain.divide(bn, MC);
    BigDecimal avgLoss = loss.divide(bn, MC);
    BigDecimal nMinus1 = BigDecimal.valueOf(n - 1L);
    for (int i = n + 1; i < closes.size(); i++) {
      BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
      BigDecimal g = change.signum() > 0 ? change : BigDecimal.ZERO;
      BigDecimal l = change.signum() < 0 ? change.negate() : BigDecimal.ZERO;
      avgGain = avgGain.multiply(nMinus1).add(g).divide(bn, MC);
      avgLoss = avgLoss.multiply(nMinus1).add(l).divide(bn, MC);
    }
    if (avgLoss.signum() == 0) {
      return Optional.of(HUNDRED);
    }
    BigDecimal rs = avgGain.divide(avgLoss, MC);
    return Optional.of(HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MC)));
  }

  /**
   * Average True Range with Wilder's smoothing. True range = max(high - low, |high - previous
   * close|, |low - previous close|). Needs n + 1 bars.
   */
  public static Optional<BigDecimal> atr(
      List<BigDecimal> highs, List<BigDecimal> lows, List<BigDecimal> closes, int n) {
    int size = closes.size();
    if (n <= 0 || size < n + 1 || highs.size() != size || lows.size() != size) {
      return Optional.empty();
    }
    BigDecimal first = BigDecimal.ZERO;
    for (int i = 1; i <= n; i++) {
      first = first.add(trueRange(highs.get(i), lows.get(i), closes.get(i - 1)));
    }
    BigDecimal bn = BigDecimal.valueOf(n);
    BigDecimal atr = first.divide(bn, MC);
    for (int i = n + 1; i < size; i++) {
      BigDecimal tr = trueRange(highs.get(i), lows.get(i), closes.get(i - 1));
      atr = atr.multiply(BigDecimal.valueOf(n - 1L)).add(tr).divide(bn, MC);
    }
    return Optional.of(atr);
  }

  static BigDecimal trueRange(BigDecimal high, BigDecimal low, BigDecimal prevClose) {
    return high.subtract(low)
        .max(high.subtract(prevClose).abs())
        .max(low.subtract(prevClose).abs());
  }

  /** Highest of the last {@code n} values, optionally skipping the most recent {@code skip}. */
  public static Optional<BigDecimal> highest(List<BigDecimal> values, int n, int skip) {
    return window(values, n, skip).map(w -> w.stream().reduce(BigDecimal::max).orElseThrow());
  }

  /** Lowest of the last {@code n} values, optionally skipping the most recent {@code skip}. */
  public static Optional<BigDecimal> lowest(List<BigDecimal> values, int n, int skip) {
    return window(values, n, skip).map(w -> w.stream().reduce(BigDecimal::min).orElseThrow());
  }

  /** Fractional change over {@code n} periods: last / value n periods earlier - 1. */
  public static Optional<BigDecimal> change(List<BigDecimal> values, int n) {
    if (n <= 0 || values.size() < n + 1) {
      return Optional.empty();
    }
    BigDecimal then = values.get(values.size() - 1 - n);
    if (then.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(values.get(values.size() - 1).divide(then, MC).subtract(BigDecimal.ONE));
  }

  /** Largest absolute one-period fractional change within the last {@code n} periods. */
  public static Optional<BigDecimal> largestMove(List<BigDecimal> values, int n) {
    if (n <= 0 || values.size() < n + 1) {
      return Optional.empty();
    }
    BigDecimal max = BigDecimal.ZERO;
    for (int i = values.size() - n; i < values.size(); i++) {
      BigDecimal prev = values.get(i - 1);
      if (prev.signum() > 0) {
        max = max.max(values.get(i).divide(prev, MC).subtract(BigDecimal.ONE).abs());
      }
    }
    return Optional.of(max);
  }

  /** Percentage rounded for display, for example 0.12345 becomes 12.35. */
  public static BigDecimal percent(BigDecimal fraction) {
    return fraction.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
  }

  private static Optional<List<BigDecimal>> window(List<BigDecimal> values, int n, int skip) {
    if (n <= 0 || skip < 0 || values.size() < n + skip) {
      return Optional.empty();
    }
    int end = values.size() - skip;
    return Optional.of(values.subList(end - n, end));
  }
}
