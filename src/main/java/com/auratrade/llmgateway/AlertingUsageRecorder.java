package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records usage, then checks whether spend crossed an alert level. An alert failure never affects
 * the agent run that triggered it. The alert sink is a log line for now; a phone or email channel
 * can replace it later without touching callers.
 */
final class AlertingUsageRecorder implements UsageRecorder {

  private static final Logger log = LoggerFactory.getLogger(AlertingUsageRecorder.class);

  private final UsageRecorder delegate;
  private final Supplier<BigDecimal> monthSpendUsd;
  private final BigDecimal budgetUsd;
  private final Clock clock;
  private final Consumer<String> alertSink;
  private final BudgetAlertTracker tracker = new BudgetAlertTracker();

  AlertingUsageRecorder(
      UsageRecorder delegate,
      Supplier<BigDecimal> monthSpendUsd,
      BigDecimal budgetUsd,
      Clock clock,
      Consumer<String> alertSink) {
    this.delegate = delegate;
    this.monthSpendUsd = monthSpendUsd;
    this.budgetUsd = budgetUsd;
    this.clock = clock;
    this.alertSink = alertSink;
  }

  @Override
  public void record(UsageEntry entry) {
    delegate.record(entry);
    try {
      BigDecimal spend = monthSpendUsd.get();
      BigDecimal used = spend.divide(budgetUsd, 6, RoundingMode.HALF_UP);
      tracker
          .check(Periods.month(clock), used)
          .ifPresent(level -> alertSink.accept(message(level, spend)));
    } catch (RuntimeException e) {
      log.warn("Budget alert check failed: {}", e.toString());
    }
  }

  private String message(int level, BigDecimal spend) {
    return "LLM budget alert: "
        + level
        + "% of the monthly budget used ($"
        + spend.setScale(2, RoundingMode.HALF_UP)
        + " of $"
        + budgetUsd.setScale(2, RoundingMode.HALF_UP)
        + "). "
        + consequence(level);
  }

  private static String consequence(int level) {
    return switch (level) {
      case 50 -> "Review spend in the llm_usage_daily view or GET /dev/llm/spend.";
      case 75 -> "Non-critical agents now run on their floor tier.";
      case 90 -> "Non-critical agents are stopped; only critical agents still run.";
      default -> "Budget exhausted: all agents are stopped until next month.";
    };
  }
}
