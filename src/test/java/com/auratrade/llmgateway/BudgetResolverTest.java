package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetResolverTest {

  // Budget of $50: 60% = $30, 75% = $37.50, 85% = $42.50, 100% = $50
  private final BudgetResolver resolver =
      new BudgetResolver(BudgetLimits.defaults(new BigDecimal("50")));

  private final AgentPolicy research = new AgentPolicy(Tier.HEAVY, Tier.LIGHT, false, 600_000);
  private final AgentPolicy risk = new AgentPolicy(Tier.HEAVY, Tier.HEAVY, true, 200_000);
  private final AgentPolicy triage = new AgentPolicy(Tier.LIGHT, Tier.LIGHT, false, 1_000_000);
  private final AgentPolicy localFloor = new AgentPolicy(Tier.LIGHT, Tier.LOCAL, false, 100_000);

  private Tier resolve(AgentPolicy policy, String spend, long todayTokens) {
    return resolver.resolve(AgentType.STOCK_ANALYSIS, policy, new BigDecimal(spend), todayTokens);
  }

  @Test
  void usesPreferredTierWhileBudgetIsHealthy() {
    assertThat(resolve(research, "0", 0)).isEqualTo(Tier.HEAVY);
    assertThat(resolve(research, "29.99", 0)).isEqualTo(Tier.HEAVY);
  }

  @Test
  void dropsOneTierAtSixtyPercent() {
    assertThat(resolve(research, "30", 0)).isEqualTo(Tier.LIGHT);
    assertThat(resolve(localFloor, "30", 0)).isEqualTo(Tier.LOCAL);
  }

  @Test
  void usesTheFloorAtSeventyFivePercent() {
    assertThat(resolve(research, "37.50", 0)).isEqualTo(Tier.LIGHT);
    assertThat(resolve(localFloor, "37.50", 0)).isEqualTo(Tier.LOCAL);
  }

  @Test
  void stopsNonCriticalAgentsAtEightyFivePercent() {
    assertThat(resolve(research, "42.49", 0)).isEqualTo(Tier.LIGHT);
    assertThatThrownBy(() -> resolve(research, "42.50", 0))
        .isInstanceOf(BudgetExceededException.class);
    assertThatThrownBy(() -> resolve(triage, "45", 0)).isInstanceOf(BudgetExceededException.class);
  }

  @Test
  void agentWithFreeLocalFloorKeepsWorkingAfterTheStop() {
    assertThat(resolve(localFloor, "49", 0)).isEqualTo(Tier.LOCAL);
    assertThat(resolve(localFloor, "80", 0)).isEqualTo(Tier.LOCAL);
  }

  @Test
  void criticalAgentIsNeverDowngradedAndHasTheReservedSlice() {
    assertThat(resolve(risk, "0", 0)).isEqualTo(Tier.HEAVY);
    assertThat(resolve(risk, "37.50", 0)).isEqualTo(Tier.HEAVY);
    assertThat(resolve(risk, "49.99", 0)).isEqualTo(Tier.HEAVY);
  }

  @Test
  void criticalAgentFailsClosedAtOneHundredPercent() {
    assertThatThrownBy(() -> resolve(risk, "50", 0)).isInstanceOf(BudgetExceededException.class);
    assertThatThrownBy(() -> resolve(risk, "60", 0)).isInstanceOf(BudgetExceededException.class);
  }

  @Test
  void dailyTokenCapStopsEveryAgentIncludingCriticalAndLocal() {
    assertThat(resolve(research, "0", 599_999)).isEqualTo(Tier.HEAVY);
    assertThatThrownBy(() -> resolve(research, "0", 600_000))
        .isInstanceOf(BudgetExceededException.class);
    assertThatThrownBy(() -> resolve(risk, "0", 200_000))
        .isInstanceOf(BudgetExceededException.class);
    assertThatThrownBy(() -> resolve(localFloor, "80", 100_000))
        .isInstanceOf(BudgetExceededException.class);
  }

  @Test
  void invalidPoliciesAreRejected() {
    assertThatThrownBy(() -> new AgentPolicy(Tier.LIGHT, Tier.HEAVY, false, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AgentPolicy(Tier.HEAVY, Tier.LIGHT, true, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AgentPolicy(Tier.HEAVY, Tier.HEAVY, false, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
