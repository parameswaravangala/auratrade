package com.auratrade.backtest;

import com.auratrade.strategy.PlanExecutor;
import com.auratrade.strategy.PlanExecutor.Bar;
import com.auratrade.strategy.PlanExecutor.Exit;
import com.auratrade.strategy.PlanExecutor.Result;
import com.auratrade.strategy.PlanExecutor.Status;
import com.auratrade.strategy.TradePlan;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs {@link TradePlan}s on a portfolio (ADR 0041). Two passes, as in {@link
 * PortfolioSimulationService}:
 *
 * <ol>
 *   <li>Each signal is executed on its own with {@link PlanExecutor}. Signals that never fill are
 *       dropped here.
 *   <li>Day by day, the fills are admitted under the portfolio limits: at most {@code maxPositions}
 *       open, cash for the trade and its buy costs, and a size from the risk budget. On each
 *       session, entries are made first (best score first) and then the exits of the session are
 *       applied, so a stop on the entry day is booked. Exits and buys both fill at the executor's
 *       prices, with slippage against the account.
 * </ol>
 *
 * <p>Limits: a signal rejected for slots or cash is not re-tried later, and no liquidity limit or
 * partial fill is modelled. Money is BigDecimal throughout; quantities are whole shares.
 */
final class PlanPortfolioSimulator {

  /** A signal decided at the close of {@code date}. */
  record Signal(
      LocalDate date, long instrumentId, String symbol, TradePlan plan, BigDecimal score) {}

  record Settings(
      BigDecimal initialCapital,
      int maxPositions,
      BigDecimal riskPerTrade,
      BigDecimal maxPositionFraction,
      int slippageBps,
      CostModel costs) {}

  /** One closed position, net of costs. */
  record Trade(
      String symbol,
      LocalDate entryDate,
      BigDecimal entryPrice,
      long quantity,
      LocalDate exitDate,
      BigDecimal netPnl) {}

  record Day(LocalDate date, BigDecimal equity, int positions) {}

  record Outcome(
      List<Trade> trades,
      List<Day> days,
      int rejected,
      int openAtEnd,
      BigDecimal totalCosts,
      BigDecimal finalEquity) {}

  private static final MathContext MC = MathContext.DECIMAL64;
  private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

  private PlanPortfolioSimulator() {}

