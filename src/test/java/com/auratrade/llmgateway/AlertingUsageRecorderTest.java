package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertingUsageRecorderTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
  private final List<UsageEntry> stored = new ArrayList<>();
  private final List<String> alerts = new ArrayList<>();
  private BigDecimal monthSpend = BigDecimal.ZERO;

  private final UsageEntry entry =
      new UsageEntry(
          Instant.parse("2026-11-26T10:00:00Z"),
          AgentType.ADHOC_CHAT,
          Tier.HEAVY,
          "m",
          new TokenUsage(1, 1, 0, 0),
          BigDecimal.ONE,
          "run-1");

  private AlertingUsageRecorder recorder() {
    return new AlertingUsageRecorder(
        stored::add, () -> monthSpend, new BigDecimal("50"), clock, alerts::add);
  }

  @Test
  void alwaysStoresTheEntryAndAlertsOnlyOnNewLevels() {
    var recorder = recorder();

    monthSpend = new BigDecimal("10");
    recorder.record(entry);
    assertThat(alerts).isEmpty();

    monthSpend = new BigDecimal("26");
    recorder.record(entry);
    recorder.record(entry);

    assertThat(stored).hasSize(3);
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0)).contains("50%").contains("$26.00 of $50.00");
  }

  @Test
  void messagesDescribeTheConsequence() {
    var recorder = recorder();

    monthSpend = new BigDecimal("38");
    recorder.record(entry);
    monthSpend = new BigDecimal("46");
    recorder.record(entry);

    assertThat(alerts).hasSize(2);
    assertThat(alerts.get(0)).contains("75%").contains("floor tier");
    assertThat(alerts.get(1)).contains("90%").contains("only critical agents");
  }

  @Test
  void aFailingAlertCheckNeverBreaksTheRun() {
    var recorder =
        new AlertingUsageRecorder(
            stored::add,
            () -> {
              throw new IllegalStateException("db down");
            },
            new BigDecimal("50"),
            clock,
            alerts::add);

    assertThatCode(() -> recorder.record(entry)).doesNotThrowAnyException();
    assertThat(stored).hasSize(1);
    assertThat(alerts).isEmpty();
  }
}
