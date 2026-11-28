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
 * Relative strength (spec section 3, strategy 3). The share beats Nifty 50 over one month and three
 * months (same dates), sits above its 20-, 50- and 200-day EMAs, and makes higher highs and higher
 * lows. The entry is a buy-stop above the day's high.
 *
 * <p>Simplifications against the spec, each stated in ADR 0046: the entry is a continuation
 * trigger, not a confirmed consolidation breakout or a support pullback; the sector rule (the
 * sector beats Nifty) is reported as unknown and kept out of the required conditions, because no
 * sector data is verified (plan decision D6); the trail is 2 x ATR(14), one of the spec's two
 * options, chosen up front and not switched later.
 */
public final class RelativeStrength implements Strategy {

  public static final String ID = "relative-strength";

  static final ParameterSchema SCHEMA =
      ParameterSchema.builder()
          .decimal(
              "trailAtrMultiple",
              "Trailing stop: this many ATR(14) below the highest close",
              "1.5",
              "4",
              "2")
          .decimal("entryBuffer", "Buy-stop trigger above the day's high", "0", "0.01", "0.001")
          .decimal("maxChase", "Highest fill allowed above the trigger", "0", "0.05", "0.02")
          .decimal(
              "maxStopDistance",
              "Widest acceptable stop, as a fraction of the trigger",
              "0.02",
              "0.15",
              "0.10")
          .decimal(
              "stopBuffer", "Stop placed this far below the structure", "0.001", "0.02", "0.005")
          .decimal("targetR", "First target in R", "2", "3", "2")
          .integer("entryWindow", "Sessions a buy-stop stays valid", 1, 5, 3)
          .integer("maxHold", "Holding limit in sessions", 5, 20, 20)
          .build();

  private static final int EMA_FAST = 20;
  private static final int EMA_MID = 50;
  private static final int EMA_SLOW = 200;
  private static final int MONTH = 21;
  private static final int QUARTER = 63;
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
    return "Relative strength";
  }

  @Override
  public String description() {
    return "Beats Nifty 50 over one and three months, above the 20-, 50- and 200-day EMAs, with higher"
        + " highs and higher lows. Entry on a buy-stop above the day's high.";
  }

  @Override
  public ParameterSchema parameterSchema() {
    return SCHEMA;
  }

  @Override
  public Set<DataRequirement> requires() {
    return Set.of(
        DataRequirement.DAILY_CANDLES, DataRequirement.BENCHMARK, DataRequirement.SECTOR_INDEX);
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
    Candle today = candles.get(candles.size() - 1);
    BigDecimal close = today.close();
    BigDecimal high = today.high();

    Optional<BigDecimal> ema20 = Indicators.ema(closes, EMA_FAST);
    Optional<BigDecimal> ema50 = Indicators.ema(closes, EMA_MID);
    Optional<BigDecimal> ema200 = Indicators.ema(closes, EMA_SLOW);
    Optional<BigDecimal> month = RelativeReturn.excess(share, context, MONTH);
    Optional<BigDecimal> quarter = RelativeReturn.excess(share, context, QUARTER);
    Optional<BigDecimal> structure =
        Indicators.higherHighsAndLows(highs, lows, SWING_STRENGTH)
            .map(b -> b ? BigDecimal.ONE : BigDecimal.ZERO);
    Optional<BigDecimal> swingLow =
        Indicators.lastSwingLow(lows, SWING_STRENGTH).map(Indicators.Swing::value);

    BigDecimal trigger = high.multiply(BigDecimal.ONE.add(p.decimal("entryBuffer")), MC);
    BigDecimal structural =
        today.low().multiply(BigDecimal.ONE.subtract(p.decimal("stopBuffer")), MC);
    BigDecimal stop = swingLow.map(s -> s.min(structural)).orElse(structural);
    BigDecimal maxStop = p.decimal("maxStopDistance");

    List<ConditionResult> conditions = new ArrayList<>();
    conditions.add(
        ConditionResult.check(
            "outperforms_nifty_1m",
            "One-month return ahead of Nifty 50",
            month,
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "outperforms_nifty_3m",
            "Three-month return ahead of Nifty 50",
            quarter,
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "close_above_ema20",
            "Close above the 20-day EMA",
            ema20.map(close::subtract),
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
            "close_above_ema200",
            "Close above the 200-day EMA",
            ema200.map(close::subtract),
            v -> v.signum() > 0,
            "> 0"));
    conditions.add(
        ConditionResult.check(
            "higher_highs_lows",
            "Higher highs and higher lows",
            structure,
            v -> v.compareTo(BigDecimal.ONE) == 0,
            "true"));
    conditions.add(
        ConditionResult.check(
            "stop_distance",
            "Structural stop below the trigger, within the maximum",
            Optional.of(trigger.subtract(stop).divide(trigger, MC)),
            v -> v.signum() > 0 && v.compareTo(maxStop) <= 0,
            "0 < distance <= " + maxStop.toPlainString()));
    conditions.add(
        ConditionResult.unavailable(
            "sector_outperforms",
            "Sector index ahead of Nifty 50 (no verified sector data; plan decision D6)",
            false,
            "not evaluated"));

    boolean requiredPass =
        conditions.stream().filter(c -> c.required()).allMatch(c -> c.outcome() == Outcome.PASS);
    Optional<TradePlan> plan =
        requiredPass ? Optional.of(plan(trigger, stop, p)) : Optional.empty();
    BigDecimal quality = quality(quarter);
    String explanation =
        String.format(
            "Three-month return %s ahead of Nifty, close %s above the 200-day EMA",
            quarter
                .map(q ->
                    q.multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                            .toPlainString()
                        + "%")
                .orElse("unknown"),
            ema200.map(e -> close.subtract(e).toPlainString()).orElse("unknown"));
    return new Assessment(conditions, quality, explanation, plan);
  }

  private static TradePlan plan(BigDecimal trigger, BigDecimal stop, Parameters p) {
    BigDecimal maxPrice = trigger.multiply(BigDecimal.ONE.add(p.decimal("maxChase")), MC);
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, trigger, maxPrice, p.integer("entryWindow")),
        stop,
        "lower of the day's low less a buffer and the last confirmed swing low",
        List.of(new Target(p.decimal("targetR"), HALF)),
        new TrailingRule.Atr(ATR_PERIOD, p.decimal("trailAtrMultiple"), BigDecimal.ONE),
        new Invalidation.None(),
        p.integer("maxHold"));
  }

  /** 50 at zero excess return, plus 100 points per unit of three-month excess, capped at 100. */
  static BigDecimal quality(Optional<BigDecimal> quarter) {
    BigDecimal score = new BigDecimal("50");
    if (quarter.isPresent()) {
      score = score.add(quarter.get().multiply(BigDecimal.valueOf(100)));
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
