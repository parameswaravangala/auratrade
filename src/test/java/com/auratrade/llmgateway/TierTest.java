package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TierTest {

  @Test
  void movesOneStepDownTowardTheFloor() {
    assertThat(Tier.HEAVY.oneDown(Tier.LIGHT)).isEqualTo(Tier.LIGHT);
    assertThat(Tier.HEAVY.oneDown(Tier.LOCAL)).isEqualTo(Tier.LIGHT);
    assertThat(Tier.LIGHT.oneDown(Tier.LOCAL)).isEqualTo(Tier.LOCAL);
  }

  @Test
  void neverGoesBelowTheFloor() {
    assertThat(Tier.LIGHT.oneDown(Tier.LIGHT)).isEqualTo(Tier.LIGHT);
    assertThat(Tier.HEAVY.oneDown(Tier.HEAVY)).isEqualTo(Tier.HEAVY);
    assertThat(Tier.LOCAL.oneDown(Tier.LIGHT)).isEqualTo(Tier.LOCAL);
  }
}
