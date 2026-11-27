package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.strategy.PlanExecutor.Bar;
import com.auratrade.strategy.PlanExecutor.ExitReason;
import com.auratrade.strategy.PlanExecutor.Status;
import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.Target;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every expected number is worked by hand in the comment. Plan: buy-stop trigger 100, no-chase
 * limit 102, valid 2 sessions, stop 95, so R = 5; targets at 110 (2R, half) and 115 (3R, half).
 */
class PlanExecutorTest {

  private static Bar bar(int day, String o, String h, String l, String c) {
    return new Bar(
        LocalDate.of(2026, 1, 1).plusDays(day),
        new BigDecimal(o),
        new BigDecimal(h),
        new BigDecimal(l),
        new BigDecimal(c));
  }

  private static TradePlan plan(
      List<Target> targets, TrailingRule trailing, Invalidation inv, int maxHold) {
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, new BigDecimal("100"), new BigDecimal("102"), 2),
        new BigDecimal("95"),
        "below the breakout",
        targets,
        trailing,
        inv,
        maxHold);
  }

  private static TradePlan ladder() {
    return plan(
        List.of(
            new Target(new BigDecimal("2"), new BigDecimal("0.5")),
            new Target(new BigDecimal("3"), new BigDecimal("0.5"))),
        new TrailingRule.None(),
        new Invalidation.None(),
        10);
  }

  private static void assertR(PlanExecutor.Result r, String expected) {
    assertThat(r.realizedR()).isEqualByComparingTo(expected);
  }

  @Test
  void bothTargetsHitAfterAPartialFillAndTheTradeCloses() {
    // Signal bar 0. Day 1 opens 99 and trades up to 101, so the buy-stop fills at the trigger 100.
    // Day 2 opens 101, reaches 111: the 110 target sells half. Day 3 reaches 116: the 115 target
    // sells the other half. Realised R = (0.5 x 10 + 0.5 x 15) / 5 = 2.5.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "101", "111", "100", "110"),
            bar(3, "112", "116", "111", "115"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.status()).isEqualTo(Status.CLOSED);
    assertThat(r.entryPrice()).isEqualByComparingTo("100");
    assertThat(r.exits())
        .extracting(PlanExecutor.Exit::reason)
        .containsExactly(ExitReason.TARGET, ExitReason.TARGET);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("110");
    assertThat(r.exits().get(1).price()).isEqualByComparingTo("115");
    assertR(r, "2.5");
    assertThat(r.sessionsHeld()).isEqualTo(2);
  }

  @Test
  void intradayStopExitsTheWholePosition() {
    // Fill at 100 on day 1. Day 2 trades down to 94: exit at the stop price 95.
    // R = (95 - 100) / 5 = -1.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "97", "98", "94", "96"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.status()).isEqualTo(Status.CLOSED);
    assertThat(r.exits()).hasSize(1);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.STOP);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("95");
    assertR(r, "-1");
  }

  @Test
  void gapThroughTheStopExitsAtTheOpenNotTheStop() {
    // Day 2 opens at 90, below the stop: exit at 90. R = (90 - 100) / 5 = -2.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "90", "92", "89", "91"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.STOP_GAP);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("90");
    assertR(r, "-2");
  }

  @Test
  void whenABarTouchesBothStopAndTargetTheStopWins() {
    // Day 2 reaches 112 and falls to 94: order inside the bar is unknown, so the stop is taken
    // first and no target is sold.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "101", "112", "94", "110"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.exits()).hasSize(1);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.STOP);
    assertR(r, "-1");
  }

  @Test
  void noEntryInsideTheWindowIsNotFilled() {
    // Window is 2 sessions after the signal (days 1 and 2). Day 3 would trigger but is too late.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "96", "99", "95", "98"),
            bar(2, "97", "99", "96", "98"),
            bar(3, "99", "101", "98", "100"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.status()).isEqualTo(Status.NOT_FILLED);
    assertThat(r.entryDate()).isEmpty();
    assertThat(r.exits()).isEmpty();
  }

  @Test
  void aGapAboveTheNoChaseLimitIsNotTaken() {
    // Day 1 opens at 103, above the 102 limit, so the buy is skipped. Day 2 also opens at 103.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "103", "104", "102", "103"),
            bar(2, "103", "104", "102", "103"));
    assertThat(PlanExecutor.execute(ladder(), bars, 0).status()).isEqualTo(Status.NOT_FILLED);
  }

  @Test
  void holdingLimitExitsAtTheClose() {
    // Max hold 2 sessions. Fill on day 1 at 100. On day 3 the hold reaches 2: exit at the close
    // 102. R = (102 - 100) / 5 = 0.4.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "100", "101", "99", "100"),
            bar(2, "100", "101", "99", "101"),
            bar(3, "101", "103", "100.5", "102"));
    PlanExecutor.Result r =
        PlanExecutor.execute(
            plan(List.of(), new TrailingRule.None(), new Invalidation.None(), 2), bars, 0);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.HOLDING_LIMIT);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("102");
    assertR(r, "0.4");
  }

  @Test
  void invalidationOnACloseExitsAtTheNextOpen() {
    // Level 97. Day 2 closes at 96, below it. Day 3 opens at 98 and the position exits there.
    // R = (98 - 100) / 5 = -0.4.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "99", "101", "98", "100"),
            bar(2, "100", "101", "96", "96"),
            bar(3, "98", "99", "97.5", "98"));
    PlanExecutor.Result r =
        PlanExecutor.execute(
            plan(
                List.of(),
                new TrailingRule.None(),
                new Invalidation.CloseBelow(new BigDecimal("97"), "breakout failed"),
                10),
            bars,
            0);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.INVALIDATED);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("98");
    assertR(r, "-0.4");
  }

  @Test
  void emaTrailingRaisesTheStopAndAGapThroughItExitsAtTheOpen() {
    // Trail: EMA(2) of closes, active from profit 0. Fill day 1 closes 100: EMA 100, so the stop
    // rises from 95 to 100. Day 2 closes 110: EMA 100 + (2/3)(110 - 100) = 106.67, stop 106.67
    // from day 3. Day 3 closes 108: EMA 107.56, stop 107.56. Day 4 opens at 107, below the stop:
    // exit at 107. R = (107 - 100) / 5 = 1.4.
    List<Bar> bars =
        List.of(
            bar(0, "100", "101", "99", "100"),
            bar(1, "100", "101", "99", "100"),
            bar(2, "101", "111", "101", "110"),
            bar(3, "109", "109", "108", "108"),
            bar(4, "107", "108", "106.9", "107.5"));
    PlanExecutor.Result r =
        PlanExecutor.execute(
            plan(List.of(), new TrailingRule.Ema(2, BigDecimal.ZERO), new Invalidation.None(), 10),
            bars,
            0);
    assertThat(r.exits().get(0).reason()).isEqualTo(ExitReason.STOP_GAP);
    assertThat(r.exits().get(0).price()).isEqualByComparingTo("107");
    assertR(r, "1.4");
  }

  @Test
  void stillHeldAtTheLastBarIsOpen() {
    List<Bar> bars = List.of(bar(0, "100", "101", "99", "100"), bar(1, "100", "101", "99", "100"));
    PlanExecutor.Result r = PlanExecutor.execute(ladder(), bars, 0);
    assertThat(r.status()).isEqualTo(Status.OPEN);
    assertThat(r.openFraction()).isEqualByComparingTo("1");
    assertThat(r.exits()).isEmpty();
  }

  @Test
  void barsMustBeInternallyConsistent() {
    assertThatThrownBy(() -> bar(0, "100", "99", "98", "98.5"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> bar(0, "105", "101", "99", "100"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
