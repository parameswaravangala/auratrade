package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only view of LLM spend. Month and day follow IST. */
@Service
public class SpendService {

  private final UsageRepository usage;
  private final GatewayProperties properties;
  private final Clock clock;

  SpendService(UsageRepository usage, GatewayProperties properties, Clock clock) {
    this.usage = usage;
    this.properties = properties;
    this.clock = clock;
  }

  public SpendReport report() {
    List<SpendRow> month = usage.spendByAgentTierSince(Periods.monthStart(clock));
    List<SpendRow> today = usage.spendByAgentTierSince(Periods.dayStart(clock));
    BigDecimal spend =
        month.stream().map(SpendRow::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal budget = properties.monthlyBudgetUsd();
    BigDecimal percent =
        spend.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP);
    return new SpendReport(spend, budget, percent, month, today);
  }
}
