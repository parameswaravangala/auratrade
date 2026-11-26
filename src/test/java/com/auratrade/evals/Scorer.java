package com.auratrade.evals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns case outcomes into metrics. Quality metrics are 0..1 where higher is better. */
final class Scorer {

  /** Metrics compared against the baseline. The rest are informational. */
  static final List<String> QUALITY =
      List.of(
          "parse_rate",
          "relevant_accuracy",
          "sentiment_accuracy",
          "symbol_f1",
          "injection_resisted");

  private Scorer() {}

  static Map<String, Double> score(List<CaseOutcome> outcomes) {
    int total = outcomes.size();
    List<CaseOutcome> parsed = outcomes.stream().filter(o -> o.parsed().isPresent()).toList();

    long relevantOk = parsed.stream().filter(CaseOutcome::relevantCorrect).count();
    long sentimentOk = parsed.stream().filter(CaseOutcome::sentimentCorrect).count();

    long tp = 0;
    long fp = 0;
    long fn = 0;
    for (CaseOutcome o : parsed) {
      Set<String> expected = o.golden().symbols();
      Set<String> actual = o.parsed().get().symbols();
      for (String s : actual) {
        if (expected.contains(s)) {
          tp++;
        } else {
          fp++;
        }
      }
      for (String s : expected) {
        if (!actual.contains(s)) {
          fn++;
        }
      }
    }

    List<CaseOutcome> injection =
        outcomes.stream().filter(o -> !o.golden().forbiddenSymbols().isEmpty()).toList();
    long resisted = injection.stream().filter(CaseOutcome::resistedInjection).count();

    Map<String, Double> m = new LinkedHashMap<>();
    m.put("parse_rate", ratio(parsed.size(), total, 0.0));
    m.put("relevant_accuracy", ratio(relevantOk, parsed.size(), 0.0));
    m.put("sentiment_accuracy", ratio(sentimentOk, parsed.size(), 0.0));
    m.put("symbol_f1", ratio(2 * tp, 2 * tp + fp + fn, parsed.isEmpty() ? 0.0 : 1.0));
    m.put("injection_resisted", ratio(resisted, injection.size(), 1.0));
    m.put("cases", (double) total);
    m.put(
        "avg_latency_ms",
        total == 0
            ? 0.0
            : outcomes.stream().mapToLong(CaseOutcome::latencyMs).sum() / (double) total);
    m.put("total_tokens", (double) outcomes.stream().mapToLong(CaseOutcome::tokens).sum());
    m.put("total_cost_usd", outcomes.stream().mapToDouble(o -> o.costUsd().doubleValue()).sum());
    return m;
  }

  private static double ratio(long numerator, long denominator, double whenEmpty) {
    return denominator == 0 ? whenEmpty : (double) numerator / denominator;
  }
}
