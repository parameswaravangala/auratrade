package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {

  private static final ModelPrice PRICE =
      new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00"), null, null);

  private final List<UsageEntry> recorded = new ArrayList<>();
  private final List<String> modelsCalled = new ArrayList<>();

  private GatewayProperties properties() {
    Map<Tier, TierConfig> tiers = new EnumMap<>(Tier.class);
    tiers.put(Tier.HEAVY, new TierConfig("heavy-model", PRICE));
    tiers.put(Tier.LIGHT, new TierConfig("light-model", PRICE));
    tiers.put(Tier.LOCAL, new TierConfig("local-model", PRICE));

    Map<AgentType, AgentPolicy> agents = new EnumMap<>(AgentType.class);
    for (AgentType agent : AgentType.values()) {
      agents.put(agent, new AgentPolicy(Tier.HEAVY, Tier.LIGHT, false, 1_000_000));
    }
    agents.put(AgentType.RISK_REVIEW, new AgentPolicy(Tier.HEAVY, Tier.HEAVY, true, 1_000_000));
    return new GatewayProperties(new BigDecimal("50"), tiers, agents);
  }

  private LlmGateway gateway(TierResolver resolver, ModelInvoker invoker) {
    Clock clock = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
    return new LlmGateway(resolver, invoker, recorded::add, properties(), clock);
  }

  private ModelInvoker working() {
    return (model, system, user) -> {
      modelsCalled.add(model);
      return new InvocationResult("answer", new TokenUsage(1_000, 500, 0, 0));
    };
  }

  @Test
  void callsThePreferredTierPricesTheRunAndRecordsUsage() {
    var gateway = gateway(agent -> Tier.HEAVY, working());

    LlmResult result = gateway.call(AgentType.STOCK_ANALYSIS, "sys", "user");

    assertThat(result.text()).isEqualTo("answer");
    assertThat(result.tier()).isEqualTo(Tier.HEAVY);
    assertThat(result.model()).isEqualTo("heavy-model");
    // 1000 in at 2.00/M + 500 out at 10.00/M = 0.002 + 0.005
    assertThat(result.costUsd()).isEqualByComparingTo("0.007000");
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).agent()).isEqualTo(AgentType.STOCK_ANALYSIS);
    assertThat(recorded.get(0).runId()).isEqualTo(result.runId());
  }

  @Test
  void usesTheTierTheBudgetServiceSelected() {
    var gateway = gateway(agent -> Tier.LIGHT, working());

    LlmResult result = gateway.call(AgentType.STOCK_ANALYSIS, "sys", "user");

    assertThat(result.tier()).isEqualTo(Tier.LIGHT);
    assertThat(modelsCalled).containsExactly("light-model");
  }

  @Test
  void nonCriticalAgentFallsBackOneTierWhenTheProviderFails() {
    ModelInvoker flaky =
        (model, system, user) -> {
          modelsCalled.add(model);
          if (model.equals("heavy-model")) {
            throw new IllegalStateException("429");
          }
          return new InvocationResult("ok", new TokenUsage(10, 10, 0, 0));
        };
    var gateway = gateway(agent -> Tier.HEAVY, flaky);

    LlmResult result = gateway.call(AgentType.STOCK_ANALYSIS, "sys", "user");

    assertThat(result.tier()).isEqualTo(Tier.LIGHT);
    assertThat(modelsCalled).containsExactly("heavy-model", "light-model");
    assertThat(recorded).hasSize(1);
    assertThat(recorded.get(0).tier()).isEqualTo(Tier.LIGHT);
  }

  @Test
  void nonCriticalAgentFailsWhenEvenTheFloorFails() {
    ModelInvoker down =
        (model, system, user) -> {
          modelsCalled.add(model);
          throw new IllegalStateException("down");
        };
    var gateway = gateway(agent -> Tier.HEAVY, down);

    assertThatThrownBy(() -> gateway.call(AgentType.STOCK_ANALYSIS, "sys", "user"))
        .isInstanceOf(LlmCallFailedException.class);
    assertThat(modelsCalled).containsExactly("heavy-model", "light-model");
    assertThat(recorded).isEmpty();
  }

  @Test
  void criticalAgentRetriesTheSameTierOnceThenFailsClosed() {
    ModelInvoker down =
        (model, system, user) -> {
          modelsCalled.add(model);
          throw new IllegalStateException("down");
        };
    var gateway = gateway(agent -> Tier.HEAVY, down);

    assertThatThrownBy(() -> gateway.call(AgentType.RISK_REVIEW, "sys", "user"))
        .isInstanceOf(LlmCallFailedException.class);
    assertThat(modelsCalled).containsExactly("heavy-model", "heavy-model");
    assertThat(recorded).isEmpty();
  }

  @Test
  void criticalAgentRecoversOnTheRetryWithoutDowngrading() {
    int[] calls = {0};
    ModelInvoker flaky =
        (model, system, user) -> {
          modelsCalled.add(model);
          if (calls[0]++ == 0) {
            throw new IllegalStateException("blip");
          }
          return new InvocationResult("ok", new TokenUsage(10, 10, 0, 0));
        };
    var gateway = gateway(agent -> Tier.HEAVY, flaky);

    LlmResult result = gateway.call(AgentType.RISK_REVIEW, "sys", "user");

    assertThat(result.tier()).isEqualTo(Tier.HEAVY);
    assertThat(modelsCalled).containsExactly("heavy-model", "heavy-model");
  }

  @Test
  void budgetExceededStopsBeforeAnyProviderCall() {
    TierResolver stopped =
        agent -> {
          throw new BudgetExceededException(agent, "monthly budget limit reached");
        };
    var gateway = gateway(stopped, working());

    assertThatThrownBy(() -> gateway.call(AgentType.STOCK_ANALYSIS, "sys", "user"))
        .isInstanceOf(BudgetExceededException.class);
    assertThat(modelsCalled).isEmpty();
    assertThat(recorded).isEmpty();
  }
}
