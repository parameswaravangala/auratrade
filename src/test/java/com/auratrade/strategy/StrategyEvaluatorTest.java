package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.indicators.Indicators;
import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.Target;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StrategyEvaluatorTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);

  /**
   * Test strategy with two required conditions (close above its EMA, last volume at least {@code
   * minRatio} x average) and one optional one that is always UNKNOWN (no sector data).
   */
  static class ToyStrategy implements Strategy {
    static final ParameterSchema SCHEMA =
        ParameterSchema.builder()
            .integer("emaPeriod", "EMA length", 2, 50, 3)
            .decimal("minRatio", "Volume ratio", "1", "5", "2")
            .build();

    TradePlan planToReturn;
    BigDecimal quality = BigDecimal.valueOf(80);
    boolean returnPlan = true;

    @Override
    public String id() {
      return "toy-strategy";
    }

    @Override
    public String name() {
      return "Toy";
    }

    @Override
    public String description() {
      return "For tests";
    }

    @Override
    public ParameterSchema parameterSchema() {
      return SCHEMA;
    }

    @Override
    public Set<DataRequirement> requires() {
      return Set.of(DataRequirement.DAILY_CANDLES, DataRequirement.SECTOR_INDEX);
    }

    @Override
    public int lookback(Parameters p) {
      return p.integer("emaPeriod") + 1;
    }

    @Override
    public Assessment assess(Series share, StrategyContext context, Parameters p) {
      List<BigDecimal> closes = share.candles().stream().map(Candle::close).toList();
      List<BigDecimal> volumes =
          share.candles().stream().map(c -> BigDecimal.valueOf(c.volume())).toList();
      BigDecimal close = closes.get(closes.size() - 1);
      Optional<BigDecimal> ema = Indicators.ema(closes, p.integer("emaPeriod"));
      List<ConditionResult> conditions = new ArrayList<>();
      conditions.add(
          ConditionResult.check(
              "close_above_ema",
              "Close above the EMA",
              ema.map(close::subtract),
              v -> v.signum() > 0,
              "> 0"));
      conditions.add(
          ConditionResult.check(
              "volume_ratio",
              "Volume against average",
              Indicators.ratioToAverage(volumes, 2),
              v -> v.compareTo(p.decimal("minRatio")) >= 0,
              ">= " + p.decimal("minRatio")));
      conditions.add(
          ConditionResult.unavailable(
              "sector_outperforms", "Sector beats Nifty", false, "no sector data"));
      Optional<TradePlan> plan =
          returnPlan
              ? Optional.of(
                  planToReturn != null
                      ? planToReturn
                      : new TradePlan(
                          new Entry(EntryType.NEXT_OPEN, close, close, 1),
                          close.subtract(BigDecimal.ONE),
                          "one rupee",
                          List.of(new Target(BigDecimal.valueOf(2), BigDecimal.ONE)),
                          new TrailingRule.None(),
                          new Invalidation.None(),
                          10))
              : Optional.empty();
      return new Assessment(conditions, quality, "toy explanation", plan);
    }
  }

  private static Series series(String closesCsv, String volumesCsv) {
    String[] closes = closesCsv.split(",");
    String[] volumes = volumesCsv.split(",");
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < closes.length; i++) {
      BigDecimal c = new BigDecimal(closes[i]);
      candles.add(
          Candle.daily(
              START.plusDays(i), c, c.add(BigDecimal.ONE), c, c, Long.parseLong(volumes[i])));
    }
    return new Series(7L, "TOY", "Toy Ltd", candles);
  }

  private static LocalDate lastDay(Series s) {
    return s.lastDate();
  }

  private final ToyStrategy strategy = new ToyStrategy();
  private final StrategyVersion v1 = StrategyVersion.defaults(strategy, "1.0.0");

  private StrategyEvaluation run(Series s) {
    return StrategyEvaluator.evaluate(
        strategy, v1, s, new StrategyContext(lastDay(s), Optional.empty()));
  }

  @Test
  void passesWhenEveryRequiredConditionPassesEvenWithAnOptionalUnknown() {
    // EMA(3) of 10,11,12,13: seed 11, then 12. Close 13 > 12. Volume 300 / avg(100,100) = 3 >= 2.
    StrategyEvaluation e = run(series("10,11,12,13", "100,100,100,300"));
    assertThat(e.status()).isEqualTo(EvaluationStatus.PASSED);
    assertThat(e.score()).isEqualByComparingTo("80");
    assertThat(e.plan()).isPresent();
    assertThat(e.strategyRef()).isEqualTo("toy-strategy@1.0.0");
    assertThat(e.conditions())
        .filteredOn(c -> c.id().equals("sector_outperforms"))
        .extracting(ConditionResult::outcome)
        .containsExactly(Outcome.UNKNOWN);
    assertThat(e.blockers()).isEmpty();
  }

  @Test
  void oneFailedRequiredConditionIsPartialWithZeroScoreAndNoPlan() {
    // Volume 150 / 100 = 1.5 < 2.
    StrategyEvaluation e = run(series("10,11,12,13", "100,100,100,150"));
    assertThat(e.status()).isEqualTo(EvaluationStatus.PARTIAL);
    assertThat(e.score()).isEqualByComparingTo("0");
    assertThat(e.plan()).isEmpty();
    assertThat(e.blockers()).extracting(ConditionResult::id).containsExactly("volume_ratio");
  }

  @Test
  void twoFailedRequiredConditionsIsFailed() {
    // Falling closes: EMA(3) of 13,12,11,10 = 11, close 10 below. Volume ratio 1.
    StrategyEvaluation e = run(series("13,12,11,10", "100,100,100,100"));
    assertThat(e.status()).isEqualTo(EvaluationStatus.FAILED);
    assertThat(e.blockers()).hasSize(2);
  }

  @Test
  void unknownRequiredDataIsNeverAPass() {
    // Average volume 0: the ratio cannot be measured, so the condition is UNKNOWN.
    StrategyEvaluation e = run(series("10,11,12,13", "0,0,0,300"));
    assertThat(e.status()).isEqualTo(EvaluationStatus.NOT_EVALUATED);
    assertThat(e.score()).isEqualByComparingTo("0");
    assertThat(e.plan()).isEmpty();
  }

  @Test
  void staleSeriesIsNotEvaluated() {
    Series s = series("10,11,12,13", "100,100,100,300");
    StrategyEvaluation e =
        StrategyEvaluator.evaluate(
            strategy, v1, s, new StrategyContext(lastDay(s).plusDays(1), Optional.empty()));
    assertThat(e.status()).isEqualTo(EvaluationStatus.NOT_EVALUATED);
    assertThat(e.conditions())
        .extracting(ConditionResult::id)
        .containsExactly(StrategyEvaluator.FRESH_DATA);
  }

  @Test
  void shortHistoryIsNotEvaluated() {
    // Lookback is emaPeriod + 1 = 4; three candles are not enough.
    StrategyEvaluation e = run(series("10,11,12", "100,100,300"));
    assertThat(e.status()).isEqualTo(EvaluationStatus.NOT_EVALUATED);
    assertThat(e.explanation()).contains("at least 4 sessions, have 3");
  }

  @Test
  void candlesAfterTheDecisionSessionAreRejected() {
    Series s = series("10,11,12,13", "100,100,100,300");
    assertThatThrownBy(() ->
            StrategyEvaluator.evaluate(
                strategy, v1, s, new StrategyContext(lastDay(s).minusDays(1), Optional.empty())))
        .hasMessageContaining("look-ahead");
  }

  @Test
  void passingWithoutAPlanIsABug() {
    strategy.returnPlan = false;
    assertThatThrownBy(() -> run(series("10,11,12,13", "100,100,100,300")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a trade plan");
  }

  @Test
  void qualityOutsideTheScaleIsABug() {
    strategy.quality = BigDecimal.valueOf(101);
    assertThatThrownBy(() -> run(series("10,11,12,13", "100,100,100,300")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void versionOfAnotherStrategyIsRejected() {
    Strategy other =
        new ToyStrategy() {
          @Override
          public String id() {
            return "other";
          }
        };
    StrategyVersion foreign = StrategyVersion.defaults(other, "1.0.0");
    Series s = series("10,11,12,13", "100,100,100,300");
    assertThatThrownBy(() ->
            StrategyEvaluator.evaluate(
                strategy, foreign, s, new StrategyContext(lastDay(s), Optional.empty())))
        .hasMessageContaining("does not belong");
  }

  @Test
  void versionsValidateTheirParametersAndVersionNumber() {
    assertThatThrownBy(
            () -> StrategyVersion.of(strategy, "1.0.0", Map.of("emaPeriod", BigDecimal.ONE)))
        .isInstanceOf(InvalidParametersException.class);
    assertThatThrownBy(() -> StrategyVersion.defaults(strategy, "v1"))
        .hasMessageContaining("1.0.0");
    assertThat(StrategyVersion.of(strategy, "1.1.0", Map.of("minRatio", new BigDecimal("1.5"))))
        .isNotEqualTo(v1);
  }

  @Test
  void sameInputsGiveTheSameEvaluation() {
    Series s = series("10,11,12,13", "100,100,100,300");
    assertThat(run(s)).isEqualTo(run(s));
  }
}
