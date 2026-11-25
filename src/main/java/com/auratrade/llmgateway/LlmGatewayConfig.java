package com.auratrade.llmgateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class LlmGatewayConfig {

  @Bean
  BudgetResolver budgetResolver(GatewayProperties properties) {
    return new BudgetResolver(BudgetLimits.defaults(properties.monthlyBudgetUsd()));
  }
}
