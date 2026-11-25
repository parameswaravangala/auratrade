package com.auratrade.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MarketZonesTest {

  @Test
  void istIsFiveHoursThirtyMinutesAheadOfUtc() {
    ZoneOffset offset = MarketZones.IST.getRules().getOffset(Instant.parse("2026-11-25T00:00:00Z"));

    assertThat(offset.getTotalSeconds()).isEqualTo(5 * 3600 + 30 * 60);
  }
}
