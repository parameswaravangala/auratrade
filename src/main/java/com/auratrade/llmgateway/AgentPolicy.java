package com.auratrade.llmgateway;

import java.util.Objects;

/**
 * How an agent may be served.
 *
 * @param preferred tier used when budget is healthy
 * @param floor cheapest tier the agent may be degraded to
 * @param critical safety-critical agents are never downgraded; they fail closed instead
 * @param dailyTokenCap maximum tokens (input, output and cache) per day for this agent
 */
public record AgentPolicy(Tier preferred, Tier floor, boolean critical, long dailyTokenCap) {

  public AgentPolicy {
    Objects.requireNonNull(preferred, "preferred");
    Objects.requireNonNull(floor, "floor");
    if (floor.ordinal() < preferred.ordinal()) {
      throw new IllegalArgumentException("floor must not be a stronger tier than preferred");
    }
    if (critical && floor != preferred) {
      throw new IllegalArgumentException("critical agents must have floor equal to preferred");
    }
    if (dailyTokenCap <= 0) {
      throw new IllegalArgumentException("dailyTokenCap must be positive");
    }
  }
}
