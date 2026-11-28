package com.auratrade.strategy.builtin;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.DataRequirement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 20 EMA pullback (spec section 3, strategy 2). In an uptrend (50-day EMA above the 200-day), the
 * price pulls back to the 20-day EMA, RSI(14) cools to 40 to 50, and a bullish reversal bar forms
 * on higher volume than the recent average. The stop sits below the reversal bar or the last
 * confirmed swing low, whichever is lower.
 *
 * <p>Entry: a buy-stop above the reversal bar's high. Target: {@code targetR} (default 2R) for half
 * the position. The spec's first target, the previous swing high, is not used: the plan model takes
 * R-multiples only, and a swing-high target needs its own plan type. Trail: EMA(20) from 1R.
 * Invalidation: a close below the reversal bar's low.
 *
 * <p>Defaults are the spec's ranges or conventional values. They are not tuned.
 */
public final class Pullback20Ema implements Strategy {

  public static final String ID = "pullback-20ema";

  static final ParameterSchema SCHEMA =
      ParameterSchema.builder()
          .decimal(
              "pullbackTolerance",
              "Low within this fraction of the 20-day EMA",
              "0.005",
              "0.05",
              "0.02")
          .decimal("rsiMin", "Lowest RSI(14) on the reversal day", "35", "50", "40")
          .decimal("rsiMax", "Highest RSI(14) on the reversal day", "45", "60", "50")
          .decimal(
              "recoveryVolumeRatio",
              "Reversal-day volume over the 10-session average",
              "1",
              "2",
              "1")
          .decimal(
              "entryBuffer", "Buy-stop trigger above the reversal day's high", "0", "0.01", "0.001")
          .decimal("maxChase", "Highest fill allowed above the trigger", "0", "0.05", "0.02")
          .decimal(
              "maxStopDistance",
              "Widest acceptable stop, as a fraction of the trigger",
              "0.02",
              "0.15",
              "0.08")
          .decimal(
              "stopBuffer", "Stop placed this far below the structure", "0.001", "0.02", "0.005")
          .decimal("targetR", "First target in R", "2", "3", "2")
          .integer("entryWindow", "Sessions a buy-stop stays valid", 1, 5, 3)
          .integer("maxHold", "Holding limit in sessions", 3, 12, 12)
          .lessOrEqual("rsiMin", "rsiMax")
          .build();

