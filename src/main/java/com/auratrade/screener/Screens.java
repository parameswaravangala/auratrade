package com.auratrade.screener;

import static com.auratrade.indicators.Indicators.MC;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in screens. Thresholds are starting values chosen to be conventional, not tuned; the
 * backtest slice should measure them before anyone relies on them.
 */
final class Screens {

  private Screens() {}

  static List<Screen> all() {
    return List.of(new Breakout(), new Pullback(), new RelativeStrength());
  }

  static List<BigDecimal> closes(Series s) {
    return s.candles().stream().map(Candle::close).toList();
  }

  static List<BigDecimal> highs(Series s) {
    return s.candles().stream().map(Candle::high).toList();
  }

  static List<BigDecimal> volumes(Series s) {
    return s.candles().stream().map(c -> BigDecimal.valueOf(c.volume())).toList();
  }

  static BigDecimal last(List<BigDecimal> values) {
    return values.get(values.size() - 1);
  }

  /** Close above the highest high of the previous 252 sessions, on at least twice usual volume. */
  static final class Breakout implements Screen {
    static final BigDecimal MIN_VOLUME_RATIO = BigDecimal.valueOf(2);

    @Override
    public String name() {
      return "52w-high-breakout";
    }

    @Override
    public String description() {
      return "Close above the highest high of the previous 52 weeks (252 sessions) on at least"
          + " 2x the 50-day average volume. Ranked by volume ratio.";
    }

    @Override
    public int lookback() {
      return 253;
    }

    @Override
    public Optional<Hit> evaluate(Series s, Context context) {
      List<BigDecimal> closes = closes(s);
      List<BigDecimal> volumes = volumes(s);
      Optional<BigDecimal> priorHigh = Indicators.highest(highs(s), 252, 1);
      Optional<BigDecimal> avgVolume = Indicators.sma(volumes.subList(0, volumes.size() - 1), 50);
      if (priorHigh.isEmpty() || avgVolume.isEmpty() || avgVolume.get().signum() == 0) {
        return Optional.empty();
      }
      BigDecimal close = last(closes);
      BigDecimal ratio = last(volumes).divide(avgVolume.get(), MC);
      if (close.compareTo(priorHigh.get()) <= 0 || ratio.compareTo(MIN_VOLUME_RATIO) < 0) {
        return Optional.empty();
      }
      Map<String, BigDecimal> m = new LinkedHashMap<>();
      m.put("close", close);
      m.put("prior52wHigh", priorHigh.get());
      m.put(
          "pctAboveHigh",
          Indicators.percent(close.divide(priorHigh.get(), MC).subtract(BigDecimal.ONE)));
      m.put("volumeRatio", ratio);
      return Optional.of(
          Hit.of(
              s,
              ratio,
              m,
              String.format(
                  "Closed at %s above the 52-week high of %s on %sx average volume",
                  close.toPlainString(),
                  priorHigh.get().toPlainString(),
                  ratio.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString())));
    }
  }

  /**
   * Uptrend (close and 50-day average above the 200-day) that has pulled back to within 3% of the
   * 50-day average with RSI(14) between 40 and 55.
   */
  static final class Pullback implements Screen {
    static final BigDecimal MAX_DISTANCE = new BigDecimal("0.03");
    static final BigDecimal RSI_LOW = BigDecimal.valueOf(40);
    static final BigDecimal RSI_HIGH = BigDecimal.valueOf(55);

    @Override
    public String name() {
      return "pullback-to-50dma";
    }

    @Override
    public String description() {
      return "Uptrend (close and 50-day average above the 200-day average) pulled back to within"
          + " 3% of the 50-day average, RSI(14) between 40 and 55. Ranked by trend strength.";
    }

    @Override
    public int lookback() {
      return 201;
    }

    @Override
    public Optional<Hit> evaluate(Series s, Context context) {
      List<BigDecimal> closes = closes(s);
      Optional<BigDecimal> sma50 = Indicators.sma(closes, 50);
      Optional<BigDecimal> sma200 = Indicators.sma(closes, 200);
      Optional<BigDecimal> rsi = Indicators.rsi(closes, 14);
      if (sma50.isEmpty() || sma200.isEmpty() || rsi.isEmpty()) {
        return Optional.empty();
      }
      BigDecimal close = last(closes);
      BigDecimal distance = close.divide(sma50.get(), MC).subtract(BigDecimal.ONE);
      boolean uptrend =
          close.compareTo(sma200.get()) > 0 && sma50.get().compareTo(sma200.get()) > 0;
      boolean near = distance.abs().compareTo(MAX_DISTANCE) <= 0;
      boolean rsiOk = rsi.get().compareTo(RSI_LOW) >= 0 && rsi.get().compareTo(RSI_HIGH) <= 0;
      if (!uptrend || !near || !rsiOk) {
        return Optional.empty();
      }
      BigDecimal trend = sma50.get().divide(sma200.get(), MC).subtract(BigDecimal.ONE);
      Map<String, BigDecimal> m = new LinkedHashMap<>();
      m.put("close", close);
      m.put("sma50", sma50.get());
      m.put("sma200", sma200.get());
      m.put("pctFromSma50", Indicators.percent(distance));
      m.put("rsi14", rsi.get());
      m.put("trendPct", Indicators.percent(trend));
      return Optional.of(
          Hit.of(
              s,
              trend,
              m,
              String.format(
                  "In an uptrend (50-day %s%% above 200-day), now %s%% from the 50-day average,"
                      + " RSI %s",
                  Indicators.percent(trend).toPlainString(),
                  Indicators.percent(distance).toPlainString(),
                  rsi.get().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString())));
    }
  }

  /** Six-month return ahead of the benchmark, still above the 50-day average. */
  static final class RelativeStrength implements Screen {

    @Override
    public String name() {
      return "relative-strength-leaders";
    }

    @Override
    public String description() {
      return "Six-month (126-session) return ahead of Nifty 50 and close above the 50-day"
          + " average. Ranked by outperformance.";
    }

    @Override
    public int lookback() {
      return 127;
    }

    @Override
    public boolean needsBenchmark() {
      return true;
    }

    @Override
    public Optional<Hit> evaluate(Series s, Context context) {
      List<BigDecimal> closes = closes(s);
      Optional<BigDecimal> ret = Indicators.change(closes, 126);
      Optional<BigDecimal> benchRet =
          context.benchmark().flatMap(b -> Indicators.change(closes(b), 126));
      Optional<BigDecimal> sma50 = Indicators.sma(closes, 50);
      if (ret.isEmpty() || benchRet.isEmpty() || sma50.isEmpty()) {
        return Optional.empty();
      }
      BigDecimal rs =
          BigDecimal.ONE
              .add(ret.get())
              .divide(BigDecimal.ONE.add(benchRet.get()), MC)
              .subtract(BigDecimal.ONE);
      BigDecimal close = last(closes);
      if (rs.signum() <= 0 || close.compareTo(sma50.get()) <= 0) {
        return Optional.empty();
      }
      Map<String, BigDecimal> m = new LinkedHashMap<>();
      m.put("close", close);
      m.put("return6mPct", Indicators.percent(ret.get()));
      m.put("benchmark6mPct", Indicators.percent(benchRet.get()));
      m.put("outperformancePct", Indicators.percent(rs));
      m.put("sma50", sma50.get());
      return Optional.of(
          Hit.of(
              s,
              rs,
              m,
              String.format(
                  "Up %s%% in six months against %s%% for Nifty 50, above its 50-day average",
                  Indicators.percent(ret.get()).toPlainString(),
                  Indicators.percent(benchRet.get()).toPlainString())));
    }
  }
}
