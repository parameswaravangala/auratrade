package com.auratrade.platform.web;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link LocalRequestGuard} in front of every request, including actuator. On by default;
 * it may only be switched off ({@code auratrade.security.local-guard.enabled=false}) once real
 * authentication protects the app.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "auratrade.security.local-guard",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class LocalRequestGuardConfig {

  @Bean
  FilterRegistrationBean<LocalRequestGuard> localRequestGuard(
      @Value("${auratrade.security.local-guard.allowed-origins}") List<String> allowedOrigins) {
    var registration =
        new FilterRegistrationBean<>(new LocalRequestGuard(new LinkedHashSet<>(allowedOrigins)));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setName("localRequestGuard");
    return registration;
  }
}
