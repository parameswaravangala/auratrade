package com.auratrade.platform.startup;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with an unsafe profile mix. Runs as a bean factory post-processor, so it fails
 * before any other bean (data source, Flyway, schedulers) is created:
 *
 * <ul>
 *   <li>{@code dev} and {@code prod} together: dev endpoints would be exposed with prod data.
 *   <li>{@code prod} with the development database password {@code aura_dev}.
 * </ul>
 */
@Component
class ProfileSafetyCheck implements BeanFactoryPostProcessor, EnvironmentAware {

  static final String DEV_DB_PASSWORD = "aura_dev";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    check(Set.copyOf(Arrays.asList(environment.getActiveProfiles())), dbPassword(environment));
  }

  /** Pure check, unit tested. Throws {@link IllegalStateException} on an unsafe configuration. */
  static void check(Collection<String> activeProfiles, String dbPassword) {
    boolean dev = activeProfiles.contains("dev");
    boolean prod = activeProfiles.contains("prod");
    if (dev && prod) {
      throw new IllegalStateException(
          "Profiles 'dev' and 'prod' must not be active together: dev endpoints would run against"
              + " production. Activate exactly one.");
    }
    if (prod && DEV_DB_PASSWORD.equals(dbPassword)) {
      throw new IllegalStateException(
          "Profile 'prod' is using the development database password. Set DB_PASSWORD to a real"
              + " secret.");
    }
  }

  /** The configured password, or null if it cannot be resolved (the data source fails later). */
  private static String dbPassword(Environment environment) {
    try {
      return environment.getProperty("spring.datasource.password");
    } catch (IllegalArgumentException unresolvedPlaceholder) {
      return null;
    }
  }
}
