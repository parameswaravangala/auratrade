package com.auratrade.strategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A conditional long-trade plan produced with a passing evaluation. Pure data, so the backtest and
 * paper trading execute the same plan (ADR 0038). Prices are per share in INR. R is the planned
 * risk per share: entry trigger minus stop.
 *
 * @param stop initial stop-loss; no rule ever widens it
 * @param stopReason why this stop, for example "below the 20-session breakout level"
 * @param targets partial exits in ascending R; their fractions of the original quantity sum to at
 *     most 1, and whatever remains exits by stop, trailing stop, invalidation or holding limit
 * @param maxHoldSessions sessions after entry at which any remaining position exits at the close
 */
public record TradePlan(
    Entry entry,
    BigDecimal stop,
    String stopReason,
    List<Target> targets,
    TrailingRule trailing,
    Invalidation invalidation,
    int maxHoldSessions) {

  /** How the position is entered. */
  public enum EntryType {
    /** Buy when a later session trades at or above the trigger; never above the maximum price. */
    BUY_STOP,
    /** Buy at the next session's open if it is at or below the maximum price. */
    NEXT_OPEN
  }

  /**
   * @param trigger the buy-stop price, or for NEXT_OPEN the reference price (the signal close)
   * @param maxPrice no-chase limit: no fill above this price
   * @param validSessions sessions after the signal during which the entry may fill
   */
  public record Entry(EntryType type, BigDecimal trigger, BigDecimal maxPrice, int validSessions) {
    public Entry {
      Objects.requireNonNull(type, "type");
      positive(trigger, "trigger");
      Objects.requireNonNull(maxPrice, "maxPrice");
      if (maxPrice.compareTo(trigger) < 0) {
        throw new IllegalArgumentException("maxPrice below trigger");
      }
      if (validSessions < 1) {
        throw new IllegalArgumentException("validSessions must be at least 1");
      }
    }
  }

  /** Sell {@code fraction} of the original quantity at trigger + rMultiple x R. */
  public record Target(BigDecimal rMultiple, BigDecimal fraction) {
    public Target {
      positive(rMultiple, "rMultiple");
      positive(fraction, "fraction");
      if (fraction.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException("fraction above 1");
      }
    }
  }

  /** How the stop follows the price after entry. A trailing stop only ever rises. */
  public sealed interface TrailingRule {
    /** Keep the initial stop. */
    record None() implements TrailingRule {}

    /** Stop follows the EMA of closes, once the trade is {@code activateAtR} in profit. */
    record Ema(int period, BigDecimal activateAtR) implements TrailingRule {
      public Ema {
        if (period < 2) {
          throw new IllegalArgumentException("period must be at least 2");
        }
        nonNegative(activateAtR, "activateAtR");
      }
    }

    /**
     * Stop at the highest close minus multiple x ATR(period), once {@code activateAtR} in profit.
     */
    record Atr(int period, BigDecimal multiple, BigDecimal activateAtR) implements TrailingRule {
      public Atr {
        if (period < 2) {
          throw new IllegalArgumentException("period must be at least 2");
        }
        positive(multiple, "multiple");
        nonNegative(activateAtR, "activateAtR");
      }
    }
  }

  /** A setup-specific exit besides the stop, judged on the daily close. */
  public sealed interface Invalidation {
    record None() implements Invalidation {}

    /** Exit at the next open after a daily close below {@code level}. */
    record CloseBelow(BigDecimal level, String reason) implements Invalidation {
      public CloseBelow {
        positive(level, "level");
        Objects.requireNonNull(reason, "reason");
      }
    }
  }

  public TradePlan {
    Objects.requireNonNull(entry, "entry");
    positive(stop, "stop");
    Objects.requireNonNull(stopReason, "stopReason");
    Objects.requireNonNull(trailing, "trailing");
    Objects.requireNonNull(invalidation, "invalidation");
    targets = List.copyOf(targets);
    if (stop.compareTo(entry.trigger()) >= 0) {
      throw new IllegalArgumentException("stop must be below the entry trigger");
    }
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal previous = BigDecimal.ZERO;
    for (Target t : targets) {
      if (t.rMultiple().compareTo(previous) <= 0) {
        throw new IllegalArgumentException("targets must be in strictly ascending R");
      }
      previous = t.rMultiple();
      total = total.add(t.fraction());
    }
    if (total.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("target fractions sum above 1");
    }
    if (invalidation instanceof Invalidation.CloseBelow c && c.level().compareTo(stop) <= 0) {
      throw new IllegalArgumentException("invalidation level must be above the stop");
    }
    if (maxHoldSessions < 1) {
      throw new IllegalArgumentException("maxHoldSessions must be at least 1");
    }
  }

  /** Planned risk per share, R = trigger - stop. */
  public BigDecimal riskPerShare() {
    return entry.trigger().subtract(stop);
  }

  /** Worst-case risk per share if the entry fills at the no-chase limit. Use it for sizing. */
  public BigDecimal maxRiskPerShare() {
    return entry.maxPrice().subtract(stop);
  }

  /** Target price at the planned entry: trigger + rMultiple x R. */
  public BigDecimal targetPrice(Target target) {
    return entry.trigger().add(target.rMultiple().multiply(riskPerShare()));
  }

  /** Reward to risk of the first target, empty when the plan has no target. */
  public Optional<BigDecimal> plannedRewardRisk() {
    return targets.stream().findFirst().map(Target::rMultiple);
  }

  private static void positive(BigDecimal v, String name) {
    Objects.requireNonNull(v, name);
    if (v.signum() <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  private static void nonNegative(BigDecimal v, String name) {
    Objects.requireNonNull(v, name);
    if (v.signum() < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
  }
}