  static Outcome run(
      List<LocalDate> sessions, Map<Long, List<Bar>> bars, List<Signal> signals, Settings s) {
    // Pass 1: each signal on its own.
    Map<Long, Map<LocalDate, Integer>> indexByDate = new HashMap<>();
    Map<Long, Map<LocalDate, Bar>> barByDate = new HashMap<>();
    for (var e : bars.entrySet()) {
      Map<LocalDate, Integer> idx = new HashMap<>();
      Map<LocalDate, Bar> byDate = new HashMap<>();
      List<Bar> list = e.getValue();
      for (int i = 0; i < list.size(); i++) {
        idx.put(list.get(i).date(), i);
        byDate.put(list.get(i).date(), list.get(i));
      }
      indexByDate.put(e.getKey(), idx);
      barByDate.put(e.getKey(), byDate);
    }
    List<Candidate> candidates = new ArrayList<>();
    int rejected = 0;
    for (Signal sig : signals) {
      Integer si = indexByDate.getOrDefault(sig.instrumentId(), Map.of()).get(sig.date());
      if (si == null) {
        rejected++;
        continue;
      }
      Result r = PlanExecutor.execute(sig.plan(), bars.get(sig.instrumentId()), si);
      if (r.status() == Status.NOT_FILLED) {
        rejected++;
        continue;
      }
      candidates.add(new Candidate(sig, r));
    }
    candidates.sort(
        Comparator.comparing((Candidate c) -> c.result().entryDate().orElseThrow())
            .thenComparing(c -> c.signal().score(), Comparator.reverseOrder())
            .thenComparing(c -> c.signal().instrumentId()));

    // Pass 2: the portfolio, day by day.
    BigDecimal slip = BigDecimal.valueOf(s.slippageBps()).divide(TEN_THOUSAND, MC);
    BigDecimal cash = s.initialCapital();
    BigDecimal equityPrev = s.initialCapital();
    BigDecimal totalCosts = BigDecimal.ZERO;
    List<Position> open = new ArrayList<>();
    List<Trade> trades = new ArrayList<>();
    List<Day> days = new ArrayList<>();
    int next = 0;

    for (LocalDate d : sessions) {
      // Entries first, best score first.
      while (next < candidates.size()
          && !candidates.get(next).result().entryDate().orElseThrow().isAfter(d)) {
        Candidate c = candidates.get(next++);
        if (open.size() >= s.maxPositions()) {
          rejected++;
          continue;
        }
        BigDecimal price = c.result().entryPrice().multiply(BigDecimal.ONE.add(slip), MC);
        long qty = sizeFor(c.signal().plan(), price, equityPrev, cash, s);
        if (qty <= 0) {
          rejected++;
          continue;
        }
        BigDecimal value = price.multiply(BigDecimal.valueOf(qty), MC);
        BigDecimal buyCost = s.costs().buyCost(value);
        cash = cash.subtract(value).subtract(buyCost);
        totalCosts = totalCosts.add(buyCost);
        open.add(new Position(c, qty, price, value, buyCost));
      }
      // Exits for this session, then the mark to market at the close.
      for (var it = open.iterator(); it.hasNext(); ) {
        Position p = it.next();
        while (p.nextExit < p.result().exits().size()
            && p.result().exits().get(p.nextExit).date().equals(d)) {
          Exit e = p.result().exits().get(p.nextExit);
          boolean last = p.nextExit == p.result().exits().size() - 1;
          long sell =
              last && p.result().status() == Status.CLOSED
                  ? p.remaining
                  : Math.min(
                      p.remaining,
                      BigDecimal.valueOf(p.qty)
                          .multiply(e.fraction(), MC)
                          .setScale(0, RoundingMode.DOWN)
                          .longValueExact());
          p.nextExit++;
          if (sell <= 0) {
            continue;
          }
          BigDecimal px = e.price().multiply(BigDecimal.ONE.subtract(slip), MC);
          BigDecimal value = px.multiply(BigDecimal.valueOf(sell), MC);
          BigDecimal cost = s.costs().sellCost(value);
          cash = cash.add(value).subtract(cost);
          totalCosts = totalCosts.add(cost);
          p.proceeds = p.proceeds.add(value);
          p.sellCosts = p.sellCosts.add(cost);
          p.remaining -= sell;
        }
        Bar today = barByDate.get(p.c.signal().instrumentId()).get(d);
        if (today != null) {
          p.lastClose = today.close();
        }
        if (p.remaining == 0) {
          BigDecimal net =
              p.proceeds.subtract(p.sellCosts).subtract(p.buyValue).subtract(p.buyCost);
          trades.add(
              new Trade(
                  p.c.signal().symbol(),
                  p.c.result().entryDate().orElseThrow(),
                  p.price,
                  p.qty,
                  d,
                  net));
          it.remove();
        }
      }
      BigDecimal equity = cash;
      for (Position p : open) {
        equity = equity.add(p.lastClose.multiply(BigDecimal.valueOf(p.remaining), MC));
      }
      equityPrev = equity;
      days.add(new Day(d, equity, open.size()));
    }
    return new Outcome(trades, days, rejected, open.size(), totalCosts, equityPrev);
  }

  /** Whole shares from the risk budget and the position cap, reduced until cash covers costs. */
  static long sizeFor(
      TradePlan plan, BigDecimal price, BigDecimal equity, BigDecimal cash, Settings s) {
    BigDecimal riskPerShare = plan.riskPerShare();
    long byRisk =
        equity
            .multiply(s.riskPerTrade(), MC)
            .divide(riskPerShare, MC)
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
    long byCap =
        equity
            .multiply(s.maxPositionFraction(), MC)
            .divide(price, MC)
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
    long qty = Math.min(byRisk, byCap);
    qty = Math.min(qty, cash.divide(price, MC).setScale(0, RoundingMode.DOWN).longValueExact());
    while (qty > 0) {
      BigDecimal value = price.multiply(BigDecimal.valueOf(qty), MC);
      if (value.add(s.costs().buyCost(value)).compareTo(cash) <= 0) {
        break;
      }
      qty--;
    }
    return Math.max(qty, 0);
  }

  private record Candidate(Signal signal, Result result) {}

  private static final class Position {
    final Candidate c;
    final long qty;
    final BigDecimal price;
    final BigDecimal buyValue;
    final BigDecimal buyCost;
    long remaining;
    int nextExit;
    BigDecimal proceeds = BigDecimal.ZERO;
    BigDecimal sellCosts = BigDecimal.ZERO;
    BigDecimal lastClose;

    Position(Candidate c, long qty, BigDecimal price, BigDecimal buyValue, BigDecimal buyCost) {
      this.c = c;
      this.qty = qty;
      this.remaining = qty;
      this.price = price;
      this.buyValue = buyValue;
      this.buyCost = buyCost;
      this.lastClose = price;
    }

    Result result() {
      return c.result();
    }
  }
}
