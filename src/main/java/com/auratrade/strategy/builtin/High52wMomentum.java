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
 * 52-week high momentum (spec section 3, strategy 4). The close is within a set tolerance of the
 * highest high of the prior 252 sessions (or above it), the share is in an uptrend (above its 50-
 * and 200-day EMAs), it has outperformed Nifty 50 over six months, and volume is above its
 * 20-session average on the day. The entry is a buy-stop above the day's high.
 *
 * <p>Simplifications against the spec, each stated in ADR 0046: volume expansion is required as a
 * ratio of at least {@code minVolumeRatio} to the 20-session average, not as a comparison with the
 * breakout's own volume; the fundamental check (no major deterioration in earnings) is reported
 * unknown and kept out of the required conditions, since no fundamentals source exists (plan
 * decision D6). The stop is the lower of the prior 52-week high less a buffer and the last swing
 * low.
 */
public final class High52wMomentum implements Strategy {

  public static final String ID = "high-52w-momentum";

  static final ParameterSchema SCHEMA =
      ParameterSchema.builder()
          .decimal(
              "nearHighTolerance",
              "Close within this fraction of the prior 52-week high",
              "0.01",
              "0.10",
              "0.05")
          .decimal("minVolumeRatio", "Volume on the day over the 20-session average", "1", "2", "1")
          .decimal("entryBuffer", "Buy-stop trigger above the day's high", "0", "0.01", "0.001")
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
          .integer("maxHold", "Holding limit in sessions", 5, 20, 20)
          .build();

