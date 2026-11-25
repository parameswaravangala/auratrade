package com.auratrade.llmgateway;

/** Which tier may this agent use right now? Implemented by BudgetService; faked in tests. */
interface TierResolver {
  Tier resolveTier(AgentType agent);
}
