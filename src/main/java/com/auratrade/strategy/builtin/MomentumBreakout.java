package com.auratrade.strategy.builtin;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.DataRequirement;
import com.auratrade.strategy.MarketRegime;
import com.auratrade.strategy.Outcome;
import com.auratrade.strategy.ParameterSchema;
import com.auratrade.strategy.Parameters;
import com.auratrade.strategy.Strategy;
import com.auratrade.strategy.StrategyContext;
import com.auratrade.strategy.TradePlan;
import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.Target;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Momentum breakout (spec section 3, strategy 1). A daily close above the highest high of the prior
 * sessions, on at least 1.5 times the 20-session average volume, with the close above the 20- and
 * 50-day EMAs and RSI(14) between 55 and 70. Outperforming Nifty over three months is preferred, so
 * it is an optional condition that adds to the quality score but does not block.
 *
 * <p>Entry: a buy-stop at the breakout day's high plus a small buffer, with a no-chase limit and a
 * short validity window. Stop: the lower of the breakout level less a buffer and the last confirmed
 * swing low. The setup is rejected if that stop is wider than the configured maximum distance.
 * Target: 2R (configurable) for half the position, then an EMA(20) trail from 1R. Invalidation: a
 * close back below the breakout level.
 *
 * <p>Defaults are the spec's ranges and conventional values. They are not tuned; the gate tests
 * them on the training period before any use.
 */
public final class MomentumBreakout implements Strategy {

  public static final String ID = "momentum-breakout";

  static final ParameterSchema SCHEMA =
      ParameterSchema.builder()
          .integer(
              "resistanceLookback", "Prior sessions whose highest high is resistance", 20, 30, 20)
          .decimal(
              "minVolumeRatio", "Breakout volume over the 20-session average", "1.5", "2.0", "1.5")
          .decimal("rsiMin", "Lowest RSI(14) on the breakout day", "50", "60", "55")
          .decimal("rsiMax", "Highest RSI(14) on the breakout day", "65", "75", "70")
          .decimal(
              "entryBuffer", "Buy-stop trigger above the breakout day's high", "0", "0.01", "0.001")
          .decimal("maxChase", "Highest fill allowed above the trigger", "0", "0.05", "0.02")
          .decimal(
              "maxStopDistance",
              "Widest acceptable stop, as a fraction of the trigger",
              "0.02",
              "0.15",
              "0.08")
          .decimal(
              "stopBuffer",
              "Stop placed this far below the breakout level",
              "0.001",
              "0.02",
              "0.005")
          .decimal("targetR", "First target in R", "2", "3", "2")
          .integer("entryWindow", "Sessions a buy-stop stays valid", 1, 5, 3)
          .integer("maxHold", "Holding limit in sessions", 3, 15, 15)
          .integer("regimeFilter", "Block entries when NIFTY 50 is bearish (1 on, 0 off)", 0, 1, 0)
          .lessOrEqual("rsiMin", "rsiMax")
          .build();

