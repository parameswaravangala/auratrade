package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.OptionalInt;

/**
 * Decides when to raise a budget alert: once per level (50, 75, 90, 100 percent) per month, and
 * only the highest level crossed. State is in memory, so one repeat alert is possible after a
 * restart. Pure logic, no I/O.
 */
final class BudgetAlertTracker {

  private static final int[] LEVELS_DESCENDING = {100, 90, 75, 50};

  private YearMonth month;
  private int lastAlertedLevel;

  synchronized OptionalInt check(YearMonth current, BigDecimal usedFraction) {
    if (!current.equals(month)) {
      month = current;
      lastAlertedLevel = 0;
    }
    int percent = usedFraction.multiply(BigDecimal.valueOf(100)).intValue();
    for (int level : LEVELS_DESCENDING) {
      if (percent >= level) {
        if (level > lastAlertedLevel) {
          lastAlertedLevel = level;
          return OptionalInt.of(level);
        }
        return OptionalInt.empty();
      }
    }
    return OptionalInt.empty();
  }
}
