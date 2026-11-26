package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only door to LLMs. Resolves the tier from budget state, calls the provider, falls back one
 * tier on provider errors (never for critical agents, which retry once and then fail closed),
 * prices the run and records usage.
 */
@Service
public class LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);
  private static final int CRITICAL_ATTEMPTS = 2;

  private final TierResolver tiers;
  private final ModelInvoker invoker;
  private final UsageRecorder usage;
  private final GatewayProperties properties;
  private final Clock clock;

  LlmGateway(
      TierResolver tiers,
      ModelInvoker invoker,
      UsageRecorder usage,
      GatewayProperties properties,
      Clock clock) {
    this.tiers = tiers;
    this.invoker = invoker;
    this.usage = usage;
    this.properties = properties;
    this.clock = clock;
  }

  /** Same as the four-argument call with provider defaults. */
  public LlmResult call(AgentType agent, String system, String user) {
    return call(agent, system, user, CallOptions.DEFAULT);
  }

  /** Throws BudgetExceededException (do not run) or LlmCallFailedException (provider failed). */
  public LlmResult call(AgentType agent, String system, String user, CallOptions options) {
    AgentPolicy policy = properties.policy(agent);
    Tier tier = tiers.resolveTier(agent);
    String runId = UUID.randomUUID().toString();
    int attemptsOnTier = 0;

    while (true) {
      TierConfig config = properties.tier(tier);
      attemptsOnTier++;
      try {
        InvocationResult result = invoker.invoke(config.model(), system, user, options);
        BigDecimal cost = CostCalculator.cost(config.price(), result.usage());
        usage.record(
            new UsageEntry(
                clock.instant(), agent, tier, config.model(), result.usage(), cost, runId));
        return new LlmResult(result.text(), tier, config.model(), result.usage(), cost, runId);
      } catch (BudgetExceededException e) {
        throw e;
      } catch (RuntimeException e) {
        if (policy.critical()) {
          if (attemptsOnTier >= CRITICAL_ATTEMPTS) {
            throw new LlmCallFailedException(agent, tier, e);
          }
          log.warn("{} on {} failed, retrying same tier: {}", agent, tier, e.toString());
          continue;
        }
        Tier next = tier.oneDown(policy.floor());
        if (next == tier) {
          throw new LlmCallFailedException(agent, tier, e);
        }
        log.warn("{} on {} failed, falling back to {}: {}", agent, tier, next, e.toString());
        tier = next;
        attemptsOnTier = 0;
      }
    }
  }
}