  private static final int EMA_FAST = 20;
  private static final int EMA_SLOW = 50;
  private static final int VOLUME_AVERAGE = 20;
  private static final int RSI_PERIOD = 14;
  private static final int TREND_TRAIL = 20;
  private static final int OUTPERFORMANCE_SESSIONS = 63;
  private static final int SWING_STRENGTH = 2;
  private static final BigDecimal HALF = new BigDecimal("0.5");
  private static final MathContext MC = MathContext.DECIMAL64;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "Momentum breakout";
  }

  @Override
  public String description() {
    return "Daily close above the prior resistance on at least 1.5x average volume, in an uptrend"
        + " (above the 20- and 50-day EMAs) with RSI(14) between 55 and 70.";
  }

  @Override
  public ParameterSchema parameterSchema() {
    return SCHEMA;
  }

  @Override
  public Set<DataRequirement> requires() {
    return Set.of(DataRequirement.DAILY_CANDLES, DataRequirement.BENCHMARK);
  }

  @Override
  public int lookback(Parameters p) {
    // Resistance needs its window plus the breakout day; outperformance needs 63 sessions back.
    return Math.max(
        EMA_SLOW + 1, Math.max(p.integer("resistanceLookback") + 1, OUTPERFORMANCE_SESSIONS + 1));
  }

  @Override
  public Assessment assess(Series share, StrategyContext context, Parameters p) {
    List<Candle> candles = share.candles();
    List<BigDecimal> closes = column(candles, Candle::close);
    List<BigDecimal> highs = column(candles, Candle::high);
    List<BigDecimal> lows = column(candles, Candle::low);
    List<BigDecimal> volumes = candles.stream().map(c -> BigDecimal.valueOf(c.volume())).toList();
    BigDecimal close = closes.get(closes.size() - 1);
    BigDecimal high = highs.get(highs.size() - 1);

    Optional<BigDecimal> resistance = Indicators.highest(highs, p.integer("resistanceLookback"), 1);
    Optional<BigDecimal> ema20 = Indicators.ema(closes, EMA_FAST);
    Optional<BigDecimal> ema50 = Indicators.ema(closes, EMA_SLOW);
    Optional<BigDecimal> rsi = Indicators.rsi(closes, RSI_PERIOD);
    Optional<BigDecimal> volumeRatio = Indicators.ratioToAverage(volumes, VOLUME_AVERAGE);
    Optional<BigDecimal> swingLow =
        Indicators.lastSwingLow(lows, SWING_STRENGTH).map(Indicators.Swing::value);

    BigDecimal minVolume = p.decimal("minVolumeRatio");
    BigDecimal rsiMin = p.decimal("rsiMin");
    BigDecimal rsiMax = p.decimal("rsiMax");
    BigDecimal maxStopDistance = p.decimal("maxStopDistance");

    Optional<BigDecimal> stop =
        resistance.map(r -> stopLevel(r, swingLow, p.decimal("stopBuffer")));
    Optional<BigDecimal> stopDistance =
        stop.map(s -> trigger(high, p).subtract(s).divide(trigger(high, p), MC));

    List<ConditionResult> conditions =
        List.of(
            ConditionResult.check(
                "close_above_ema20",
                "Close above the 20-day EMA",
                ema20.map(close::subtract),
                v -> v.signum() > 0,
                "> 0"),
            ConditionResult.check(
                "close_above_ema50",
                "Close above the 50-day EMA",
                ema50.map(close::subtract),
                v -> v.signum() > 0,
                "> 0"),
            ConditionResult.check(
                "breakout_above_resistance",
                "Close above the highest high of the prior "
                    + p.integer("resistanceLookback")
                    + " sessions",
                resistance.map(close::subtract),
                v -> v.signum() > 0,
                "> 0"),
            ConditionResult.check(
                "volume_ratio",
                "Breakout volume against the 20-session average",
                volumeRatio,
                v -> v.compareTo(minVolume) >= 0,
                ">= " + minVolume.toPlainString()),
            ConditionResult.check(
                "rsi_in_range",
                "RSI(14) in the breakout range",
                rsi,
                v -> v.compareTo(rsiMin) >= 0 && v.compareTo(rsiMax) <= 0,
                rsiMin.toPlainString() + " to " + rsiMax.toPlainString()),
            ConditionResult.check(
                "stop_distance",
                "Structural stop within the maximum distance",
                stopDistance,
                v -> v.compareTo(maxStopDistance) <= 0,
                "<= " + maxStopDistance.toPlainString()));

    Optional<BigDecimal> outperformance = outperformance(share, context);
    List<ConditionResult> all = new java.util.ArrayList<>(conditions);
    all.add(
        ConditionResult.optional(
            "outperforms_nifty_3m",
            "Three-month return ahead of Nifty 50 (preferred)",
            outperformance,
            v -> v.signum() > 0,
            "> 0"));

    if (p.integer("regimeFilter") == 1) {
      all.add(MarketRegime.condition(context));
    }
    boolean requiredPass =
        all.stream().filter(ConditionResult::required).allMatch(c -> c.outcome() == Outcome.PASS);
    Optional<TradePlan> plan =
        requiredPass && resistance.isPresent() && stop.isPresent()
            ? Optional.of(plan(high, resistance.get(), stop.get(), p))
            : Optional.empty();

    BigDecimal quality = quality(volumeRatio, minVolume, outperformance);
    String explanation = explanation(close, resistance, volumeRatio, rsi, p);
    return new Assessment(all, quality, explanation, plan);
  }

  private static TradePlan plan(
      BigDecimal high, BigDecimal resistance, BigDecimal stop, Parameters p) {
    BigDecimal trigger = trigger(high, p);
    BigDecimal maxPrice = trigger.multiply(BigDecimal.ONE.add(p.decimal("maxChase")), MC);
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, trigger, maxPrice, p.integer("entryWindow")),
        stop,
        "lower of the breakout level less a buffer and the last confirmed swing low",
        List.of(new Target(p.decimal("targetR"), HALF)),
        new TrailingRule.Ema(TREND_TRAIL, BigDecimal.ONE),
        new Invalidation.CloseBelow(resistance, "close back below the breakout level"),
        p.integer("maxHold"));
  }

  private static BigDecimal trigger(BigDecimal high, Parameters p) {
    return high.multiply(BigDecimal.ONE.add(p.decimal("entryBuffer")), MC);
  }

  private static BigDecimal stopLevel(
      BigDecimal resistance, Optional<BigDecimal> swingLow, BigDecimal buffer) {
    BigDecimal belowLevel = resistance.multiply(BigDecimal.ONE.subtract(buffer), MC);
    return swingLow.map(s -> s.min(belowLevel)).orElse(belowLevel);
  }

  /**
   * Three-month return of the share less Nifty's over the same dates; empty if either is missing.
   */
  static Optional<BigDecimal> outperformance(Series share, StrategyContext context) {
    if (context.benchmark().isEmpty()) {
      return Optional.empty();
    }
    List<Candle> candles = share.candles();
    int n = candles.size();
    if (n <= OUTPERFORMANCE_SESSIONS) {
      return Optional.empty();
    }
    LocalDate start = candles.get(n - 1 - OUTPERFORMANCE_SESSIONS).tradingDate();
    Map<LocalDate, BigDecimal> bench = new HashMap<>();
    for (Candle c : context.benchmark().get().candles()) {
      bench.put(c.tradingDate(), c.close());
    }
    BigDecimal benchStart = bench.get(start);
    BigDecimal benchEnd = bench.get(context.asOf());
    if (benchStart == null || benchEnd == null || benchStart.signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal shareStart = candles.get(n - 1 - OUTPERFORMANCE_SESSIONS).close();
    BigDecimal shareEnd = candles.get(n - 1).close();
    BigDecimal shareReturn = shareEnd.divide(shareStart, MC).subtract(BigDecimal.ONE);
    BigDecimal benchReturn = benchEnd.divide(benchStart, MC).subtract(BigDecimal.ONE);
    return Optional.of(shareReturn.subtract(benchReturn));
  }

  /**
   * 50 at the minimum volume, plus up to 20 for volume above the minimum, plus 20 for
   * outperformance.
   */
  static BigDecimal quality(
      Optional<BigDecimal> volumeRatio, BigDecimal minVolume, Optional<BigDecimal> outperformance) {
    BigDecimal score = new BigDecimal("50");
    if (volumeRatio.isPresent()) {
      BigDecimal extra =
          volumeRatio.get().subtract(minVolume).max(BigDecimal.ZERO).min(BigDecimal.valueOf(2));
      score = score.add(extra.multiply(BigDecimal.TEN));
    }
    if (outperformance.isPresent() && outperformance.get().signum() > 0) {
      score = score.add(BigDecimal.valueOf(20));
    }
    return score
        .min(BigDecimal.valueOf(100))
        .max(BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private static String explanation(
      BigDecimal close,
      Optional<BigDecimal> resistance,
      Optional<BigDecimal> volumeRatio,
      Optional<BigDecimal> rsi,
      Parameters p) {
    if (resistance.isEmpty() || volumeRatio.isEmpty() || rsi.isEmpty()) {
      return "Not enough history to measure the breakout";
    }
    return String.format(
        "Closed at %s above the %d-session high of %s on %sx average volume, RSI %s",
        close.toPlainString(),
        p.integer("resistanceLookback"),
        resistance.get().toPlainString(),
        volumeRatio.get().setScale(1, RoundingMode.HALF_UP).toPlainString(),
        rsi.get().setScale(1, RoundingMode.HALF_UP).toPlainString());
  }

  private static List<BigDecimal> column(
      List<Candle> candles, java.util.function.Function<Candle, BigDecimal> f) {
    return candles.stream().map(f).toList();
  }
}
