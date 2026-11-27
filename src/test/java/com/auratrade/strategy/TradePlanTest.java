package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.Target;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradePlanTest {

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  private static TradePlan plan(String stop, List<Target> targets, Invalidation inv) {
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, d("1000"), d("1020"), 3),
        d(stop),
        "below the breakout level",
        targets,
        new TrailingRule.Ema(20, d("1")),
        inv,
        15);
  }

  @Test
  void specWorkedExample() {
    // Spec section 7: entry 1,000, stop 975, R = 25; 2R target = 1,050.
    TradePlan p =
        plan(
            "975",
            List.of(new Target(d("2"), d("0.5")), new Target(d("3"), d("0.5"))),
            new Invalidation.None());
    assertThat(p.riskPerShare()).isEqualByComparingTo("25");
    assertThat(p.targetPrice(p.targets().get(0))).isEqualByComparingTo("1050");
    assertThat(p.targetPrice(p.targets().get(1))).isEqualByComparingTo("1075");
    assertThat(p.plannedRewardRisk())
        .hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("2"));
    // Chasing to the no-chase limit of 1,020 makes the risk 45 per share.
    assertThat(p.maxRiskPerShare()).isEqualByComparingTo("45");
  }

  @Test
  void noTargetMeansNoPlannedRewardRisk() {
    assertThat(plan("975", List.of(), new Invalidation.None()).plannedRewardRisk()).isEmpty();
  }

  @Test
  void stopMustBeBelowTheTrigger() {
    assertThatThrownBy(() -> plan("1000", List.of(), new Invalidation.None()))
        .hasMessageContaining("stop must be below");
  }

  @Test
  void targetsMustAscendAndNotExceedTheWholePosition() {
    assertThatThrownBy(() ->
            plan(
                "975",
                List.of(new Target(d("3"), d("0.5")), new Target(d("2"), d("0.5"))),
                new Invalidation.None()))
        .hasMessageContaining("ascending");
    assertThatThrownBy(() ->
            plan(
                "975",
                List.of(new Target(d("2"), d("0.6")), new Target(d("3"), d("0.6"))),
                new Invalidation.None()))
        .hasMessageContaining("sum above 1");
  }

  @Test
  void invalidationLevelMustBeAboveTheStop() {
    assertThatThrownBy(
            () -> plan("975", List.of(), new Invalidation.CloseBelow(d("970"), "breakout failed")))
        .hasMessageContaining("above the stop");
    assertThat(plan("975", List.of(), new Invalidation.CloseBelow(d("990"), "breakout failed")))
        .isNotNull();
  }

  @Test
  void entryRejectsAMaxPriceBelowTheTrigger() {
    assertThatThrownBy(() -> new Entry(EntryType.BUY_STOP, d("100"), d("99"), 1))
        .hasMessageContaining("maxPrice");
    assertThatThrownBy(() -> new Entry(EntryType.NEXT_OPEN, d("100"), d("100"), 0))
        .hasMessageContaining("validSessions");
  }

  @Test
  void trailingRulesRejectNonsense() {
    assertThatThrownBy(() -> new TrailingRule.Atr(14, d("0"), d("0")))
        .hasMessageContaining("multiple");
    assertThatThrownBy(() -> new TrailingRule.Ema(1, d("0"))).hasMessageContaining("period");
    assertThatThrownBy(() -> new TrailingRule.Ema(20, d("-1"))).hasMessageContaining("negative");
  }
}
