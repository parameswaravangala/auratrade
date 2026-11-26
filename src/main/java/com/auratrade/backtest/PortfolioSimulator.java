package com.auratrade.backtest;

import com.auratrade.screener.indicators.Indicators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simulates an account trading screen signals, one session at a time (ADR 0022). Pure: prices,
 * signals and settings in, trades and an equity curve out. Money is BigDecimal throughout.
 *
 * <p>Each session: (1) exits, checked in this order: a one-day gap over 40% versus the previous
 * close (likely an unadjusted corporate action) closes at the previous close; an open at or below
 * the stop closes at the open; a low at or below the stop closes at the stop; reaching the holding
 * limit closes at the close. (2) Entries from the previous session's signals, best score first, at
 * this session's open plus slippage, while slots and cash allow. Size = the smaller of (equity x
 * risk per trade) / (entry - stop) and (equity x maximum position) / entry, in whole shares; the
 * stop is entry - ATR multiple x ATR(14) as of the signal session. (3) Mark to market at the close.
 *
 * <p>Known simplifications: exits and entries on the same open both use that session's open, so
 * cash freed by a morning exit is available to a morning entry; no intraday order of events beyond
 * the rules above; no partial fills or liquidity limits.
 */
final class PortfolioSimulator {

  static final BigDecimal MAX_GAP = new BigDecimal("0.40");

  /** One candidate entry: signalled at the close of {@code date}. */
  record Signal(LocalDate date, long instrumentId, String symbol, BigDecimal score) {}

  record Settings(
      BigDecimal initialCapital,
      int maxPositions,
      BigDecimal riskPerTrade,
      BigDecimal maxPositionFraction,
      int atrPeriod,
      BigDecimal atrMultiple,
      int maxHoldSessions,
      int slippageBps,
      CostModel costs,
      boolean regimeFilter) {}

  /** Length of the benchmark average used by the regime filter. */
  static final int REGIME_AVERAGE = 200;

  record Trade(
      String symbol,
      LocalDate entryDate,
      BigDecimal entryPrice,
      long quantity,
      LocalDate exitDate,
      BigDecimal exitPrice,
      String exitReason,
      int sessionsHeld,
      BigDecimal costs,
      BigDecimal netPnl) {}

  record Day(
      LocalDate date, BigDecimal equity, BigDecimal cash, int positions, BigDecimal benchmark) {}

  record Outcome(List<Trade> trades, List<Day> days, BigDecimal totalCosts) {}

  private static final class Position {
    final long id;
    final String symbol;
    final long qty;
    final BigDecimal entry;
    final BigDecimal stop;
    final LocalDate entryDate;
    final BigDecimal entryCost;
    int sessions;

    Position(
        long id,
        String symbol,
        long qty,
        BigDecimal entry,
        BigDecimal stop,
        LocalDate d,
        BigDecimal cost) {
      this.id = id;
      this.symbol = symbol;
      this.qty = qty;
      this.entry = entry;
      this.stop = stop;
      this.entryDate = d;
      this.entryCost = cost;
    }
  }

  private PortfolioSimulator() {}

