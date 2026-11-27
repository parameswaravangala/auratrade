package com.auratrade.strategy;

import com.auratrade.indicators.Indicators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes one {@link TradePlan} over daily bars (ADR 0040). Pure: bars in, a trade outcome out.
 * The backtest and paper trading both call it, so they cannot disagree about the exit rules.
 *
 * <p>Timing and fills, in the order they are applied:
 *
 * <ul>
 *   <li>Entry is looked for on the sessions after the signal, for {@code validSessions} sessions
 *       (BUY_STOP) or the next session only (NEXT_OPEN). A BUY_STOP fills at the open if the open
 *       is at or above the trigger, otherwise at the trigger if the high reaches it. A fill above
 *       the no-chase limit is not taken.
 *   <li>On each later session, in this order: an invalidation flagged on the previous close exits
 *       at the open; a gap through the stop exits at the open; a gap through a target sells that
 *       fraction at the open; an intraday low through the stop exits at the stop price; intraday
 *       highs through targets sell at the target price. Stops are checked before targets, so a bar
 *       that touches both counts as a stop (the pessimistic reading, since intraday order is
 *       unknown).
 *   <li>After the close: the invalidation level is tested; the holding limit exits at the close; a
 *       trailing stop is raised (never lowered) from bars up to and including this close, so it
 *       applies from the next session.
 * </ul>
 *
 * <p>Targets are the plan's {@code trigger + R x (trigger - stop)}, also when the entry filled
 * above the trigger, so the gate compares like with like. Nothing reads a bar after the one being
 * processed, except the close that closed it.
 */
public final class PlanExecutor {

  /** One session of prices. The high is at least the open, close and low. */
  public record Bar(
      LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    public Bar {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(open, "open");
      Objects.requireNonNull(high, "high");
      Objects.requireNonNull(low, "low");
      Objects.requireNonNull(close, "close");
      if (low.signum() <= 0 || high.compareTo(low) < 0) {
        throw new IllegalArgumentException(
            "bad bar on " + date + ": low " + low + ", high " + high);
      }
      if (open.compareTo(low) < 0
          || open.compareTo(high) > 0
          || close.compareTo(low) < 0
          || close.compareTo(high) > 0) {
        throw new IllegalArgumentException("open or close outside the bar on " + date);
      }
    }
  }

  public enum Status {
    /** No entry filled within the entry window. */
    NOT_FILLED,
    /** Filled and still holding at the last bar supplied. */
    OPEN,
    /** Fully exited. */
    CLOSED
  }

  public enum ExitReason {
    STOP,
    STOP_GAP,
    TARGET,
    TARGET_GAP,
    INVALIDATED,
    HOLDING_LIMIT
  }

  /** A sale of {@code fraction} of the original quantity. */
  public record Exit(LocalDate date, BigDecimal price, BigDecimal fraction, ExitReason reason) {}

  /**
   * @param entryDate and {@code entryPrice} are empty and null when not filled
   * @param exits in time order
   * @param openFraction what is still held, of the original quantity
   * @param sessionsHeld sessions from entry to the last exit, or to the last bar while open
   * @param realizedR fraction-weighted R of the exits so far: sum of fraction x (price - entry) /
   *     (entry - stop). Zero when not filled.
   */
  public record Result(
      Status status,
      Optional<LocalDate> entryDate,
      BigDecimal entryPrice,
      List<Exit> exits,
      BigDecimal openFraction,
      int sessionsHeld,
      BigDecimal realizedR) {}

  private PlanExecutor() {}

  /**
   * Runs {@code plan} over {@code bars}. {@code bars.get(signalIndex)} is the session the plan was
   * signalled on (its close is known); entries are looked for after it.
   */
  public static Result execute(TradePlan plan, List<Bar> bars, int signalIndex) {
    Objects.requireNonNull(plan, "plan");
    if (signalIndex < 0 || signalIndex >= bars.size()) {
      throw new IllegalArgumentException("signalIndex outside the bars");
    }
    TradePlan.Entry entry = plan.entry();
    int last =
        entry.type() == TradePlan.EntryType.NEXT_OPEN
            ? signalIndex + 1
            : signalIndex + entry.validSessions();
    int fillIndex = -1;
    BigDecimal fill = null;
    for (int t = signalIndex + 1; t <= Math.min(last, bars.size() - 1) && fillIndex < 0; t++) {
      Optional<BigDecimal> price = entryPrice(entry, bars.get(t));
      if (price.isPresent() && price.get().compareTo(entry.maxPrice()) <= 0) {
        fillIndex = t;
        fill = price.get();
      }
    }
    if (fillIndex < 0) {
      return new Result(
          Status.NOT_FILLED,
          Optional.empty(),
          null,
          List.of(),
          BigDecimal.ZERO,
          0,
          BigDecimal.ZERO);
    }
    return manage(plan, bars, fillIndex, fill);
  }

  /** The price at which this bar fills a BUY_STOP or NEXT_OPEN entry, empty if it does not. */
  static Optional<BigDecimal> entryPrice(TradePlan.Entry entry, Bar bar) {
    if (entry.type() == TradePlan.EntryType.NEXT_OPEN) {
      return Optional.of(bar.open());
    }
    BigDecimal trigger = entry.trigger();
    if (bar.open().compareTo(trigger) >= 0) {
      return Optional.of(bar.open());
    }
    if (bar.high().compareTo(trigger) >= 0) {
      return Optional.of(trigger);
    }
    return Optional.empty();
  }

