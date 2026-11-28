package com.auratrade.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.EvaluationStatus;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.TradePlan;
import com.auratrade.strategy.TradePlan.Entry;
import com.auratrade.strategy.TradePlan.EntryType;
import com.auratrade.strategy.TradePlan.Invalidation;
import com.auratrade.strategy.TradePlan.TrailingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SelectionTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 26);

  private static TradePlan plan() {
    return new TradePlan(
        new Entry(EntryType.BUY_STOP, new BigDecimal("100"), new BigDecimal("102"), 3),
        new BigDecimal("95"),
        "below the breakout",
        List.of(),
        new TrailingRule.None(),
        new Invalidation.None(),
        10);
  }

  private static StrategyEvaluation passed(String ref, String score) {
    return new StrategyEvaluation(
        ref,
        1,
        "X",
        DAY,
        EvaluationStatus.PASSED,
        List.of(ConditionResult.check("c", "d", Optional.of(BigDecimal.ONE), v -> true, "t")),
        new BigDecimal(score),
        "e",
        Optional.of(plan()));
  }

  /** Exactly one required condition failed and none was unknown: PARTIAL. */
  private static StrategyEvaluation partial(String ref) {
    return new StrategyEvaluation(
        ref,
        1,
        "X",
        DAY,
        EvaluationStatus.PARTIAL,
        List.of(ConditionResult.check("c", "d", Optional.of(BigDecimal.ONE), v -> false, "t")),
        BigDecimal.ZERO,
        "e",
        Optional.empty());
  }

  private static StrategyEvaluation failed(String ref) {
    return new StrategyEvaluation(
        ref,
        1,
        "X",
        DAY,
        EvaluationStatus.FAILED,
        List.of(
            ConditionResult.check("c", "d", Optional.of(BigDecimal.ONE), v -> false, "t"),
            ConditionResult.check("c2", "d2", Optional.of(BigDecimal.ONE), v -> false, "t")),
        BigDecimal.ZERO,
        "e",
        Optional.empty());
  }

  private static Selection.ShareEvaluations share(
      long id, String symbol, StrategyEvaluation... evals) {
    return new Selection.ShareEvaluations(id, symbol, symbol + " Ltd", List.of(evals));
  }

  @Test
  void shortlistIsOrderedByBestPassedScoreThenSymbol() {
    var result =
        Selection.rank(
            List.of(
                share(1, "BBB", passed("a@1.0.0", "60")),
                share(2, "AAA", passed("a@1.0.0", "80")),
                share(3, "CCC", passed("a@1.0.0", "60"), passed("b@1.0.0", "90"))),
            10,
            Map.of());
    assertThat(result.shortlist())
        .extracting(Selection.Candidate::symbol)
        .containsExactly("CCC", "AAA", "BBB");
    assertThat(result.shortlist().get(0).bestScore()).isEqualByComparingTo("90");
    assertThat(result.shortlist().get(0).passedStrategies()).containsExactly("a@1.0.0", "b@1.0.0");
    assertThat(result.passing()).isEqualTo(3);
  }

  @Test
  void theLimitIsCappedAtTheMaximumShortlist() {
    List<Selection.ShareEvaluations> many = new java.util.ArrayList<>();
    for (int i = 0; i < 15; i++) {
      many.add(share(i, "S" + String.format("%02d", i), passed("a@1.0.0", "50")));
    }
    assertThat(Selection.rank(many, 50, Map.of()).shortlist()).hasSize(Selection.MAX_SHORTLIST);
    assertThat(Selection.rank(many, 3, Map.of()).shortlist()).hasSize(3);
    assertThat(Selection.rank(many, 3, Map.of()).passing()).isEqualTo(15);
    assertThatThrownBy(() -> Selection.rank(many, 0, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void almostQualifiedListsPartialSetupsOnly() {
    var result =
        Selection.rank(
            List.of(
                share(1, "NEAR", partial("a@1.0.0")),
                share(2, "FAR", failed("a@1.0.0")),
                share(3, "PASS", passed("a@1.0.0", "70"))),
            10,
            Map.of());
    assertThat(result.almostQualified())
        .extracting(Selection.Candidate::symbol)
        .containsExactly("NEAR");
    assertThat(result.almostQualified().get(0).passedStrategies()).isEmpty();
    assertThat(result.shortlist()).extracting(Selection.Candidate::symbol).containsExactly("PASS");
  }

  @Test
  void everyStrategyResultIsKeptOnTheCandidate() {
    var result =
        Selection.rank(
            List.of(share(1, "AAA", passed("a@1.0.0", "70"), failed("b@1.0.0"))), 10, Map.of());
    assertThat(result.shortlist().get(0).evaluations()).hasSize(2);
    assertThat(result.shortlist().get(0).evaluations())
        .extracting(StrategyEvaluation::status)
        .containsExactly(EvaluationStatus.PASSED, EvaluationStatus.FAILED);
  }

  @Test
  void skippedSharesAreReportedInOrder() {
    var result = Selection.rank(List.of(), 10, Map.of("STALE", 4, "ILLIQUID", 2));
    assertThat(result.skipped()).containsExactly(Map.entry("ILLIQUID", 2), Map.entry("STALE", 4));
    assertThat(result.evaluated()).isZero();
    assertThat(result.shortlist()).isEmpty();
  }
}