  static Outcome run(
      List<LocalDate> sessions,
      Map<Long, PriceTable> prices,
      Map<LocalDate, List<Signal>> signalsByDate,
      PriceTable benchmark,
      Settings s) {
    BigDecimal cash = s.initialCapital();
    Map<Long, Position> open = new LinkedHashMap<>();
    List<Trade> trades = new ArrayList<>();
    List<Day> days = new ArrayList<>();
    BigDecimal totalCosts = BigDecimal.ZERO;
    BigDecimal slip = BigDecimal.valueOf(s.slippageBps()).movePointLeft(4);
    BigDecimal prevEquity = s.initialCapital();
    BigDecimal benchBase = null;

    for (int d = 0; d < sessions.size(); d++) {
      LocalDate today = sessions.get(d);

      // 1. Exits.
      for (var it = open.values().iterator(); it.hasNext(); ) {
        Position p = it.next();
        PriceTable t = prices.get(p.id);
        int i = t.indexOf(today);
        if (i < 0) {
          continue; // no bar today (suspended); keep holding
        }
        BigDecimal prevClose = i > 0 ? t.close(i - 1) : p.entry;
        BigDecimal exitPrice = null;
        String reason = null;
        BigDecimal gap = t.open(i).divide(prevClose, Indicators.MC).subtract(BigDecimal.ONE).abs();
        if (gap.compareTo(MAX_GAP) > 0) {
          exitPrice = prevClose;
          reason = "CORPORATE_ACTION";
        } else if (t.open(i).compareTo(p.stop) <= 0) {
          exitPrice = slipDown(t.open(i), slip);
          reason = "STOP_GAP";
        } else if (t.low(i).compareTo(p.stop) <= 0) {
          exitPrice = slipDown(p.stop, slip);
          reason = "STOP";
        } else {
          p.sessions++;
          if (p.sessions >= s.maxHoldSessions()) {
            exitPrice = slipDown(t.close(i), slip);
            reason = "TIME";
          }
        }
        if (exitPrice != null) {
          if (!"TIME".equals(reason)) {
            p.sessions++;
          }
          BigDecimal proceeds = exitPrice.multiply(BigDecimal.valueOf(p.qty));
          BigDecimal cost = s.costs().sellCost(proceeds);
          cash = cash.add(proceeds).subtract(cost);
          totalCosts = totalCosts.add(cost);
          BigDecimal pnl =
              proceeds
                  .subtract(p.entry.multiply(BigDecimal.valueOf(p.qty)))
                  .subtract(cost)
                  .subtract(p.entryCost);
          trades.add(
              new Trade(
                  p.symbol,
                  p.entryDate,
                  p.entry,
                  p.qty,
                  today,
                  exitPrice,
                  reason,
                  p.sessions,
                  cost.add(p.entryCost),
                  pnl.setScale(2, RoundingMode.HALF_UP)));
          it.remove();
        }
      }

      // 2. Entries from the previous session's signals.
      if (d > 0 && (!s.regimeFilter() || benchmarkAboveAverage(benchmark, sessions.get(d - 1)))) {
        LocalDate signalDay = sessions.get(d - 1);
        for (Signal sig : signalsByDate.getOrDefault(signalDay, List.of())) {
          if (open.size() >= s.maxPositions()) {
            break;
          }
          if (open.containsKey(sig.instrumentId())) {
            continue;
          }
          PriceTable t = prices.get(sig.instrumentId());
          int i = t == null ? -1 : t.indexOf(today);
          int si = t == null ? -1 : t.indexOf(signalDay);
          if (i < 0 || si < s.atrPeriod()) {
            continue;
          }
          Optional<BigDecimal> atr = atrAt(t, si, s.atrPeriod());
          if (atr.isEmpty()) {
            continue;
          }
          BigDecimal entry =
              t.open(i).multiply(BigDecimal.ONE.add(slip)).setScale(4, RoundingMode.HALF_UP);
          BigDecimal stop = entry.subtract(atr.get().multiply(s.atrMultiple()));
          BigDecimal riskPerShare = entry.subtract(stop);
          if (stop.signum() <= 0 || riskPerShare.signum() <= 0) {
            continue;
          }
          long byRisk =
              prevEquity
                  .multiply(s.riskPerTrade())
                  .divide(riskPerShare, 0, RoundingMode.FLOOR)
                  .longValue();
          long byCap =
              prevEquity
                  .multiply(s.maxPositionFraction())
                  .divide(entry, 0, RoundingMode.FLOOR)
                  .longValue();
          long qty = Math.min(byRisk, byCap);
          // Keep enough cash for charges: reserve 1% on top of the value.
          long byCash =
              cash.divide(entry.multiply(new BigDecimal("1.01")), 0, RoundingMode.FLOOR)
                  .longValue();
          qty = Math.min(qty, byCash);
          if (qty < 1) {
            continue;
          }
          BigDecimal value = entry.multiply(BigDecimal.valueOf(qty));
          BigDecimal cost = s.costs().buyCost(value);
          cash = cash.subtract(value).subtract(cost);
          totalCosts = totalCosts.add(cost);
          open.put(
              sig.instrumentId(),
              new Position(sig.instrumentId(), sig.symbol(), qty, entry, stop, today, cost));
        }
      }

      // 3. Mark to market at the close.
      BigDecimal equity = cash;
      for (Position p : open.values()) {
        PriceTable t = prices.get(p.id);
        int i = t.indexAtOrBefore(today);
        BigDecimal mark = i >= 0 ? t.close(i) : p.entry;
        equity = equity.add(mark.multiply(BigDecimal.valueOf(p.qty)));
      }
      int bi = benchmark.indexAtOrBefore(today);
      BigDecimal bench = null;
      if (bi >= 0) {
        if (benchBase == null) {
          benchBase = benchmark.close(bi);
        }
        bench =
            s.initialCapital()
                .multiply(benchmark.close(bi))
                .divide(benchBase, 2, RoundingMode.HALF_UP);
      }
      days.add(
          new Day(
              today,
              equity.setScale(2, RoundingMode.HALF_UP),
              cash.setScale(2, RoundingMode.HALF_UP),
              open.size(),
              bench));
      prevEquity = equity;
    }

    // Close what is still open at the last close, so trade statistics are complete.
    if (!sessions.isEmpty()) {
      LocalDate last = sessions.get(sessions.size() - 1);
      for (Position p : open.values()) {
        PriceTable t = prices.get(p.id);
        int i = t.indexAtOrBefore(last);
        BigDecimal exitPrice = slipDown(i >= 0 ? t.close(i) : p.entry, slip);
        BigDecimal proceeds = exitPrice.multiply(BigDecimal.valueOf(p.qty));
        BigDecimal cost = s.costs().sellCost(proceeds);
        totalCosts = totalCosts.add(cost);
        BigDecimal pnl =
            proceeds
                .subtract(p.entry.multiply(BigDecimal.valueOf(p.qty)))
                .subtract(cost)
                .subtract(p.entryCost);
        trades.add(
            new Trade(
                p.symbol,
                p.entryDate,
                p.entry,
                p.qty,
                last,
                exitPrice,
                "END",
                p.sessions,
                cost.add(p.entryCost),
                pnl.setScale(2, RoundingMode.HALF_UP)));
      }
    }
    return new Outcome(trades, days, totalCosts.setScale(2, RoundingMode.HALF_UP));
  }