  private static Result manage(TradePlan plan, List<Bar> bars, int entryIndex, BigDecimal fill) {
    BigDecimal stop = plan.stop();
    BigDecimal risk = plan.riskPerShare();
    List<TradePlan.Target> targets = plan.targets();
    List<Exit> exits = new ArrayList<>();
    BigDecimal remaining = BigDecimal.ONE;
    int nextTarget = 0;
    boolean invalidatePending = false;
    int lastIndex = entryIndex;

    for (int t = entryIndex; t < bars.size() && remaining.signum() > 0; t++) {
      Bar bar = bars.get(t);
      lastIndex = t;
      if (t > entryIndex) {
        if (invalidatePending) {
          remaining = exit(exits, bar.date(), bar.open(), remaining, ExitReason.INVALIDATED);
          break;
        }
        if (bar.open().compareTo(stop) <= 0) {
          remaining = exit(exits, bar.date(), bar.open(), remaining, ExitReason.STOP_GAP);
          break;
        }
        while (nextTarget < targets.size()
            && bar.open().compareTo(plan.targetPrice(targets.get(nextTarget))) >= 0) {
          remaining =
              sellTarget(
                  exits,
                  bar.date(),
                  bar.open(),
                  targets.get(nextTarget),
                  remaining,
                  ExitReason.TARGET_GAP);
          nextTarget++;
        }
        if (remaining.signum() == 0) {
          break;
        }
      }
      if (bar.low().compareTo(stop) <= 0) {
        remaining = exit(exits, bar.date(), stop, remaining, ExitReason.STOP);
        break;
      }
      while (nextTarget < targets.size()
          && bar.high().compareTo(plan.targetPrice(targets.get(nextTarget))) >= 0) {
        remaining =
            sellTarget(
                exits,
                bar.date(),
                plan.targetPrice(targets.get(nextTarget)),
                targets.get(nextTarget),
                remaining,
                ExitReason.TARGET);
        nextTarget++;
      }
      if (remaining.signum() == 0) {
        break;
      }
      // After the close.
      int held = t - entryIndex;
      if (plan.invalidation() instanceof TradePlan.Invalidation.CloseBelow c
          && bar.close().compareTo(c.level()) < 0) {
        invalidatePending = true;
      }
      if (held >= plan.maxHoldSessions()) {
        remaining = exit(exits, bar.date(), bar.close(), remaining, ExitReason.HOLDING_LIMIT);
        break;
      }
      stop = trail(plan, bars, entryIndex, t, fill, risk, stop);
    }

    Status status = remaining.signum() == 0 ? Status.CLOSED : Status.OPEN;
    int held = lastIndex - entryIndex;
    return new Result(
        status,
        Optional.of(bars.get(entryIndex).date()),
        fill,
        exits,
        remaining,
        held,
        realizedR(exits, fill, plan.stop()));
  }

  /** Raises the stop per the trailing rule, using bars up to and including {@code t}. */
  private static BigDecimal trail(
      TradePlan plan,
      List<Bar> bars,
      int entryIndex,
      int t,
      BigDecimal fill,
      BigDecimal risk,
      BigDecimal stop) {
    BigDecimal close = bars.get(t).close();
    BigDecimal candidate = null;
    switch (plan.trailing()) {
      case TradePlan.TrailingRule.None none -> {
        return stop;
      }
      case TradePlan.TrailingRule.Ema ema -> {
        if (close.subtract(fill).compareTo(ema.activateAtR().multiply(risk)) < 0) {
          return stop;
        }
        List<BigDecimal> closes = closes(bars, 0, t);
        candidate = Indicators.ema(closes, ema.period()).orElse(null);
      }
      case TradePlan.TrailingRule.Atr atr -> {
        if (close.subtract(fill).compareTo(atr.activateAtR().multiply(risk)) < 0) {
          return stop;
        }
        Optional<BigDecimal> a =
            Indicators.atr(
                column(bars, 0, t, Bar::high),
                column(bars, 0, t, Bar::low),
                closes(bars, 0, t),
                atr.period());
        BigDecimal highest =
            closes(bars, entryIndex, t).stream().reduce(BigDecimal::max).orElseThrow();
        candidate = a.map(v -> highest.subtract(atr.multiple().multiply(v))).orElse(null);
      }
    }
    if (candidate == null || candidate.compareTo(stop) <= 0) {
      return stop;
    }
    return candidate;
  }

  private static BigDecimal exit(
      List<Exit> exits, LocalDate date, BigDecimal price, BigDecimal remaining, ExitReason reason) {
    exits.add(new Exit(date, price, remaining, reason));
    return BigDecimal.ZERO;
  }

  private static BigDecimal sellTarget(
      List<Exit> exits,
      LocalDate date,
      BigDecimal price,
      TradePlan.Target target,
      BigDecimal remaining,
      ExitReason reason) {
    BigDecimal sold = target.fraction().min(remaining);
    exits.add(new Exit(date, price, sold, reason));
    return remaining.subtract(sold);
  }

  static BigDecimal realizedR(List<Exit> exits, BigDecimal fill, BigDecimal stop) {
    BigDecimal r = fill.subtract(stop);
    BigDecimal sum = BigDecimal.ZERO;
    for (Exit e : exits) {
      sum = sum.add(e.fraction().multiply(e.price().subtract(fill)));
    }
    return sum.divide(r, Indicators.MC).setScale(4, RoundingMode.HALF_UP);
  }

  private static List<BigDecimal> closes(List<Bar> bars, int from, int to) {
    List<BigDecimal> out = new ArrayList<>(to - from + 1);
    for (int i = from; i <= to; i++) {
      out.add(bars.get(i).close());
    }
    return out;
  }

  private static List<BigDecimal> column(
      List<Bar> bars, int from, int to, java.util.function.Function<Bar, BigDecimal> f) {
    List<BigDecimal> out = new ArrayList<>(to - from + 1);
    for (int i = from; i <= to; i++) {
      out.add(f.apply(bars.get(i)));
    }
    return out;
  }
}
