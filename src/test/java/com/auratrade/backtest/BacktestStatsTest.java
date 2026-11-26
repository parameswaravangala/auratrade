package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.backtest.ScreenReplay.Sample;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestStatsTest {

  static Sample sample(String symbol, String date, String score, String ret, String excess) {
    return new Sample(
        "s",
        LocalDate.parse(date),
        symbol,
        new BigDecimal(score),
        20,
        new BigDecimal(ret),
        new BigDecimal(excess));
  }

  @Test
  void summarisesHitRateMeanMedianAndTails() {
    // Excess: -0.02, 0.01, 0.03, 0.06 -> hit rate 75%, mean 2%, median (0.01+0.03)/2 = 2%.
    // Returns sorted: -0.05, 0.00, 0.04, 0.08 -> mean 1.75%, p10 index floor(0.1*3)=0 -> -5%.
    var stats =
        BacktestStats.of(
            20,
            List.of(
                sample("A", "2025-01-01", "1", "0.08", "0.06"),
                sample("B", "2025-01-01", "1", "-0.05", "-0.02"),
                sample("C", "2025-01-01", "1", "0.04", "0.03"),
                sample("D", "2025-01-01", "1", "0.00", "0.01")),
            BigDecimal.ZERO);

    assertThat(stats.samples()).isEqualTo(4);
    assertThat(stats.hitRatePct()).isEqualByComparingTo("75.00");
    assertThat(stats.meanExcessPct()).isEqualByComparingTo("2.00");
    assertThat(stats.medianExcessPct()).isEqualByComparingTo("2.00");
    assertThat(stats.meanReturnPct()).isEqualByComparingTo("1.75");
    assertThat(stats.p10ReturnPct()).isEqualByComparingTo("-5.00");
    assertThat(stats.worstReturnPct()).isEqualByComparingTo("-5.00");
  }

  @Test
  void costsAreSubtractedFromEveryReturn() {
    // 50 bps = 0.005 off: excess 0.004 becomes -0.001, so it no longer counts as a win.
    var stats =
        BacktestStats.of(
            20, List.of(sample("A", "2025-01-01", "1", "0.01", "0.004")), new BigDecimal("0.005"));

    assertThat(stats.hitRatePct()).isEqualByComparingTo("0.00");
    assertThat(stats.meanReturnPct()).isEqualByComparingTo("0.50");
  }

  @Test
  void topPerDateKeepsTheHighestScoresOfEachDate() {
    var top =
        BacktestStats.topPerDate(
            List.of(
                sample("A", "2025-01-01", "3", "0", "0"),
                sample("B", "2025-01-01", "1", "0", "0"),
                sample("C", "2025-01-01", "2", "0", "0"),
                sample("D", "2025-01-02", "1", "0", "0")),
            2);

    assertThat(top).extracting(Sample::symbol).containsExactlyInAnyOrder("A", "C", "D");
  }

  @Test
  void emptyInputGivesEmptyStatsNotAnError() {
    assertThat(BacktestStats.of(5, List.of(), BigDecimal.ZERO).samples()).isZero();
  }
}
