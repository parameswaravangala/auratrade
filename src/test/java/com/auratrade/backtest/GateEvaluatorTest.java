package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.backtest.GateEvaluator.Input;
import com.auratrade.backtest.GateEvaluator.SettingResult;
import com.auratrade.backtest.PlanPortfolioSimulator.Day;
import com.auratrade.backtest.PlanPortfolioSimulator.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateEvaluatorTest {

  private static Trade trade(LocalDate entry, int holdDays, long pnl) {
    return new Trade(
        "X", entry, BigDecimal.ONE, 1, entry.plusDays(holdDays), BigDecimal.valueOf(pnl));
  }

  private static List<Day> curve(long... equities) {
    List<Day> out = new ArrayList<>();
    LocalDate d = LocalDate.of(2023, 1, 31);
    for (long e : equities) {
      out.add(new Day(d, BigDecimal.valueOf(e), 0));
      d = d.plusMonths(1);
    }
    return out;
  }

  /**
   * Two settings, identical except for their name. In sample (2019 to 2022): 40 trades of +50, all
   * closed before 2023. Out of sample: 120 trades, 2023 and 2024, alternating +200 and -100 (so
   * expectancy 50, profit factor 2). Equity rises from 100,000 to 124,000 over 24 months.
   */
  private static Input passingInput(long... equities) {
    List<Trade> trades = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      trades.add(trade(LocalDate.of(2021, 1, 1).plusDays(i), 3, 50));
    }
    for (int i = 0; i < 120; i++) {
      int year = 2023 + i / 60;
      LocalDate entry = LocalDate.of(year, 1, 1).plusDays((i % 60) * 5L);
      trades.add(trade(entry, 3, i % 2 == 0 ? 200 : -100));
    }
    List<Day> days = curve(equities);
    return new Input(
        "relative-strength@1.0.0",
        LocalDate.of(2023, 1, 1),
        LocalDate.of(2024, 12, 31),
        BigDecimal.valueOf(100_000),
        List.of(new SettingResult("a", trades, days), new SettingResult("b", trades, days)),
        "a",
        0.05);
  }

  private static final long[] RISING = {
    100_000, 101_000, 102_000, 103_000, 104_000, 105_000, 106_000, 107_000, 108_000, 109_000,
    110_000, 111_000, 112_000, 113_000, 114_000, 115_000, 116_000, 117_000, 118_000, 119_000,
    120_000, 121_000, 122_000, 123_000
  };

  @Test
  void metricsForAKnownSetOfTrades() {
    // Net +100, -50, +200, -25: total 225, expectancy 56.25, win rate 1/2, profit factor 300/75 =
    // 4.
    List<Trade> trades =
        List.of(
            trade(LocalDate.of(2023, 1, 2), 1, 100),
            trade(LocalDate.of(2023, 2, 2), 1, -50),
            trade(LocalDate.of(2023, 3, 2), 1, 200),
            trade(LocalDate.of(2023, 4, 2), 1, -25));
    GateEvaluator.Metrics m = GateEvaluator.metrics(trades);
    assertThat(m.trades()).isEqualTo(4);
    assertThat(m.expectancy()).isEqualByComparingTo("56.25");
    assertThat(m.winRate()).isEqualByComparingTo("0.5");
    assertThat(m.profitFactor()).hasValueSatisfying(pf -> assertThat(pf).isEqualByComparingTo("4"));
    assertThat(m.totalNet()).isEqualByComparingTo("225");
  }

  @Test
  void selectionOnlyUsesTradesThatClosedBeforeTheCutoff() {
    // Setting A is poor before 2023 (-10 per trade) but a huge winner for trades closing in 2024.
    // Setting B is +20 per trade before 2023. At the 2023 cutoff only the past counts, so B wins.
    List<Trade> aTrades = new ArrayList<>();
    List<Trade> bTrades = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      LocalDate d = LocalDate.of(2021, 1, 1).plusDays(i);
      aTrades.add(trade(d, 3, -10));
      bTrades.add(trade(d, 3, 20));
    }
    for (int i = 0; i < 40; i++) {
      LocalDate d = LocalDate.of(2024, 1, 1).plusDays(i);
      aTrades.add(trade(d, 3, 10_000));
      bTrades.add(trade(d, 3, 1));
    }
    List<SettingResult> settings =
        List.of(
            new SettingResult("a", aTrades, List.of()), new SettingResult("b", bTrades, List.of()));
    assertThat(GateEvaluator.select(settings, LocalDate.of(2023, 1, 1)))
        .hasValueSatisfying(s -> assertThat(s.id()).isEqualTo("b"));
    // Whatever A earns after the cutoff, the 2023 choice does not change.
    assertThat(GateEvaluator.select(settings, LocalDate.of(2023, 1, 1)).orElseThrow().id())
        .isEqualTo("b");
  }

  @Test
  void aSettingWithTooFewTradesIsNotSelected() {
    List<Trade> few = List.of(trade(LocalDate.of(2021, 1, 1), 1, 999));
    assertThat(
            GateEvaluator.select(
                List.of(new SettingResult("a", few, List.of())), LocalDate.of(2023, 1, 1)))
        .isEmpty();
  }

  @Test
  void aSoundRunPassesEveryCheck() {
    GateEvaluator.Report r = GateEvaluator.evaluate(passingInput(RISING));
    assertThat(r.checks()).extracting(GateEvaluator.Check::passed).containsOnly(true);
    assertThat(r.passed()).isTrue();
    assertThat(r.chosenSettingId()).isEqualTo("a");
    assertThat(r.outOfSample().trades()).isEqualTo(120);
    assertThat(r.settingsBeatingNifty()).isEqualTo(2);
  }

  @Test
  void aDeepDrawdownFailsOnlyTheDrawdownCheck() {
    // Equity falls from 130,000 to 60,000 (-54%) before recovering to 124,000.
    GateEvaluator.Report r =
        GateEvaluator.evaluate(passingInput(100_000, 130_000, 60_000, 124_000));
    assertThat(r.passed()).isFalse();
    assertThat(r.checks().stream().filter(c -> !c.passed()))
        .extracting(GateEvaluator.Check::name)
        .containsExactly("Max drawdown at spec defaults, out of sample");
    assertThat(r.defaultMaxDrawdown()).isNegative();
  }

  @Test
  void tooFewOutOfSampleTradesFailsTheTradeCountCheck() {
    Input base = passingInput(RISING);
    List<SettingResult> trimmed = new ArrayList<>();
    for (SettingResult s : base.settings()) {
      // Keep the in-sample trades and only 60 out-of-sample ones.
      List<Trade> keep = new ArrayList<>(s.trades().subList(0, 40));
      keep.addAll(s.trades().subList(40, 100));
      trimmed.add(new SettingResult(s.id(), keep, s.days()));
    }
    GateEvaluator.Report r =
        GateEvaluator.evaluate(
            new Input(
                base.strategyRef(),
                base.oosStart(),
                base.end(),
                base.initialCapital(),
                trimmed,
                "a",
                base.niftyOosCagr()));
    assertThat(r.passed()).isFalse();
    assertThat(r.checks().get(0).passed()).isFalse();
    assertThat(r.checks().get(0).value()).isEqualTo("60");
  }
}
