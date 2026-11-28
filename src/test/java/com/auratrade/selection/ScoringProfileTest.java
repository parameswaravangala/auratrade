package com.auratrade.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.selection.ScoringProfile.Policy;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScoringProfileTest {

  private static final Set<String> MOMENTUM_AND_PULLBACK =
      Set.of("momentum-breakout", "pullback-20ema");

  private static Map<String, BigDecimal> scores(String... kv) {
    java.util.HashMap<String, BigDecimal> m = new java.util.HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], new BigDecimal(kv[i + 1]));
    }
    return m;
  }

  private static ScoringProfile profile(Policy policy) {
    return new ScoringProfile(
        "p",
        "P",
        policy,
        Map.of(
            "momentum-breakout", BigDecimal.valueOf(25),
            "pullback-20ema", BigDecimal.valueOf(20),
            "relative-strength", BigDecimal.valueOf(20),
            "high-52w-momentum", BigDecimal.valueOf(15)));
  }

  @Test
  void strictUsesTheConfiguredWeightsAndTreatsUnpassedAsZero() {
    // 25 x 80 + 20 x 0 (pullback did not pass) = 2,000, over 100 = 20.00. Weights total 45, so a
    // half-empty profile reads low: that is the point of STRICT.
    assertThat(
            profile(Policy.STRICT)
                .combined(scores("momentum-breakout", "80"), MOMENTUM_AND_PULLBACK))
        .isEqualByComparingTo("20.00");
  }

  @Test
  void autoNormalizedRescalesToTheApprovedWeights() {
    // Approved: momentum 25 and pullback 20 (total 45). Momentum 80 only: 25 x 80 / 45 = 44.44.
    assertThat(
            profile(Policy.AUTO_NORMALIZED)
                .combined(scores("momentum-breakout", "80"), MOMENTUM_AND_PULLBACK))
        .isEqualByComparingTo("44.44");
    // Both pass: (25 x 80 + 20 x 60) / 45 = 71.11.
    assertThat(
            profile(Policy.AUTO_NORMALIZED)
                .combined(
                    scores("momentum-breakout", "80", "pullback-20ema", "60"),
                    MOMENTUM_AND_PULLBACK))
        .isEqualByComparingTo("71.11");
  }

  @Test
  void aStrategyOutsideTheApprovedSetNeverCounts() {
    // Relative strength has a weight but is not approved: its score is ignored entirely.
    assertThat(
            profile(Policy.AUTO_NORMALIZED)
                .combined(
                    scores("momentum-breakout", "80", "relative-strength", "100"),
                    MOMENTUM_AND_PULLBACK))
        .isEqualByComparingTo("44.44");
  }

  @Test
  void nothingApprovedGivesZero() {
    assertThat(
            profile(Policy.AUTO_NORMALIZED).combined(scores("momentum-breakout", "90"), Set.of()))
        .isEqualByComparingTo("0");
  }

  @Test
  void weightsAboveOneHundredAreRejected() {
    assertThatThrownBy(() ->
            new ScoringProfile(
                "x",
                "X",
                Policy.STRICT,
                Map.of("a", BigDecimal.valueOf(60), "b", BigDecimal.valueOf(50))))
        .hasMessageContaining("above 100");
    assertThatThrownBy(
            () -> new ScoringProfile("x", "X", Policy.STRICT, Map.of("a", BigDecimal.ZERO)))
        .hasMessageContaining("positive");
    assertThatThrownBy(() -> new ScoringProfile("x", "X", Policy.STRICT, Map.of()))
        .hasMessageContaining("at least one");
  }

  @Test
  void theBalancedSwingProfileUsesTheSpecWeights() {
    assertThat(ScoringProfile.BALANCED_SWING.weights())
        .containsEntry("momentum-breakout", BigDecimal.valueOf(25))
        .containsEntry("pullback-20ema", BigDecimal.valueOf(20))
        .containsEntry("relative-strength", BigDecimal.valueOf(20))
        .containsEntry("high-52w-momentum", BigDecimal.valueOf(15))
        .hasSize(4);
    assertThat(ScoringProfile.BALANCED_SWING.policy()).isEqualTo(Policy.AUTO_NORMALIZED);
  }

  @Test
  void theCombinedScoreCanReorderSharesAgainstTheBestSingleScore() {
    // A: best score 90 from momentum alone. B: momentum 60 and pullback 90. The best single score
    // puts A first; the combined score puts B first: 44.44 against (25 x 60 + 20 x 90) / 45 =
    // 73.33.
    ScoringProfile p =
        new ScoringProfile(
            "two",
            "Two",
            Policy.AUTO_NORMALIZED,
            Map.of(
                "momentum-breakout", BigDecimal.valueOf(25),
                "pullback-20ema", BigDecimal.valueOf(20)));
    var a = share(1, "AAA", pass("momentum-breakout@1.0.0", "90"));
    var b =
        share(2, "BBB", pass("momentum-breakout@1.0.0", "60"), pass("pullback-20ema@1.0.0", "90"));
    Selection.Result byBest = Selection.rank(List.of(a, b), 10, Map.of(), MOMENTUM_AND_PULLBACK);
    assertThat(byBest.shortlist())
        .extracting(Selection.Candidate::symbol)
        .containsExactly("AAA", "BBB");
    Selection.Result byCombined =
        Selection.rank(List.of(a, b), 10, Map.of(), MOMENTUM_AND_PULLBACK, p);
    assertThat(byCombined.shortlist())
        .extracting(Selection.Candidate::symbol)
        .containsExactly("BBB", "AAA");
    assertThat(byCombined.shortlist().get(0).combinedScore()).isEqualByComparingTo("73.33");
    assertThat(byCombined.shortlist().get(1).combinedScore()).isEqualByComparingTo("50.00");
  }

  private static Selection.ShareEvaluations share(
      long id, String symbol, StrategyEvaluation... evals) {
    return new Selection.ShareEvaluations(id, symbol, symbol + " Ltd", List.of(evals));
  }

  private static StrategyEvaluation pass(String ref, String score) {
    LocalDate day = LocalDate.of(2026, 11, 26);
    TradePlan plan =
        new TradePlan(
            new Entry(EntryType.BUY_STOP, new BigDecimal("100"), new BigDecimal("102"), 3),
            new BigDecimal("95"),
            "below",
            List.of(),
            new TrailingRule.None(),
            new Invalidation.None(),
            10);
    return new StrategyEvaluation(
        ref,
        1,
        "X",
        day,
        EvaluationStatus.PASSED,
        List.of(ConditionResult.check("c", "d", Optional.of(BigDecimal.ONE), v -> true, "t")),
        new BigDecimal(score),
        "e",
        Optional.of(plan));
  }
}
