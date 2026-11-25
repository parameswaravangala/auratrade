package com.auratrade.llmgateway;

/** Thrown when an agent must not run: queue the work for later or skip it. Never bypass it. */
public class BudgetExceededException extends RuntimeException {

  private final AgentType agent;

  public BudgetExceededException(AgentType agent, String reason) {
    super(agent + ": " + reason);
    this.agent = agent;
  }

  public AgentType agent() {
    return agent;
  }
}
