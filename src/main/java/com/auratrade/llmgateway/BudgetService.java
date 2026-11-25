package com.auratrade.llmgateway;

import com.auratrade.platform.time.MarketZones;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/**
 * Answers one question: which tier may this agent use right now? Month and day boundaries follow
 * IST. Throws BudgetExceededException when the agent must not run.
 */
@Service
public class BudgetService {

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

  public Tier resolveTier(AgentType agent) {
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(MarketZones.IST));
    ZonedDateTime dayStart = now.truncatedTo(ChronoUnit.DAYS);
    ZonedDateTime monthStart = dayStart.withDayOfMonth(1);

    BigDecimal monthSpend = usage.spendSince(monthStart.toOffsetDateTime());
    long todayTokens = usage.tokensSince(agent, dayStart.toOffsetDateTime());
    return resolver.resolve(agent, properties.policy(agent), monthSpend, todayTokens);
  }
}
