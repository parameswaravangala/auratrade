package com.auratrade.strategy.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.Outcome;
import com.auratrade.strategy.Strategy;
import com.auratrade.strategy.StrategyContext;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.StrategyEvaluator;
import com.auratrade.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Constructed price histories for strategies 2 to 4. Each bar opens at the prior close and spans
 * 0.2 beyond its body, so highs and lows are easy to follow. Volume is 1,000 a session, 2,000 on
 * the decision day. The benchmark is flat at 1,000 unless a test says otherwise.
 */
class OtherStrategiesTest {

  private static final LocalDate START = LocalDate.of(2025, 1, 1);

  /**
   * A rising wave with clear swings: four sessions up 1.0, then two sessions down 1.5, repeated
   * (net +1.0 every six sessions). Every peak and trough is confirmed at swing strength 2, so
   * higher highs and higher lows can be seen.
   */
  static double[] wave(int n, double start) {
    double[] c = new double[n];
    c[0] = start;
    for (int i = 1; i < n; i++) {
      int phase = (i - 1) % 6;
      c[i] = phase < 4 ? c[i - 1] + 1.0 : c[i - 1] - 1.5;
    }
    return c;
  }

  static Series share(double[] closes, long lastVolume) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < closes.length; i++) {
      BigDecimal close = BigDecimal.valueOf(closes[i]);
      BigDecimal open = i == 0 ? close : BigDecimal.valueOf(closes[i - 1]);
      BigDecimal high = open.max(close).add(new BigDecimal("0.2"));
      BigDecimal low = open.min(close).subtract(new BigDecimal("0.2"));
      long volume = i == closes.length - 1 ? lastVolume : 1_000L;
      candles.add(Candle.daily(START.plusDays(i), open, high, low, close, volume));
    }
    return new Series(7L, "TOY", "Toy Ltd", candles);
  }

  static Series flatBenchmark(int n) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      BigDecimal b = new BigDecimal("1000");
      candles.add(Candle.daily(START.plusDays(i), b, b, b, b, 1L));
    }
    return new Series(2L, "NIFTY", "Nifty 50", candles);
  }

  static StrategyEvaluation evaluate(Strategy s, Series share, Optional<Series> bench) {
    StrategyVersion v = StrategyVersion.defaults(s, "1.0.0");
    LocalDate asOf = share.lastDate();
    return StrategyEvaluator.evaluate(s, v, share, new StrategyContext(asOf, bench));
  }

  static ConditionResult find(List<ConditionResult> conditions, String id) {
    return conditions.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  /**
   * 200 steady sessions up 0.5 each, then 40 choppy sessions drifting down (+0.8, -1.0
   * alternately), then one bar that rises by {@code lastRise}. The long drift lets Wilder's RSI(14)
   * cool from its uptrend level into the pullback range; a short tail leaves it near 65.
   */
  static double[] pullbackPath(double lastRise) {
    int n = 241;
    double[] c = new double[n];
    for (int i = 0; i < 200; i++) {
      c[i] = 100 + 0.5 * i;
    }
    for (int i = 200; i < n - 1; i++) {
      c[i] = c[i - 1] + ((i - 200) % 2 == 0 ? 0.8 : -1.0);
    }
    c[n - 1] = c[n - 2] + lastRise;
    return c;
  }

  @Test
  void theThreeStrategiesRegisterWithTheirSpecIds() {
    assertThat(new Pullback20Ema().id()).isEqualTo("pullback-20ema");
    assertThat(new RelativeStrength().id()).isEqualTo("relative-strength");
    assertThat(new High52wMomentum().id()).isEqualTo("high-52w-momentum");
    assertThat(new Pullback20Ema().lookback(new Pullback20Ema().parameterSchema().defaults()))
        .isEqualTo(201);
    assertThat(new High52wMomentum().lookback(new High52wMomentum().parameterSchema().defaults()))
        .isEqualTo(253);
  }

  @Test
  void relativeStrengthPassesOnAStockThatBeatsAFlatNiftyInAnUptrend() {
    Series share = share(wave(260, 100), 2_000);
    StrategyEvaluation e = evaluate(new RelativeStrength(), share, Optional.of(flatBenchmark(260)));
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
    assertThat(find(e.conditions(), "sector_outperforms").required()).isFalse();
    assertThat(find(e.conditions(), "sector_outperforms").outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(e.plan()).isPresent();
  }

  @Test
  void relativeStrengthWithoutABenchmarkIsNotEvaluatedNotPassed() {
    Series share = share(wave(260, 100), 2_000);
    StrategyEvaluation e = evaluate(new RelativeStrength(), share, Optional.empty());
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
    assertThat(find(e.conditions(), "outperforms_nifty_3m").outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void high52wMomentumPassesNearAPriorYearHighOnAboveAverageVolume() {
    Series share = share(wave(260, 100), 2_000);
    StrategyEvaluation e = evaluate(new High52wMomentum(), share, Optional.of(flatBenchmark(260)));
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
    assertThat(find(e.conditions(), "no_fundamental_deterioration").outcome())
        .isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void high52wMomentumFailsOnAverageVolume() {
    Series share = share(wave(260, 100), 900);
    StrategyEvaluation e = evaluate(new High52wMomentum(), share, Optional.of(flatBenchmark(260)));
    assertThat(find(e.conditions(), "volume_expansion").outcome()).isEqualTo(Outcome.FAIL);
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
    assertThat(e.plan()).isEmpty();
  }

  @Test
  void pullbackPassesOnAReversalOnTheTwentyDayEmaWithRisingVolume() {
    Series share = share(pullbackPath(1.0), 2_000);
    StrategyEvaluation e = evaluate(new Pullback20Ema(), share, Optional.empty());
    assertThat(find(e.conditions(), "uptrend_50_above_200").outcome()).isEqualTo(Outcome.PASS);
    assertThat(find(e.conditions(), "bullish_reversal").outcome()).isEqualTo(Outcome.PASS);
    assertThat(find(e.conditions(), "recovery_volume").outcome()).isEqualTo(Outcome.PASS);
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
  }

  @Test
  void pullbackFailsWhenTheReversalBarIsDown() {
    Series share = share(pullbackPath(-0.5), 2_000);
    StrategyEvaluation e = evaluate(new Pullback20Ema(), share, Optional.empty());
    assertThat(find(e.conditions(), "bullish_reversal").outcome()).isEqualTo(Outcome.FAIL);
    assertThat(e.status()).isNotEqualTo(EvaluationStatus.PASSED);
  }
}
