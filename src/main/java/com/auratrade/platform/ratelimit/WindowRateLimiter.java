package com.auratrade.platform.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Client-side rate limiter over several sliding windows at once, for example 40 per second, 400 per
 * minute and 1,600 per 30 minutes. {@link #acquire()} blocks until a call is allowed under every
 * window. Time comes from the injected Clock and waiting goes through a {@link Sleeper}, so tests
 * run without real sleeps.
 */
public final class WindowRateLimiter {

  /** At most {@code permits} calls in any {@code window}. */
  public record Limit(int permits, Duration window) {
    public Limit {
      if (permits < 1 || window.isNegative() || window.isZero()) {
        throw new IllegalArgumentException("limit needs at least 1 permit and a positive window");
      }
    }
  }

  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration d) throws InterruptedException;
  }

  private final List<Limit> limits;
  private final Clock clock;
  private final Sleeper sleeper;
  private final Duration longest;
  private final Deque<Instant> granted = new ArrayDeque<>();

  public WindowRateLimiter(List<Limit> limits, Clock clock, Sleeper sleeper) {
    if (limits.isEmpty()) {
      throw new IllegalArgumentException("at least one limit");
    }
    this.limits = List.copyOf(limits);
    this.clock = clock;
    this.sleeper = sleeper;
    this.longest = limits.stream().map(Limit::window).max(Duration::compareTo).orElseThrow();
  }

  /** Real time: system clock and Thread.sleep. */
  public static WindowRateLimiter realTime(List<Limit> limits, Clock clock) {
    return new WindowRateLimiter(limits, clock, d -> Thread.sleep(d.toMillis()));
  }

  public synchronized void acquire() {
    while (true) {
      Instant now = clock.instant();
      while (!granted.isEmpty() && !granted.peekFirst().isAfter(now.minus(longest))) {
        granted.pollFirst();
      }
      Duration wait = Duration.ZERO;
      for (Limit limit : limits) {
        Instant windowStart = now.minus(limit.window());
        int inWindow = 0;
        for (var it = granted.descendingIterator(); it.hasNext(); ) {
          if (!it.next().isAfter(windowStart)) {
            break;
          }
          inWindow++;
        }
        if (inWindow >= limit.permits()) {
          // Find the grant that must leave the window before one more call fits.
          Instant blocking = nthNewest(limit.permits());
          Duration w = Duration.between(now, blocking.plus(limit.window()));
          if (w.compareTo(wait) > 0) {
            wait = w;
          }
        }
      }
      if (wait.isZero() || wait.isNegative()) {
        granted.addLast(now);
        return;
      }
      try {
        sleeper.sleep(wait);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while waiting for a rate-limit permit", e);
      }
    }
  }

  /** The n-th most recent grant (n = 1 is the newest). */
  private Instant nthNewest(int n) {
    int i = 0;
    for (var it = granted.descendingIterator(); it.hasNext(); ) {
      Instant t = it.next();
      if (++i == n) {
        return t;
      }
    }
    throw new IllegalStateException("fewer than " + n + " grants");
  }
}
