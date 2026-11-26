package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.backtest.PortfolioSimulator.Signal;
import com.auratrade.marketdata.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Scripted bars; every fill and P&L below is worked by hand. */
class PortfolioSimulatorTest {

  static final LocalDate D0 = LocalDate.parse("2025-01-06");

  static List<LocalDate> sessions(int n) {
    List<LocalDate> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(D0.plusDays(i));
    }
    return out;
  }

  /** Bars as {open, high, low, close}. */
  static PriceTable table(String symbol, double[][] bars) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < bars.length; i++) {
      double[] b = bars[i];
      candles.add(
          Candle.daily(
              D0.plusDays(i),
              BigDecimal.valueOf(b[0]),
              BigDecimal.valueOf(b[1]),
              BigDecimal.valueOf(b[2]),
              BigDecimal.valueOf(b[3]),
              1000));
    }
    return new PriceTable(symbol, candles);
  }

  static PortfolioSimulator.Settings settings(int maxPositions, int maxHold, CostModel costs) {
    // 1 lakh, 1% risk, 20% max position, ATR(2), stop 2 x ATR, no slippage.
    return new PortfolioSimulator.Settings(
        new BigDecimal("100000"),
        maxPositions,
        new BigDecimal("0.01"),
        new BigDecimal("0.20"),
        2,
        BigDecimal.valueOf(2),
        maxHold,
        0,
        costs);
  }

  static final double[] FLAT = {100, 101, 99, 100}; // true range 2, so ATR(2) = 2

  static PriceTable bench(int n) {
    double[][] bars = new double[n][];
    for (int i = 0; i < n; i++) {
      bars[i] = new double[] {1000, 1000, 1000, 1000};
    }
    return table("NIFTY", bars);
  }

  static Map<LocalDate, List<Signal>> signalOn(int day, long id, String symbol) {
    return Map.of(
        D0.plusDays(day), List.of(new Signal(D0.plusDays(day), id, symbol, BigDecimal.ONE)));
  }

  @Test
  void entersNextOpenSizedByRiskAndCapThenStopsOut() {
    // Signal day 2. Entry day 3 at open 100; stop = 100 - 2 x 2 = 96. Risk 1,000 / 4 = 250 shares,
    // cap 20,000 / 100 = 200 -> 200 shares. Day 4 low 95 <= 96: exit at 96. P&L = -4 x 200 = -800.
    var t =
        table(
            "ACME", new double[][] {FLAT, FLAT, FLAT, {100, 102, 99, 101}, {98, 99, 95, 97}, FLAT});

    var out =
        PortfolioSimulator.run(
            sessions(6),
            Map.of(1L, t),
            signalOn(2, 1, "ACME"),
            bench(6),
            settings(10, 60, CostModel.free()));

    assertThat(out.trades())
        .singleElement()
        .satisfies(tr -> {
          assertThat(tr.entryPrice()).isEqualByComparingTo("100");
          assertThat(tr.quantity()).isEqualTo(200);
          assertThat(tr.exitReason()).isEqualTo("STOP");
          assertThat(tr.exitPrice()).isEqualByComparingTo("96");
          assertThat(tr.netPnl()).isEqualByComparingTo("-800");
        });
    assertThat(out.days().get(5).equity()).isEqualByComparingTo("99200");
  }

  @Test
  void aGapBelowTheStopFillsAtTheOpen() {
    var t =
        table(
            "ACME", new double[][] {FLAT, FLAT, FLAT, {100, 102, 99, 101}, {90, 92, 88, 91}, FLAT});

    var out =
        PortfolioSimulator.run(
            sessions(6),
            Map.of(1L, t),
            signalOn(2, 1, "ACME"),
            bench(6),
            settings(10, 60, CostModel.free()));

    // Open 90 is below the stop 96 (and the gap is only 11%): exit at 90, P&L = -10 x 200.
    assertThat(out.trades().get(0).exitReason()).isEqualTo("STOP_GAP");
    assertThat(out.trades().get(0).netPnl()).isEqualByComparingTo("-2000");
  }

  @Test
  void exitsAtTheCloseWhenTheHoldingLimitIsReached() {
    var t =
        table(
            "ACME",
            new double[][] {
              FLAT,
              FLAT,
              FLAT,
              {100, 102, 99, 101},
              {101, 104, 100, 103},
              {103, 106, 102, 105},
              FLAT
            });

    var out =
        PortfolioSimulator.run(
            sessions(7),
            Map.of(1L, t),
            signalOn(2, 1, "ACME"),
            bench(7),
            settings(10, 2, CostModel.free()));

    // Held day 4 (1 session) and day 5 (2 sessions): exit at day 5 close 105. P&L = 5 x 200.
    assertThat(out.trades().get(0).exitReason()).isEqualTo("TIME");
    assertThat(out.trades().get(0).exitDate()).isEqualTo(D0.plusDays(5));
    assertThat(out.trades().get(0).netPnl()).isEqualByComparingTo("1000");
  }

  @Test
  void aSplitSizedGapClosesAtThePreviousClose() {
    var t =
        table(
            "ACME", new double[][] {FLAT, FLAT, FLAT, {100, 102, 99, 101}, {50, 51, 49, 50}, FLAT});

    var out =
        PortfolioSimulator.run(
            sessions(6),
            Map.of(1L, t),
            signalOn(2, 1, "ACME"),
            bench(6),
            settings(10, 60, CostModel.free()));

    // Gap 50 / 101 - 1 = -50%: exit at the previous close 101, P&L = 1 x 200.
    assertThat(out.trades().get(0).exitReason()).isEqualTo("CORPORATE_ACTION");
    assertThat(out.trades().get(0).netPnl()).isEqualByComparingTo("200");
  }

  @Test
  void respectsTheMaximumNumberOfPositions() {
    var a = table("A", new double[][] {FLAT, FLAT, FLAT, FLAT, FLAT});
    var b = table("B", new double[][] {FLAT, FLAT, FLAT, FLAT, FLAT});
    var signals =
        Map.of(
            D0.plusDays(2),
            List.of(
                new Signal(D0.plusDays(2), 1, "A", BigDecimal.TEN),
                new Signal(D0.plusDays(2), 2, "B", BigDecimal.ONE)));

    var out =
        PortfolioSimulator.run(
            sessions(5),
            Map.of(1L, a, 2L, b),
            signals,
            bench(5),
            settings(1, 60, CostModel.free()));

    assertThat(out.trades()).extracting(PortfolioSimulator.Trade::symbol).containsExactly("A");
  }

  @Test
  void costsReduceTheResultExactly() {
    var t =
        table(
            "ACME", new double[][] {FLAT, FLAT, FLAT, {100, 102, 99, 101}, {98, 99, 95, 97}, FLAT});
    var costs = CostModel.zerodhaDelivery();

    var out =
        PortfolioSimulator.run(
            sessions(6), Map.of(1L, t), signalOn(2, 1, "ACME"), bench(6), settings(10, 60, costs));

    // Buy 200 x 100 = 20,000; sell 200 x 96 = 19,200.
    BigDecimal expected =
        new BigDecimal("-800")
            .subtract(costs.buyCost(new BigDecimal("20000")))
            .subtract(costs.sellCost(new BigDecimal("19200")));
    assertThat(out.trades().get(0).netPnl()).isEqualByComparingTo(expected);
    assertThat(out.days().get(5).equity())
        .isEqualByComparingTo(new BigDecimal("100000").add(expected));
  }
}
