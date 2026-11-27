package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Status rules, condition results, evaluation invariants and the registry. */
class StrategyModelTest {

  @Test
  void statusComesFromRequiredConditionCounts() {
    assertThat(EvaluationStatus.of(3, 0, 0)).isEqualTo(EvaluationStatus.PASSED);
    assertThat(EvaluationStatus.of(3, 0, 1)).isEqualTo(EvaluationStatus.NOT_EVALUATED);
    assertThat(EvaluationStatus.of(3, 1, 0)).isEqualTo(EvaluationStatus.PARTIAL);
    assertThat(EvaluationStatus.of(3, 1, 1)).isEqualTo(EvaluationStatus.FAILED);
    assertThat(EvaluationStatus.of(3, 2, 0)).isEqualTo(EvaluationStatus.FAILED);
    assertThatThrownBy(() -> EvaluationStatus.of(0, 0, 0))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkTurnsMissingValuesIntoUnknown() {
    ConditionResult missing =
        ConditionResult.check("rsi", "RSI in range", Optional.empty(), v -> true, "55 to 70");
    assertThat(missing.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(missing.required()).isTrue();

    ConditionResult pass =
        ConditionResult.check(
            "rsi",
            "RSI in range",
            Optional.of(new BigDecimal("61.123456")),
            v -> v.compareTo(BigDecimal.valueOf(55)) >= 0,
            "55 to 70");
    assertThat(pass.outcome()).isEqualTo(Outcome.PASS);
    assertThat(pass.value()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("61.1235"));

    ConditionResult optionalFail =
        ConditionResult.optional("x", "d", Optional.of(BigDecimal.ONE), v -> false, "never");
    assertThat(optionalFail.required()).isFalse();
    assertThat(optionalFail.outcome()).isEqualTo(Outcome.FAIL);
  }

  @Test
  void unknownConditionCannotCarryAValue() {
    assertThatThrownBy(() ->
            new ConditionResult("x", "d", true, Outcome.UNKNOWN, Optional.of(BigDecimal.ONE), "t"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void evaluationRejectsAScoreOrPlanWithoutAPass() {
    var c = ConditionResult.unavailable("x", "d", true, "t");
    assertThatThrownBy(() ->
            new StrategyEvaluation(
                "a@1.0.0",
                1,
                "A",
                LocalDate.of(2026, 1, 1),
                EvaluationStatus.PARTIAL,
                List.of(c),
                BigDecimal.TEN,
                "e",
                Optional.empty()))
        .hasMessageContaining("only a PASSED");
    assertThatThrownBy(() ->
            new StrategyEvaluation(
                "a@1.0.0",
                1,
                "A",
                LocalDate.of(2026, 1, 1),
                EvaluationStatus.PASSED,
                List.of(c),
                BigDecimal.TEN,
                "e",
                Optional.empty()))
        .hasMessageContaining("plan");
    assertThatThrownBy(() ->
            new StrategyEvaluation(
                "a@1.0.0",
                1,
                "A",
                LocalDate.of(2026, 1, 1),
                EvaluationStatus.FAILED,
                List.of(c, c),
                BigDecimal.ZERO,
                "e",
                Optional.empty()))
        .hasMessageContaining("duplicate");
  }

  @Test
  void registryRejectsDuplicateAndMalformedIds() {
    var toy = new StrategyEvaluatorTest.ToyStrategy();
    StrategyRegistry registry = new StrategyRegistry(List.of(toy));
    assertThat(registry.get("toy-strategy")).isSameAs(toy);
    assertThat(registry.find("missing")).isEmpty();
    assertThatThrownBy(() -> registry.get("missing")).hasMessageContaining("unknown strategy");
    assertThatThrownBy(() -> new StrategyRegistry(List.of(toy, toy)))
        .hasMessageContaining("duplicate");
    var badId =
        new StrategyEvaluatorTest.ToyStrategy() {
          @Override
          public String id() {
            return "Bad Id";
          }
        };
    assertThatThrownBy(() -> new StrategyRegistry(List.of(badId)))
        .hasMessageContaining("malformed");
  }
}
