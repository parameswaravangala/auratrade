package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from auratrade.llm.* ; fails at startup if a tier or an agent policy is missing. */
@ConfigurationProperties(prefix = "auratrade.llm")
public record GatewayProperties(
    BigDecimal monthlyBudgetUsd, Map<Tier, TierConfig> tiers, Map<AgentType, AgentPolicy> agents) {

  public GatewayProperties {
    Objects.requireNonNull(monthlyBudgetUsd, "auratrade.llm.monthly-budget-usd");
    tiers = Map.copyOf(Objects.requireNonNull(tiers, "auratrade.llm.tiers"));
    agents = Map.copyOf(Objects.requireNonNull(agents, "auratrade.llm.agents"));
    for (Tier tier : Tier.values()) {
      if (!tiers.containsKey(tier)) {
        throw new IllegalStateException("Missing auratrade.llm.tiers." + tier);
      }
    }
    for (AgentType agent : AgentType.values()) {
      if (!agents.containsKey(agent)) {
        throw new IllegalStateException("Missing auratrade.llm.agents." + agent);
      }
    }
  }

  public AgentPolicy policy(AgentType agent) {
    return agents.get(agent);
  }

  public TierConfig tier(Tier tier) {
    return tiers.get(tier);
  }
}
