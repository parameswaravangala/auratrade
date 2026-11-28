package com.auratrade.strategy.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.Outcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The regime filter on the pullback fixture: on, it blocks entries only when the index is bearish.
 */
class RegimeFilterTest {

  private static final LocalDate START = LocalDate.of(2025, 1, 1);

  private static Series index(double start, double step) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < 241; i++) {
      BigDecimal c = BigDecimal.valueOf(start + step * i);
      candles.add(Candle.daily(START.plusDays(i), c, c, c, c, 1L));
    }
    return new Series(2L, "NIFTY", "Nifty 50", candles);
  }

  private static com.auratrade.strategy.StrategyEvaluation evaluate(
      Optional<Series> bench, int regimeFilter) {
    var strategy = new Pullback20Ema();
    var version =
        com.auratrade.strategy.StrategyVersion.of(
            strategy, "1.0.0", Map.of("regimeFilter", BigDecimal.valueOf(regimeFilter)));
    var share =
        com.auratrade.strategy.builtin.OtherStrategiesTest.share(
            com.auratrade.strategy.builtin.OtherStrategiesTest.pullbackPath(1.0), 2_000);
    return com.auratrade.strategy.StrategyEvaluator.evaluate(
        strategy,
        version,
        share,
        new com.auratrade.strategy.StrategyContext(share.lastDate(), bench));
  }

  @Test
  void theFilterIsOffByDefault() {
    assertThat(evaluate(Optional.empty(), 0).status()).isEqualTo(EvaluationStatus.PASSED);
  }

  @Test
  void aSidewaysIndexLetsTheSetupThrough() {
    var e = evaluate(Optional.of(index(1000, 0)), 1);
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
    assertThat(e.conditions())
        .filteredOn(c -> c.id().equals("regime_not_bearish"))
        .extracting(c -> c.outcome())
        .containsExactly(Outcome.PASS);
  }

  @Test
  void aBearishIndexBlocksTheSetup() {
    var e = evaluate(Optional.of(index(1200, -0.5)), 1);
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
    assertThat(e.plan()).isEmpty();
    assertThat(e.blockers()).extracting(c -> c.id()).contains("regime_not_bearish");
  }

  @Test
  void withNoIndexTheFilterFailsClosed() {
    var e = evaluate(Optional.empty(), 1);
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
    assertThat(e.conditions())
        .filteredOn(c -> c.id().equals("regime_not_bearish"))
        .extracting(c -> c.outcome())
        .containsExactly(Outcome.UNKNOWN);
  }
}