  private static final int EMA_FAST = 20;
  private static final int EMA_MID = 50;
  private static final int EMA_SLOW = 200;
  private static final int VOLUME_AVERAGE = 10;
  private static final int RSI_PERIOD = 14;
  private static final int SWING_STRENGTH = 2;
  private static final BigDecimal HALF = new BigDecimal("0.5");
  private static final MathContext MC = MathContext.DECIMAL64;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "20 EMA pullback";
  }

  @Override
  public String description() {
    return "In an uptrend (50-day EMA above the 200-day), a pullback to the 20-day EMA with RSI(14)"
        + " cooling to 40 to 50, then a bullish reversal bar on above-average volume.";
  }

  @Override
  public ParameterSchema parameterSchema() {
    return SCHEMA;
  }

  @Override
  public Set<DataRequirement> requires() {
    return Set.of(DataRequirement.DAILY_CANDLES);
  }

  @Override
  public int lookback(Parameters p) {
    return EMA_SLOW + 1;
  }

  @Override
  public Assessment assess(Series share, StrategyContext context, Parameters p) {
    List<Candle> candles = share.candles();
    List<BigDecimal> closes = column(candles, Candle::close);
    List<BigDecimal> highs = column(candles, Candle::high);
    List<BigDecimal> lows = column(candles, Candle::low);
    List<BigDecimal> volumes = candles.stream().map(c -> BigDecimal.valueOf(c.volume())).toList();
    int n = candles.size();
    Candle today = candles.get(n - 1);
    BigDecimal close = today.close();
    BigDecimal low = today.low();
    BigDecimal high = today.high();
    BigDecimal prevClose = closes.get(n - 2);

    Optional<BigDecimal> ema20 = Indicators.ema(closes, EMA_FAST);
    Optional<BigDecimal> ema50 = Indicators.ema(closes, EMA_MID);
    Optional<BigDecimal> ema200 = Indicators.ema(closes, EMA_SLOW);
    Optional<BigDecimal> rsi = Indicators.rsi(closes, RSI_PERIOD);
    Optional<BigDecimal> recovery = Indicators.ratioToAverage(volumes, VOLUME_AVERAGE);
    Optional<BigDecimal> swingLow =
        Indicators.lastSwingLow(lows, SWING_STRENGTH).map(Indicators.Swing::value);

    BigDecimal tolerance = p.decimal("pullbackTolerance");
    BigDecimal recoveryMin = p.decimal("recoveryVolumeRatio");
    BigDecimal rsiMin = p.decimal("rsiMin");
    BigDecimal rsiMax = p.decimal("rsiMax");
    BigDecimal maxStop = p.decimal("maxStopDistance");

    BigDecimal trigger = high.multiply(BigDecimal.ONE.add(p.decimal("entryBuffer")), MC);
    BigDecimal structural = low.multiply(BigDecimal.ONE.subtract(p.decimal("stopBuffer")), MC);
    BigDecimal stop = swingLow.map(s -> s.min(structural)).orElse(structural);
    Optional<BigDecimal> stopDistance = Optional.of(trigger.subtract(stop).divide(trigger, MC));

    Optional<BigDecimal> touch = ema20.map(e -> low.divide(e, MC).subtract(BigDecimal.ONE));
    List<ConditionResult> conditions = new ArrayList<>();
    conditions.add(
        ConditionResult.check(
            "uptrend_50_above_200",
            "50-day EMA above the 200-day EMA",
            ema50.flatMap(a -> ema200.map(a::subtract)),
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "close_above_ema50",
            "Close above the 50-day EMA",
            ema50.map(close::subtract),
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "low_touches_ema20",
            "Low within the tolerance of the 20-day EMA",
            touch.map(BigDecimal::abs),
            v -> v.compareTo(tolerance) <= 0,
            "<= " + tolerance.toPlainString()));
    conditions.add(
        ConditionResult.check(
            "rsi_cooled",
            "RSI(14) cooled into the pullback range",
            rsi,
            v -> v.compareTo(rsiMin) >= 0 && v.compareTo(rsiMax) <= 0,
            rsiMin.toPlainString() + " to " + rsiMax.toPlainString()));
    conditions.add(
        ConditionResult.check(
            "bullish_reversal",
            "Bullish reversal bar: up on the day and on the prior close",
            Optional.of(close.subtract(prevClose)),
            v -> v.signum() > 0 && close.compareTo(today.open()) > 0,
            "close above open and prior close"));
    conditions.add(
        ConditionResult.check(
            "recovery_volume",
            "Reversal-day volume against the 10-session average",
            recovery,
            v -> v.compareTo(recoveryMin) >= 0,
            ">= " + recoveryMin.toPlainString()));
    conditions.add(
        ConditionResult.check(
            "stop_distance",
            "Structural stop within the maximum distance",
            stopDistance,
            v -> v.compareTo(maxStop) <= 0,
            "<= " + maxStop.toPlainString()));

    boolean requiredPass = conditions.stream().allMatch(c -> c.outcome() == Outcome.PASS);
    Optional<TradePlan> plan =
        requiredPass ? Optional.of(plan(low, trigger, stop, p)) : Optional.empty();
    BigDecimal quality = quality(ema50, ema200, recovery, recoveryMin);
    String explanation =
        String.format(
            "Reversal on %s with low %s against the 20-day EMA %s, RSI %s",
            close.toPlainString(),
            low.toPlainString(),
            ema20.map(BigDecimal::toPlainString).orElse("unknown"),
            rsi.map(r -> r.setScale(1, RoundingMode.HALF_UP).toPlainString()).orElse("unknown"));
    return new Assessment(conditions, quality, explanation, plan);
  }

  private static TradePlan plan(BigDecimal low, BigDecimal trigger, BigDecimal stop, Parameters p) {
    BigDecimal maxPrice = trigger.multiply(BigDecimal.ONE.add(p.decimal("maxChase")), MC);
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, trigger, maxPrice, p.integer("entryWindow")),
        stop,
        "lower of the reversal low less a buffer and the last confirmed swing low",
        List.of(new Target(p.decimal("targetR"), HALF)),
        new TrailingRule.Ema(EMA_FAST, BigDecimal.ONE),
        new Invalidation.CloseBelow(low, "close back below the reversal bar's low"),
        p.integer("maxHold"));
  }

  /**
   * 50 at the minimum, plus up to 20 for volume above the minimum, plus 20 for a strong uptrend.
   */
  static BigDecimal quality(
      Optional<BigDecimal> ema50,
      Optional<BigDecimal> ema200,
      Optional<BigDecimal> recovery,
      BigDecimal recoveryMin) {
    BigDecimal score = new BigDecimal("50");
    if (recovery.isPresent()) {
      BigDecimal extra =
          recovery.get().subtract(recoveryMin).max(BigDecimal.ZERO).min(BigDecimal.valueOf(2));
      score = score.add(extra.multiply(BigDecimal.TEN));
    }
    if (ema50.isPresent() && ema200.isPresent() && ema200.get().signum() > 0) {
      BigDecimal spread = ema50.get().divide(ema200.get(), MC).subtract(BigDecimal.ONE);
      if (spread.compareTo(new BigDecimal("0.05")) > 0) {
        score = score.add(BigDecimal.valueOf(20));
      }
    }
    return score
        .min(BigDecimal.valueOf(100))
        .max(BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private static List<BigDecimal> column(List<Candle> candles, Function<Candle, BigDecimal> f) {
    return candles.stream().map(f).toList();
  }
}
