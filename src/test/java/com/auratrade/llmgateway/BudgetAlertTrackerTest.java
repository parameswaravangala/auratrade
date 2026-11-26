package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BudgetAlertTrackerTest {

  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final YearMonth DEC = YearMonth.of(2026, 12);

  private final BudgetAlertTracker tracker = new BudgetAlertTracker();

  private java.util.OptionalInt check(YearMonth month, String used) {
    return tracker.check(month, new BigDecimal(used));
  }

  @Test
  void staysQuietBelowFiftyPercent() {
    assertThat(check(NOV, "0.00")).isEmpty();
    assertThat(check(NOV, "0.499")).isEmpty();
  }

  @Test
  void alertsOncePerLevel() {
    assertThat(check(NOV, "0.50")).hasValue(50);
    assertThat(check(NOV, "0.55")).isEmpty();
    assertThat(check(NOV, "0.76")).hasValue(75);
    assertThat(check(NOV, "0.80")).isEmpty();
    assertThat(check(NOV, "0.91")).hasValue(90);
    assertThat(check(NOV, "1.00")).hasValue(100);
    assertThat(check(NOV, "1.20")).isEmpty();
  }

  @Test
  void aJumpRaisesOnlyTheHighestLevelCrossed() {
    assertThat(check(NOV, "0.95")).hasValue(90);
    assertThat(check(NOV, "0.96")).isEmpty();
  }

  @Test
  void resetsWhenTheMonthChanges() {
    assertThat(check(NOV, "0.80")).hasValue(75);
    assertThat(check(DEC, "0.10")).isEmpty();
    assertThat(check(DEC, "0.51")).hasValue(50);
  }
}
