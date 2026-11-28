package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.backtest.GateGrids.Setting;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
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

class GateRunServiceTest {

  @Test
  void momentumGridIsWithinTheEighteenSettingLimitAndContainsTheSpecDefault() {
    List<Setting> grid = GateGrids.forStrategy("momentum-breakout");
    assertThat(grid).hasSize(8).hasSizeLessThanOrEqualTo(18);
    assertThat(grid)
        .extracting(Setting::id)
        .contains(GateGrids.defaultSettingId("momentum-breakout"));
    assertThat(grid.stream().map(GateGrids::signalKey).distinct()).hasSize(4);
  }

  @Test
  void anUndeclaredStrategyHasNoGrid() {
    assertThatThrownBy(() -> GateGrids.forStrategy("relative-strength"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no gate grid");
  }

  @Test
  void keepTopKeepsTheBestSignalsOfEachSessionOnly() {
    LocalDate day = LocalDate.of(2026, 1, 5);
    List<PlanPortfolioSimulator.Signal> all = new java.util.ArrayList<>();
    for (int i = 0; i < GateRunService.KEEP_PER_DAY + 3; i++) {
      all.add(signal(day, i, "S" + i, i));
    }
    all.add(signal(day.plusDays(1), 99, "Z", 1));
    Map<LocalDate, List<PlanPortfolioSimulator.Signal>> kept = GateRunService.keepTop(all);
    assertThat(kept.get(day)).hasSize(GateRunService.KEEP_PER_DAY);
    assertThat(kept.get(day).get(0).score())
        .isEqualByComparingTo(BigDecimal.valueOf(GateRunService.KEEP_PER_DAY + 2));
    assertThat(kept.get(day.plusDays(1))).hasSize(1);
  }

  @Test
  void niftyCagrIsAnnualisedFromTheFirstBarOnOrAfterOosStart() {
    LocalDate start = LocalDate.of(2023, 1, 2);
    List<Candle> candles =
        List.of(
            Candle.daily(LocalDate.of(2022, 12, 30), bd(90), bd(90), bd(90), bd(90), 1),
            Candle.daily(start, bd(100), bd(100), bd(100), bd(100), 1),
            Candle.daily(start.plusDays(365), bd(110), bd(110), bd(110), bd(110), 1));
    double cagr = GateRunService.niftyCagr(new Series(1, "NIFTY", "Nifty", candles), start);
    // 100 to 110 over 365 days: 10.0% a year, with a 365 / 365.25 year fraction.
    assertThat(cagr).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.0005));
  }

  @Test
  void aHoldingLimitChangeKeepsEveryOtherPlanField() {
    TradePlan p =
        new TradePlan(
            new Entry(EntryType.BUY_STOP, bd(100), bd(102), 3),
            bd(95),
            "reason",
            List.of(),
            new TrailingRule.None(),
            new Invalidation.None(),
            15);
    TradePlan q = StrategyReplay.withMaxHold(p, 10);
    assertThat(q.maxHoldSessions()).isEqualTo(10);
    assertThat(q.stop()).isEqualByComparingTo("95");
    assertThat(q.entry()).isEqualTo(p.entry());
  }

  @Test
  void theRequestNeedsItsDatesInOrder() {
    LocalDate d = LocalDate.of(2023, 1, 1);
    assertThatThrownBy(() -> new GateRunService.Request("momentum-breakout", d, d, d.plusYears(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private static BigDecimal bd(int v) {
    return BigDecimal.valueOf(v);
  }

  private static PlanPortfolioSimulator.Signal signal(
      LocalDate day, long id, String symbol, int score) {
    TradePlan plan =
        new TradePlan(
            new Entry(EntryType.BUY_STOP, bd(100), bd(102), 3),
            bd(95),
            "r",
            List.of(),
            new TrailingRule.None(),
            new Invalidation.None(),
            15);
    return new PlanPortfolioSimulator.Signal(day, id, symbol, plan, bd(score));
  }
}
