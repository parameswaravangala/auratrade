package com.auratrade.strategy;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Three-state market regime from NIFTY 50 (spec section 5, plan P-regime). Pure: closes in, a
 * classification with its evidence out. Fails closed: with too little history the regime is
 * unknown, and a strategy that requires the regime then does not pass.
 *
 * <p>Rules, all on the index close:
 *
 * <ul>
 *   <li>Bullish: close above the 50-day EMA, 50-day EMA above the 200-day, close above the 200-day,
 *       and the 50-day EMA rising over the last 10 sessions; at least three of these four hold, and
 *       the close is above the 200-day.
 *   <li>Bearish: the mirror image (at least three of four bearish, and the close below the
 *       200-day).
 *   <li>Sideways: anything else.
 * </ul>
 *
 * <p>Breadth and volatility (spec section 5) are not used: the index alone does not give them, and
 * no verified breadth source exists. The evidence string says what decided the state.
 */
public final class MarketRegime {

  public enum Regime {
    BULLISH,
    SIDEWAYS,
    BEARISH
  }

  /** The longest average used (200 sessions) plus the 10-session slope. */
  public static final int MIN_HISTORY = 201;

  private static final int SLOPE_SESSIONS = 10;
  private static final BigDecimal NOT_BEARISH = new BigDecimal("0.5");

  /** The regime on one session. {@code score} is 1 for bullish, 0.5 for sideways, 0 for bearish. */
  public record Snapshot(Regime regime, BigDecimal score, String evidence) {}

  private MarketRegime() {}

  /**
   * Classifies from index closes, oldest first; empty when there are fewer than {@link
   * #MIN_HISTORY}.
   */
  public static Optional<Snapshot> classify(List<BigDecimal> closes) {
    int n = closes.size();
    if (n < MIN_HISTORY) {
      return Optional.empty();
    }
    BigDecimal close = closes.get(n - 1);
    BigDecimal ema50 = Indicators.ema(closes, 50).orElseThrow();
    BigDecimal ema200 = Indicators.ema(closes, 200).orElseThrow();
    BigDecimal ema50Before =
        Indicators.ema(closes.subList(0, n - SLOPE_SESSIONS), 50).orElseThrow();
    boolean rising = ema50.compareTo(ema50Before) > 0;
    boolean falling = ema50.compareTo(ema50Before) < 0;
    boolean closeAbove50 = close.compareTo(ema50) > 0;
    boolean fiftyAbove200 = ema50.compareTo(ema200) > 0;
    boolean closeAbove200 = close.compareTo(ema200) > 0;
    int bullish = count(closeAbove50, fiftyAbove200, closeAbove200, rising);
    int bearish =
        count(
            close.compareTo(ema50) < 0,
            ema50.compareTo(ema200) < 0,
            close.compareTo(ema200) < 0,
            falling);

    String evidence =
        String.format(
            "NIFTY close %s, EMA50 %s, EMA200 %s, EMA50 %s over %d sessions",
            close.toPlainString(),
            ema50.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
            ema200.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
            rising ? "rising" : "falling",
            SLOPE_SESSIONS);
    if (bullish >= 3 && closeAbove200) {
      return Optional.of(new Snapshot(Regime.BULLISH, BigDecimal.ONE, evidence));
    }
    if (bearish >= 3 && close.compareTo(ema200) < 0) {
      return Optional.of(new Snapshot(Regime.BEARISH, BigDecimal.ZERO, evidence));
    }
    return Optional.of(new Snapshot(Regime.SIDEWAYS, NOT_BEARISH, evidence));
  }

  /**
   * The condition a strategy adds when its regime filter is on: the market is not bearish. Missing
   * index history makes it unknown, which blocks a setup that requires it.
   */
  public static ConditionResult condition(StrategyContext context) {
    Optional<Snapshot> snap = snapshot(context);
    return ConditionResult.check(
        "regime_not_bearish",
        "NIFTY 50 regime is not bearish (sideways or bullish)",
        snap.map(Snapshot::score),
        v -> v.compareTo(NOT_BEARISH) >= 0,
        ">= 0.5");
  }

  /** The regime on the context's session, from the benchmark window; empty without the history. */
  public static Optional<Snapshot> snapshot(StrategyContext context) {
    return context
        .benchmark()
        .flatMap(b -> classify(b.candles().stream().map(Candle::close).toList()));
  }

  private static int count(boolean... flags) {
    int c = 0;
    for (boolean f : flags) {
      if (f) {
        c++;
      }
    }
    return c;
  }
}
