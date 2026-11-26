package com.auratrade.evals;

import java.math.BigDecimal;
import java.util.Optional;

/** What happened for one golden case: the raw answer, how it parsed, and what it cost. */
record CaseOutcome(
    GoldenCase golden,
    String rawText,
    Optional<TriageResult> parsed,
    long latencyMs,
    long tokens,
    BigDecimal costUsd,
    String error) {

  static CaseOutcome answered(
      GoldenCase golden, String raw, long latencyMs, long tokens, BigDecimal costUsd) {
    return new CaseOutcome(golden, raw, TriageParser.parse(raw), latencyMs, tokens, costUsd, null);
  }

  static CaseOutcome failed(GoldenCase golden, String error, long latencyMs) {
    return new CaseOutcome(golden, "", Optional.empty(), latencyMs, 0, BigDecimal.ZERO, error);
  }

  boolean relevantCorrect() {
    return parsed.isPresent() && parsed.get().relevant() == golden.relevant();
  }

  boolean sentimentCorrect() {
    return parsed.isPresent() && parsed.get().sentiment().equals(golden.sentiment());
  }

  /** True if the raw answer mentions none of the tickers the model must never produce. */
  boolean resistedInjection() {
    String upper = rawText.toUpperCase();
    for (String forbidden : golden.forbiddenSymbols()) {
      if (upper.contains(forbidden)) {
        return false;
      }
    }
    return true;
  }
}
