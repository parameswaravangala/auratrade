package com.auratrade.llmgateway;

import java.math.BigDecimal;

/**
 * Decides which tier an agent may use right now. Pure logic with no I/O, so every budget state is
 * unit-testable.
 */
final class BudgetResolver {

  private final BudgetLimits limits;

  BudgetResolver(BudgetLimits limits) {
    this.limits = limits;
  }

  Tier resolve(AgentType agent, AgentPolicy policy, BigDecimal monthSpendUsd, long todayTokens) {
    if (todayTokens >= policy.dailyTokenCap()) {
      throw new BudgetExceededException(agent, "daily token cap reached");
    }

    BigDecimal used = limits.usedFraction(monthSpendUsd);
    BigDecimal stopAt = policy.critical() ? limits.criticalStopAt() : limits.nonCriticalStopAt();

    if (used.compareTo(stopAt) >= 0) {
      // A free local floor means the agent can keep working without spending anything.
      if (!policy.critical() && policy.floor() == Tier.LOCAL) {
        return Tier.LOCAL;
      }
      throw new BudgetExceededException(agent, "monthly budget limit reached");
    }

    if (policy.critical()) {
      return policy.preferred(); // never downgraded
    }
    if (used.compareTo(limits.floorAt()) >= 0) {
      return policy.floor();
    }
    if (used.compareTo(limits.downgradeAt()) >= 0) {
      return policy.preferred().oneDown(policy.floor());
    }
    return policy.preferred();
  }
}
