package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.strategy.PlanExecutor.Bar;
import com.auratrade.strategy.TradePlan;
import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Sessions are Jan 1 to 5 2026. Plan: buy-stop trigger 100, no-chase 102, one session to fill, stop
 * 95 (R = 5), no targets. Costs are zero unless a test says otherwise.
 */
class PlanPortfolioSimulatorTest {

  private static final List<LocalDate> SESSIONS =
      List.of(0, 1, 2, 3, 4).stream().map(i -> LocalDate.of(2026, 1, 1 + i)).toList();

  private static Bar bar(int day, String o, String h, String l, String c) {
    return new Bar(
        SESSIONS.get(day),
        new BigDecimal(o),
        new BigDecimal(h),
        new BigDecimal(l),
        new BigDecimal(c));
  }

  private static TradePlan plan() {
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, new BigDecimal("100"), new BigDecimal("102"), 1),
        new BigDecimal("95"),
        "below the breakout",
        List.of(),
        new TrailingRule.None(),
        new Invalidation.None(),
        10);
  }

  private static PlanPortfolioSimulator.Signal signal(
      int day, long id, String symbol, String score) {
    return new PlanPortfolioSimulator.Signal(
        SESSIONS.get(day), id, symbol, plan(), new BigDecimal(score));
  }

  /** Signal on day 0, fills on day 1 at 100, stopped on day 2 at 95 (low 94). */
  private static List<Bar> stopOutSeries() {
    return List.of(
        bar(0, "100", "101", "99", "100"),
        bar(1, "99", "101", "98", "100"),
        bar(2, "101", "103", "94", "96"));
  }

  private static PlanPortfolioSimulator.Settings settings(
      String capital, int maxPositions, String risk, String maxPos, CostModel costs) {
    return new PlanPortfolioSimulator.Settings(
        new BigDecimal(capital),
        maxPositions,
        new BigDecimal(risk),
        new BigDecimal(maxPos),
        0,
        costs);
  }

  @Test
  void oneSlotTakesTheHigherScoreAndRejectsTheOther() {
    // Risk 1% of 100,000 = 1,000 over R = 5: 200 shares, cost 20,000. Equity stays 100,000 until
    // the stop: sell 200 at 95 = 19,000, a loss of 1,000 with zero costs.
    var outcome =
        PlanPortfolioSimulator.run(
            SESSIONS,
            Map.of(1L, stopOutSeries(), 2L, stopOutSeries()),
            List.of(signal(0, 1L, "AAA", "10"), signal(0, 2L, "BBB", "5")),
            settings("100000", 1, "0.01", "1", CostModel.free()));
    assertThat(outcome.trades()).hasSize(1);
    PlanPortfolioSimulator.Trade t = outcome.trades().get(0);
    assertThat(t.symbol()).isEqualTo("AAA");
    assertThat(t.quantity()).isEqualTo(200);
    assertThat(t.entryPrice()).isEqualByComparingTo("100");
    assertThat(t.exitDate()).isEqualTo(SESSIONS.get(2));
    assertThat(t.netPnl()).isEqualByComparingTo("-1000");
    assertThat(outcome.rejected()).isEqualTo(1);
    assertThat(outcome.finalEquity()).isEqualByComparingTo("99000");
    assertThat(outcome.openAtEnd()).isZero();
  }

  @Test
  void equityIsMarkedToTheCloseWhileHeld() {
    // After the fill (day 1) the position is 200 shares at a close of 100: equity stays 100,000
    // (cash 80,000 + 200 x 100). It is 99,000 at the stop.
    var outcome =
        PlanPortfolioSimulator.run(
            SESSIONS,
            Map.of(1L, stopOutSeries()),
            List.of(signal(0, 1L, "AAA", "10")),
            settings("100000", 1, "0.01", "1", CostModel.free()));
    assertThat(outcome.days().get(1).equity()).isEqualByComparingTo("100000");
    assertThat(outcome.days().get(1).positions()).isEqualTo(1);
    assertThat(outcome.days().get(2).equity()).isEqualByComparingTo("99000");
  }

  @Test
  void cashLimitsTheSizeWhenTheRiskBudgetWouldBuyMore() {
    // Risk budget 1,000 / R 5 = 200 shares; position cap 100% of 1,000 = 10 shares. The cap binds:
    // 10 shares. Cash 1,000 covers 10 shares x 100.
    long qty =
        PlanPortfolioSimulator.sizeFor(
            plan(),
            new BigDecimal("100"),
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            settings("1000", 1, "1", "1", CostModel.free()));
    assertThat(qty).isEqualTo(10);
  }

  @Test
  void sizeIsReducedUntilCashCoversTheBuyCosts() {
    // Equity 100,000, risk 1%: 200 shares, value 20,000, plus buy charges of about 24 (Zerodha
    // schedule). Cash 20,010 does not cover 200 shares, so the size falls until it fits.
    long qty =
        PlanPortfolioSimulator.sizeFor(
            plan(),
            new BigDecimal("100"),
            new BigDecimal("100000"),
            new BigDecimal("20010"),
            settings("100000", 1, "0.01", "1", CostModel.zerodhaDelivery()));
    assertThat(qty).isBetween(1L, 199L);
    BigDecimal value = new BigDecimal("100").multiply(BigDecimal.valueOf(qty));
    assertThat(value.add(CostModel.zerodhaDelivery().buyCost(value)))
        .isLessThanOrEqualTo(new BigDecimal("20010"));
  }

  @Test
  void aPositionStillOpenAtTheEndIsMarkedAndCounted() {
    // Fills on day 1 and never stops: the last bars stay above the stop.
    List<Bar> rising =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "101", "110", "101", "108"),
            bar(3, "108", "112", "107", "110"));
    var outcome =
        PlanPortfolioSimulator.run(
            SESSIONS.subList(0, 4),
            Map.of(1L, rising),
            List.of(signal(0, 1L, "AAA", "10")),
            settings("100000", 1, "0.01", "1", CostModel.free()));
    assertThat(outcome.trades()).isEmpty();
    assertThat(outcome.openAtEnd()).isEqualTo(1);
    assertThat(outcome.finalEquity()).isEqualByComparingTo("102000");
  }

  @Test
  void signalsWithoutABarOnTheirDayAreRejected() {
    var outcome =
        PlanPortfolioSimulator.run(
            SESSIONS,
            Map.of(1L, stopOutSeries()),
            List.of(signal(3, 1L, "AAA", "10")),
            settings("100000", 1, "0.01", "1", CostModel.free()));
    assertThat(outcome.trades()).isEmpty();
    assertThat(outcome.rejected()).isEqualTo(1);
  }
}
