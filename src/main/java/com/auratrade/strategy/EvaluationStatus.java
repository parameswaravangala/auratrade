package com.auratrade.strategy;

/**
 * Overall result of one strategy for one share and session, derived from the required conditions
 * only (ADR 0038, plan decision D4).
 */
public enum EvaluationStatus {
  /** Every required condition passed; the evaluation carries a score and a trade plan. */
  PASSED,
  /** Exactly one required condition failed and none was unknown ("almost qualified"). */
  PARTIAL,
  /** Two or more required conditions failed, or one failed and another was unknown. */
  FAILED,
  /** No required condition failed but at least one could not be decided (missing data). */
  NOT_EVALUATED;

  static EvaluationStatus of(int required, int failed, int unknown) {
    if (required == 0) {
      throw new IllegalStateException("a strategy needs at least one required condition");
    }
    if (failed == 0) {
      return unknown == 0 ? PASSED : NOT_EVALUATED;
    }
    return failed == 1 && unknown == 0 ? PARTIAL : FAILED;
  }
}
