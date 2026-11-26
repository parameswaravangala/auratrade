package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SpendServiceIT extends PostgresIntegrationTest {

  @Autowired SpendService spendService;
  @Autowired UsageRepository usage;
  @Autowired JdbcClient jdbc;
  @Autowired Clock clock;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate llm_usage").update();
  }

  private void spend(AgentType agent, Tier tier, String usd) {
    usage.save(
        clock.instant(),
        agent,
        tier,
        "test-model",
        new TokenUsage(100, 50, 0, 0),
        new BigDecimal(usd),
        "run");
  }

  @Test
  void reportsSpendPerAgentAndTierWithBudgetShare() {
    spend(AgentType.STOCK_ANALYSIS, Tier.HEAVY, "10.00");
    spend(AgentType.STOCK_ANALYSIS, Tier.HEAVY, "5.00");
    spend(AgentType.NEWS_TRIAGE, Tier.LIGHT, "0.50");

    SpendReport report = spendService.report();

    assertThat(report.monthSpendUsd()).isEqualByComparingTo("15.50");
    assertThat(report.budgetUsd()).isEqualByComparingTo("50");
    assertThat(report.usedPercent()).isEqualByComparingTo("31.0");
    assertThat(report.month()).hasSize(2);
    assertThat(report.month().get(0).agent()).isEqualTo("STOCK_ANALYSIS");
    assertThat(report.month().get(0).runs()).isEqualTo(2);
    assertThat(report.month().get(0).tokens()).isEqualTo(300);
    assertThat(report.today()).hasSize(2);
  }

  @Test
  void viewsAggregateTheSameData() {
    spend(AgentType.STOCK_ANALYSIS, Tier.HEAVY, "10.00");
    spend(AgentType.STOCK_ANALYSIS, Tier.HEAVY, "5.00");

    BigDecimal daily =
        jdbc.sql("select sum(cost_usd) from llm_usage_daily").query(BigDecimal.class).single();
    BigDecimal monthly =
        jdbc.sql("select sum(cost_usd) from llm_usage_monthly").query(BigDecimal.class).single();

    assertThat(daily).isEqualByComparingTo("15.00");
    assertThat(monthly).isEqualByComparingTo("15.00");
  }
}
