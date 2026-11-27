package com.auratrade.strategy.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.InvalidParametersException;
import com.auratrade.strategy.Outcome;
import com.auratrade.strategy.StrategyContext;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.StrategyEvaluator;
import com.auratrade.strategy.StrategyVersion;
import com.auratrade.strategy.TradePlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Synthetic series, 80 sessions. Closes: 100 on day 0; each odd day rises 1.0 from the day before;
 * each even day falls 0.7. So the closes drift up 0.3 every two sessions. Highs are close + 0.2,
 * lows are close - 0.2, volume 1,000. On day 79 the close is 112.7, which is above the prior
 * 20-session high of 112.6 (day 77's close 112.4 + 0.2), so it is a breakout by 0.1. RSI(14)
 * settles near 62 on this pattern. A 3-month share return of about 9% against a flat benchmark is
 * positive.
 */
class MomentumBreakoutTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final MomentumBreakout STRATEGY = new MomentumBreakout();

  private static Series series(String lastClose, long lastVolume) {
    List<Candle> candles = new ArrayList<>();
    BigDecimal prev = null;
    for (int i = 0; i < 80; i++) {
      BigDecimal close;
      if (i == 0) {
        close = new BigDecimal("100");
      } else if (i == 79 && lastClose != null) {
        close = new BigDecimal(lastClose);
      } else if (i % 2 == 1) {
        close = prev.add(BigDecimal.ONE);
      } else {
        close = prev.subtract(new BigDecimal("0.7"));
      }
      long volume = i == 79 ? lastVolume : 1_000L;
      candles.add(
          Candle.daily(
              START.plusDays(i),
              close,
              close.add(new BigDecimal("0.2")),
              close.subtract(new BigDecimal("0.2")),
              close,
              volume));
      prev = close;
    }
    return new Series(1L, "TOY", "Toy Ltd", candles);
  }

  /** Flat benchmark over the same dates. */
  private static Series flatBenchmark() {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      BigDecimal b = new BigDecimal("1000");
      candles.add(Candle.daily(START.plusDays(i), b, b, b, b, 1L));
    }
    return new Series(2L, "NIFTY", "Nifty 50", candles);
  }

  private static StrategyContext context(Optional<Series> benchmark) {
    return new StrategyContext(START.plusDays(79), benchmark);
  }

  private static StrategyEvaluation evaluate(Series share, Optional<Series> benchmark) {
    StrategyVersion v = StrategyVersion.defaults(STRATEGY, "1.0.0");
    return StrategyEvaluator.evaluate(STRATEGY, v, share, context(benchmark));
  }

  private static ConditionResult find(List<ConditionResult> conditions, String id) {
    return conditions.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void aBreakoutOnDoubleVolumePasses() {
    StrategyEvaluation e = evaluate(series(null, 2_000), Optional.of(flatBenchmark()));
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
    assertThat(find(e.conditions(), "volume_ratio").value())
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("2"));
    assertThat(find(e.conditions(), "rsi_in_range").value())
        .hasValueSatisfying(
            v -> assertThat(v).isBetween(new BigDecimal("55"), new BigDecimal("70")));
    assertThat(find(e.conditions(), "outperforms_nifty_3m").outcome()).isEqualTo(Outcome.PASS);
    assertThat(e.plan()).isPresent();
  }

  @Test
  void theBuyStopSitsAboveTheBreakoutHighPlusTheBuffer() {
    // Breakout day high 112.7 + 0.2 = 112.9; trigger = 112.9 x 1.001 = 113.0129.
    TradePlan plan =
        evaluate(series(null, 2_000), Optional.of(flatBenchmark())).plan().orElseThrow();
    assertThat(plan.entry().trigger()).isEqualByComparingTo("113.0129");
    // No-chase limit: trigger x 1.02 = 115.273158.
    assertThat(plan.entry().maxPrice()).isEqualByComparingTo("115.273158");
    assertThat(plan.entry().validSessions()).isEqualTo(3);
    assertThat(plan.stop()).isLessThan(plan.entry().trigger());
  }

  @Test
  void lowVolumeFailsTheVolumeCheckAndTheSetupIsNotActionable() {
    // A volume of 1,000 against a 1,000 average is a ratio of 1, below 1.5.
    StrategyEvaluation e = evaluate(series(null, 1_000), Optional.of(flatBenchmark()));
    assertThat(find(e.conditions(), "volume_ratio").outcome()).isEqualTo(Outcome.FAIL);
    assertThat(e.status()).isEqualTo(EvaluationStatus.PARTIAL);
    assertThat(e.plan()).isEmpty();
    assertThat(e.score()).isEqualByComparingTo("0");
  }

  @Test
  void aCloseBelowResistanceIsNotABreakout() {
    // Last close 111.7, below the prior high of 112.6.
    StrategyEvaluation e = evaluate(series("111.7", 2_000), Optional.of(flatBenchmark()));
    assertThat(find(e.conditions(), "breakout_above_resistance").outcome()).isEqualTo(Outcome.FAIL);
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
  }

  @Test
  void aMissingBenchmarkIsUnknownButDoesNotBlockTheSetup() {
    // Outperformance is preferred, not required: UNKNOWN leaves the status PASSED.
    StrategyEvaluation e = evaluate(series(null, 2_000), Optional.empty());
    assertThat(find(e.conditions(), "outperforms_nifty_3m").outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
  }

  @Test
  void lookbackCoversTheLongestWindow() {
    assertThat(STRATEGY.lookback(STRATEGY.parameterSchema().defaults())).isEqualTo(64);
  }

  @Test
  void parametersOutsideTheSpecRangesAreRejected() {
    assertThatThrownBy(() ->
            StrategyVersion.of(STRATEGY, "1.0.0", Map.of("minVolumeRatio", new BigDecimal("1.0"))))
        .isInstanceOf(InvalidParametersException.class)
        .hasMessageContaining("minVolumeRatio");
    assertThatThrownBy(() ->
            StrategyVersion.of(
                STRATEGY, "1.0.0", Map.of("resistanceLookback", new BigDecimal("40"))))
        .isInstanceOf(InvalidParametersException.class);
  }

  @Test
  void theSpecDefaultsAreTheDefaults() {
    var p = STRATEGY.parameterSchema().defaults();
    assertThat(p.integer("resistanceLookback")).isEqualTo(20);
    assertThat(p.decimal("minVolumeRatio")).isEqualByComparingTo("1.5");
    assertThat(p.decimal("rsiMin")).isEqualByComparingTo("55");
    assertThat(p.decimal("rsiMax")).isEqualByComparingTo("70");
    assertThat(p.integer("maxHold")).isEqualTo(15);
  }
}
