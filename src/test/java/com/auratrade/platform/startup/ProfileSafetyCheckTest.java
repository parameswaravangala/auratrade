package com.auratrade.platform.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ProfileSafetyCheckTest {

  @Test
  void rejectsDevAndProdTogether() {
    assertThatThrownBy(() -> ProfileSafetyCheck.check(List.of("dev", "prod"), "s3cret"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'dev' and 'prod'");
  }

  @Test
  void rejectsProdWithDevPassword() {
    assertThatThrownBy(() -> ProfileSafetyCheck.check(List.of("prod"), "aura_dev"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DB_PASSWORD");
  }

  @Test
  void allowsSafeCombinations() {
    assertThatCode(() -> ProfileSafetyCheck.check(List.of("prod"), "s3cret"))
        .doesNotThrowAnyException();
    assertThatCode(() -> ProfileSafetyCheck.check(List.of("prod"), null))
        .doesNotThrowAnyException();
    assertThatCode(() -> ProfileSafetyCheck.check(List.of("dev"), "aura_dev"))
        .doesNotThrowAnyException();
    assertThatCode(() -> ProfileSafetyCheck.check(List.of(), "aura_dev"))
        .doesNotThrowAnyException();
  }

  @Test
  void failsContextStartupBeforeOtherBeans() {
    var env = new MockEnvironment().withProperty("spring.datasource.password", "aura_dev");
    env.setActiveProfiles("prod");
    try (var context = new GenericApplicationContext()) {
      context.setEnvironment(env);
      context.registerBean(ProfileSafetyCheck.class);
      assertThatThrownBy(context::refresh).hasStackTraceContaining("development database password");
    }
  }

  @Test
  void treatsUnresolvedPasswordPlaceholderAsUnknown() {
    var env = new MockEnvironment().withProperty("spring.datasource.password", "${DB_PASSWORD}");
    env.setActiveProfiles("prod");
    try (var context = new GenericApplicationContext()) {
      context.setEnvironment(env);
      context.registerBean(ProfileSafetyCheck.class);
      assertThatCode(context::refresh).doesNotThrowAnyException();
    }
  }
}
