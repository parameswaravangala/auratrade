package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monthly budget and the fractions of it at which behaviour changes. */
record BudgetLimits(
    BigDecimal monthlyBudgetUsd,
    BigDecimal downgradeAt,
    BigDecimal floorAt,
    BigDecimal nonCriticalStopAt,
    BigDecimal criticalStopAt) {

  BudgetLimits {
    Objects.requireNonNull(monthlyBudgetUsd, "monthlyBudgetUsd");
    if (monthlyBudgetUsd.signum() <= 0) {
      throw new IllegalArgumentException("monthlyBudgetUsd must be positive");
    }
    if (downgradeAt.compareTo(floorAt) > 0
        || floorAt.compareTo(nonCriticalStopAt) > 0
        || nonCriticalStopAt.compareTo(criticalStopAt) > 0) {
      throw new IllegalArgumentException(
          "thresholds must satisfy downgradeAt <= floorAt <= nonCriticalStopAt <= criticalStopAt");
    }
  }

  static BudgetLimits defaults(BigDecimal monthlyBudgetUsd) {
    return new BudgetLimits(
        monthlyBudgetUsd,
        new BigDecimal("0.60"),
        new BigDecimal("0.75"),
        new BigDecimal("0.85"),
        new BigDecimal("1.00"));
  }

  BigDecimal usedFraction(BigDecimal spendUsd) {
    return spendUsd.divide(monthlyBudgetUsd, 6, RoundingMode.HALF_UP);
  }
}