  private static BigDecimal slipDown(BigDecimal price, BigDecimal slip) {
    return price.multiply(BigDecimal.ONE.subtract(slip)).setScale(4, RoundingMode.HALF_UP);
  }

  /**
   * Regime filter: true when the benchmark's close on {@code day} is above its 200-session average.
   * False (no new entries) when there is not enough history, so the filter fails closed.
   */
  static boolean benchmarkAboveAverage(PriceTable benchmark, LocalDate day) {
    int i = benchmark.indexAtOrBefore(day);
    if (i + 1 < REGIME_AVERAGE) {
      return false;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (int k = i + 1 - REGIME_AVERAGE; k <= i; k++) {
      sum = sum.add(benchmark.close(k));
    }
    BigDecimal average = sum.divide(BigDecimal.valueOf(REGIME_AVERAGE), Indicators.MC);
    return benchmark.close(i).compareTo(average) > 0;
  }

  /** ATR over the {@code n} bars ending at index {@code i}. */
  static Optional<BigDecimal> atrAt(PriceTable t, int i, int n) {
    if (i < n) {
      return Optional.empty();
    }
    List<BigDecimal> h = new ArrayList<>();
    List<BigDecimal> l = new ArrayList<>();
    List<BigDecimal> c = new ArrayList<>();
    for (int k = i - n; k <= i; k++) {
      h.add(t.high(k));
      l.add(t.low(k));
      c.add(t.close(k));
    }
    return Indicators.atr(h, l, c, n);
  }
}
