package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Answers one question: which tier may this agent use right now? Month and day boundaries follow
 * IST. Throws BudgetExceededException when the agent must not run.
 */
@Service
public class BudgetService implements TierResolver {

  private final BudgetResolver resolver;
  private final UsageRepository usage;
  private final GatewayProperties properties;
  private final Clock clock;

  BudgetService(
      BudgetResolver resolver, UsageRepository usage, GatewayProperties properties, Clock clock) {
    this.resolver = resolver;
    this.usage = usage;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Tier resolveTier(AgentType agent) {
    BigDecimal monthSpend = usage.spendSince(Periods.monthStart(clock));
    long todayTokens = usage.tokensSince(agent, Periods.dayStart(clock));
    return resolver.resolve(agent, properties.policy(agent), monthSpend, todayTokens);
  }
}
