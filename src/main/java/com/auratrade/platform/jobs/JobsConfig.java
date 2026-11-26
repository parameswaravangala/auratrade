package com.auratrade.platform.jobs;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Job history and runner (ADR 0017). The lease must outlast the longest run; a crashed holder's
 * lease simply expires, after which the next trigger takes over.
 */
@Configuration(proxyBeanMethods = false)
class JobsConfig {

  @Bean
  JobRunRepository jobRunRepository(JdbcClient jdbc) {
    return new JobRunRepository(jdbc);
  }

  @Bean
  JobRunner jobRunner(
      JobRunRepository runs, Clock clock, @Value("${auratrade.jobs.lease:PT3H}") Duration lease) {
    return new JobRunner(runs, clock, lease);
  }
}