  private static final int EMA_MID = 50;
  private static final int EMA_SLOW = 200;
  private static final int YEAR = 252;
  private static final int HALF_YEAR = 126;
  private static final int VOLUME_AVERAGE = 20;
  private static final int SWING_STRENGTH = 2;
  private static final int ATR_PERIOD = 14;
  private static final BigDecimal HALF = new BigDecimal("0.5");
  private static final MathContext MC = MathContext.DECIMAL64;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "52-week high momentum";
  }

  @Override
  public String description() {
    return "Close at or near the highest high of the prior 52 weeks, above the 50- and 200-day EMAs,"
        + " outperforming Nifty 50 over six months, on above-average volume.";
  }

  @Override
  public ParameterSchema parameterSchema() {
    return SCHEMA;
  }

  @Override
  public Set<DataRequirement> requires() {
    return Set.of(
        DataRequirement.DAILY_CANDLES, DataRequirement.BENCHMARK, DataRequirement.FUNDAMENTALS);
  }

  @Override
  public int lookback(Parameters p) {
    return YEAR + 1;
  }

  @Override
  public Assessment assess(Series share, StrategyContext context, Parameters p) {
    List<Candle> candles = share.candles();
    List<BigDecimal> closes = column(candles, Candle::close);
    List<BigDecimal> highs = column(candles, Candle::high);
    List<BigDecimal> lows = column(candles, Candle::low);
    List<BigDecimal> volumes = candles.stream().map(c -> BigDecimal.valueOf(c.volume())).toList();
    Candle today = candles.get(candles.size() - 1);
    BigDecimal close = today.close();

    Optional<BigDecimal> priorHigh = Indicators.highest(highs, YEAR, 1);
    Optional<BigDecimal> ema50 = Indicators.ema(closes, EMA_MID);
    Optional<BigDecimal> ema200 = Indicators.ema(closes, EMA_SLOW);
    Optional<BigDecimal> volume = Indicators.ratioToAverage(volumes, VOLUME_AVERAGE);
    Optional<BigDecimal> halfYear = RelativeReturn.excess(share, context, HALF_YEAR);
    Optional<BigDecimal> swingLow =
        Indicators.lastSwingLow(lows, SWING_STRENGTH).map(Indicators.Swing::value);

    BigDecimal trigger = today.high().multiply(BigDecimal.ONE.add(p.decimal("entryBuffer")), MC);
    Optional<BigDecimal> stop =
        priorHigh.map(h -> {
          BigDecimal structural = h.multiply(BigDecimal.ONE.subtract(p.decimal("stopBuffer")), MC);
          return swingLow.map(s -> s.min(structural)).orElse(structural);
        });
    BigDecimal maxStop = p.decimal("maxStopDistance");

    Optional<BigDecimal> nearHigh =
        priorHigh.map(h -> close.divide(h, MC).subtract(BigDecimal.ONE));
    BigDecimal tolerance = p.decimal("nearHighTolerance").negate();
    List<ConditionResult> conditions = new ArrayList<>();
    conditions.add(
        ConditionResult.check(
            "near_52w_high",
            "Close within the tolerance of the prior 52-week high",
            nearHigh,
            v -> v.compareTo(tolerance) >= 0,
            ">= " + tolerance.toPlainString()));
    conditions.add(
        ConditionResult.check(
            "close_above_ema50",
            "Close above the 50-day EMA",
            ema50.map(close::subtract),
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "close_above_ema200",
            "Close above the 200-day EMA",
            ema200.map(close::subtract),
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "outperforms_nifty_6m",
            "Six-month return ahead of Nifty 50",
            halfYear,
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "volume_expansion",
            "Volume on the day against the 20-session average",
            volume,
            v -> v.compareTo(p.decimal("minVolumeRatio")) >= 0,
            ">= " + p.decimal("minVolumeRatio").toPlainString()));
    conditions.add(
        ConditionResult.check(
            "stop_distance",
            "Structural stop below the trigger, within the maximum",
            stop.map(s -> trigger.subtract(s).divide(trigger, MC)),
            v -> v.signum() > 0 && v.compareTo(maxStop) <= 0,
            "0 < distance <= " + maxStop.toPlainString()));
    conditions.add(
        ConditionResult.unavailable(
            "no_fundamental_deterioration",
            "No major deterioration in earnings (no fundamentals source; plan decision D6)",
            false,
            "not evaluated"));

    boolean requiredPass =
        conditions.stream().filter(c -> c.required()).allMatch(c -> c.outcome() == Outcome.PASS);
    Optional<TradePlan> plan =
        requiredPass && stop.isPresent()
            ? Optional.of(plan(trigger, stop.get(), p))
            : Optional.empty();
    BigDecimal quality = quality(volume, p.decimal("minVolumeRatio"), halfYear);
    String explanation =
        String.format(
            "Close %s against the prior 52-week high %s on %sx average volume",
            close.toPlainString(),
            priorHigh.map(BigDecimal::toPlainString).orElse("unknown"),
            volume.map(v -> v.setScale(1, RoundingMode.HALF_UP).toPlainString()).orElse("unknown"));
    return new Assessment(conditions, quality, explanation, plan);
  }

  private static TradePlan plan(BigDecimal trigger, BigDecimal stop, Parameters p) {
    BigDecimal maxPrice = trigger.multiply(BigDecimal.ONE.add(p.decimal("maxChase")), MC);
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, trigger, maxPrice, p.integer("entryWindow")),
        stop,
        "lower of the prior 52-week high less a buffer and the last confirmed swing low",
        List.of(new Target(p.decimal("targetR"), HALF)),
        new TrailingRule.Atr(ATR_PERIOD, new BigDecimal("2"), BigDecimal.ONE),
        new Invalidation.None(),
        p.integer("maxHold"));
  }

  /**
   * 50 at the minimum volume ratio, plus up to 20 for volume above it, plus up to 30 for six-month
   * excess.
   */
  static BigDecimal quality(
      Optional<BigDecimal> volume, BigDecimal minVolume, Optional<BigDecimal> halfYear) {
    BigDecimal score = new BigDecimal("50");
    if (volume.isPresent()) {
      BigDecimal extra =
          volume.get().subtract(minVolume).max(BigDecimal.ZERO).min(BigDecimal.valueOf(2));
      score = score.add(extra.multiply(BigDecimal.TEN));
    }
    if (halfYear.isPresent() && halfYear.get().signum() > 0) {
      score =
          score.add(halfYear.get().multiply(BigDecimal.valueOf(100)).min(BigDecimal.valueOf(30)));
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
