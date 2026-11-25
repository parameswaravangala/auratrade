package com.auratrade.platform.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable Clock so time-dependent code (schedulers, cooldowns, expiry) is testable. */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
