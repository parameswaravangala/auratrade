package com.auratrade.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScorerTest {

  private static GoldenCase golden(
      String id, boolean relevant, String sentiment, Set<String> symbols, Set<String> forbidden) {
    return new GoldenCase(id, "text", relevant, sentiment, symbols, forbidden, List.of());
  }

  private static CaseOutcome answer(GoldenCase g, String raw) {
    return CaseOutcome.answered(g, raw, 100, 50, new BigDecimal("0.01"));
  }

  @Test
  void scoresAMixOfRightWrongAndUnparseableAnswers() {
    var a = golden("a", true, "positive", Set.of("TCS"), Set.of());
    var b = golden("b", false, "neutral", Set.of(), Set.of());
    var c = golden("c", true, "negative", Set.of("INFY"), Set.of());
    var d = golden("d", true, "positive", Set.of("WIPRO"), Set.of("XYZCORP"));

    var outcomes =
        List.of(
            answer(a, "{\"relevant\": true, \"sentiment\": \"positive\", \"symbols\": [\"TCS\"]}"),
            answer(b, "{\"relevant\": true, \"sentiment\": \"neutral\", \"symbols\": [\"TCS\"]}"),
            answer(c, "I am not sure"),
            answer(
                d,
                "{\"relevant\": true, \"sentiment\": \"positive\", \"symbols\": [\"WIPRO\", \"XYZCORP\"]}"));

    Map<String, Double> m = Scorer.score(outcomes);

    assertThat(m.get("parse_rate")).isEqualTo(0.75);
    assertThat(m.get("relevant_accuracy"))
        .isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(m.get("sentiment_accuracy"))
        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    // tp: TCS, WIPRO = 2; fp: TCS (case b), XYZCORP = 2; fn: 0 (c is unparsed and ignored)
    assertThat(m.get("symbol_f1")).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(1e-3));
    assertThat(m.get("injection_resisted")).isEqualTo(0.0);
    assertThat(m.get("cases")).isEqualTo(4.0);
    assertThat(m.get("total_tokens")).isEqualTo(200.0);
    assertThat(m.get("total_cost_usd")).isCloseTo(0.04, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void aPerfectRunScoresOne() {
    var a = golden("a", true, "positive", Set.of("TCS"), Set.of());
    var m =
        Scorer.score(
            List.of(
                answer(
                    a,
                    "{\"relevant\": true, \"sentiment\": \"positive\", \"symbols\": [\"TCS\"]}")));

    for (String metric : Scorer.QUALITY) {
      assertThat(m.get(metric)).as(metric).isEqualTo(1.0);
    }
  }

  @Test
  void detectsRegressionsBeyondTheTolerance() {
    Map<String, Double> baseline = Map.of("parse_rate", 1.0, "symbol_f1", 0.80);
    Map<String, Double> current = Map.of("parse_rate", 0.97, "symbol_f1", 0.70);

    List<String> regressions = EvalReporter.regressions(current, baseline, 0.05);

    assertThat(regressions).hasSize(1);
    assertThat(regressions.get(0)).contains("symbol_f1");
  }
}
