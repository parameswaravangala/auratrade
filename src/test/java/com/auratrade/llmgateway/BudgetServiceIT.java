package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BudgetServiceIT extends PostgresIntegrationTest {

  @Autowired BudgetService budget;
  @Autowired UsageRepository usage;
  @Autowired JdbcClient jdbc;
  @Autowired Clock clock;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate llm_usage").update();
  }

  private void spend(String usd) {
    usage.save(
        clock.instant(),
        AgentType.ADHOC_CHAT,
        Tier.HEAVY,
        "test-model",
        new TokenUsage(1, 1, 0, 0),
        new BigDecimal(usd),
        "run-" + usd);
  }

  @Test
  void healthyBudgetServesPreferredTiers() {
    assertThat(budget.resolveTier(AgentType.STOCK_ANALYSIS)).isEqualTo(Tier.HEAVY);
    assertThat(budget.resolveTier(AgentType.RISK_REVIEW)).isEqualTo(Tier.HEAVY);
  }

  @Test
  void downgradesResearchButNotRiskAsSpendGrows() {
    spend("31"); // 62% of the default $50 budget

    assertThat(budget.resolveTier(AgentType.STOCK_ANALYSIS)).isEqualTo(Tier.LIGHT);
    assertThat(budget.resolveTier(AgentType.RISK_REVIEW)).isEqualTo(Tier.HEAVY);
  }

  @Test
  void stopsResearchAtEightyFivePercentButRiskKeepsItsReservedSlice() {
    spend("43"); // 86%

    assertThatThrownBy(() -> budget.resolveTier(AgentType.STOCK_ANALYSIS))
        .isInstanceOf(BudgetExceededException.class);
    assertThat(budget.resolveTier(AgentType.RISK_REVIEW)).isEqualTo(Tier.HEAVY);
  }
}
