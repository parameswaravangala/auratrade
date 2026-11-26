package com.auratrade.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.platform.ratelimit.WindowRateLimiter.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowRateLimiterTest {

  /** A clock that only moves when the limiter "sleeps". */
  static final class FakeTime extends Clock {
    Instant now = Instant.parse("2026-11-26T04:00:00Z");
    final List<Duration> sleeps = new ArrayList<>();

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void sleep(Duration d) {
      sleeps.add(d);
      now = now.plus(d);
    }
  }

  private final FakeTime time = new FakeTime();

  private WindowRateLimiter limiter(Limit... limits) {
    return new WindowRateLimiter(List.of(limits), time, time::sleep);
  }

  @Test
  void callsWithinTheLimitDoNotWait() {
    var limiter = limiter(new Limit(3, Duration.ofSeconds(1)));

    for (int i = 0; i < 3; i++) {
      limiter.acquire();
    }

    assertThat(time.sleeps).isEmpty();
  }

  @Test
  void theNextCallWaitsUntilTheOldestLeavesTheWindow() {
    var limiter = limiter(new Limit(2, Duration.ofSeconds(1)));
    limiter.acquire(); // t=0
    time.now = time.now.plusMillis(300);
    limiter.acquire(); // t=0.3

    limiter.acquire(); // must wait until t=1.0

    assertThat(time.sleeps).containsExactly(Duration.ofMillis(700));
  }

  @Test
  void theStrictestWindowWins() {
    var limiter =
        limiter(new Limit(100, Duration.ofSeconds(1)), new Limit(3, Duration.ofMinutes(1)));
    for (int i = 0; i < 3; i++) {
      limiter.acquire();
    }

    limiter.acquire();

    assertThat(time.sleeps).containsExactly(Duration.ofMinutes(1));
  }

  @Test
  void aLongRunStaysUnderEveryLimit() {
    var limiter =
        limiter(new Limit(5, Duration.ofSeconds(1)), new Limit(12, Duration.ofSeconds(10)));
    List<Instant> grants = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      limiter.acquire();
      grants.add(time.instant());
    }

    for (Instant t : grants) {
      long inOneSecond =
          grants.stream().filter(g -> !g.isBefore(t) && g.isBefore(t.plusSeconds(1))).count();
      long inTenSeconds =
          grants.stream().filter(g -> !g.isBefore(t) && g.isBefore(t.plusSeconds(10))).count();
      assertThat(inOneSecond).isLessThanOrEqualTo(5);
      assertThat(inTenSeconds).isLessThanOrEqualTo(12);
    }
  }
}
