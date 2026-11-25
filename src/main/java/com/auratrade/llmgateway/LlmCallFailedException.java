package com.auratrade.llmgateway;

/** The provider failed and no allowed fallback remained. Critical agents fail closed this way. */
public class LlmCallFailedException extends RuntimeException {

  public LlmCallFailedException(AgentType agent, Tier tier, Throwable cause) {
    super(agent + ": provider call failed on tier " + tier, cause);
  }
}
