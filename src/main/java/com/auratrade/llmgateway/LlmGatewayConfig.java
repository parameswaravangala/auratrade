package com.auratrade.llmgateway;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class LlmGatewayConfig {

  private static final Logger log =
      LoggerFactory.getLogger("com.auratrade.llmgateway.BudgetAlerts");

  @Bean
  BudgetResolver budgetResolver(GatewayProperties properties) {
    return new BudgetResolver(BudgetLimits.defaults(properties.monthlyBudgetUsd()));
  }

  /** The recorder the gateway uses: persists usage, then raises budget alerts. */
  @Bean
  @Primary
  UsageRecorder alertingUsageRecorder(
      UsageRepository repository, GatewayProperties properties, Clock clock) {
    return new AlertingUsageRecorder(
        repository,
        () -> repository.spendSince(Periods.monthStart(clock)),
        properties.monthlyBudgetUsd(),
        clock,
        message -> log.warn(message));
  }
}
