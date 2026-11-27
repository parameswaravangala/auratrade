package com.auratrade.platform.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.platform.jobs.JobRunRepository.Status;
import com.auratrade.platform.jobs.JobRunRepository.Trigger;
import com.auratrade.platform.jobs.JobRunner.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JobRunnerIT extends PostgresIntegrationTest {

  @Autowired JobRunRepository runs;
  @Autowired JdbcClient jdbc;

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T14:00:00Z"), ZoneOffset.UTC);

  /** Owes every day after the last completed one up to "today"; records what it was asked to do. */
  static class DailyJob implements ScheduledJob {
    final List<ScheduledJob.JobWindow> executed = new ArrayList<>();
    LocalDate today = LocalDate.parse("2026-11-26");
    RuntimeException failWith;
    int failedItems;

    @Override
    public String name() {
      return "test-daily";
    }

    @Override
    public Optional<JobWindow> plan(Optional<LocalDate> last) {
      LocalDate from = last.map(d -> d.plusDays(1)).orElse(today);
      return from.isAfter(today) ? Optional.empty() : Optional.of(new JobWindow(from, today));
    }

    @Override
    public JobOutcome execute(JobWindow window) {
      executed.add(window);
      if (failWith != null) {
        throw failWith;
      }
      return new JobOutcome(10, failedItems, "did " + window);
    }
  }

  private JobRunner runner() {
    return new JobRunner(runs, clock, Duration.ofHours(1));
  }

  @BeforeEach
  void clean() {
    jdbc.sql("truncate job_run, job_lock restart identity").update();
  }

  private Status lastStatus() {
    return runs.recent(1).get(0).status();
  }

  @Test
  void aRunIsRecordedAndNotRepeatedOnceDone() {
    var job = new DailyJob();

    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);
    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.NOTHING_DUE);

    assertThat(job.executed).hasSize(1);
    assertThat(lastStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(runs.recent(1).get(0).itemsOk()).isEqualTo(10);
  }

  @Test
  void missedDaysAreCaughtUpFromHistory() {
    var job = new DailyJob();
    job.today = LocalDate.parse("2026-11-23");
    runner().run(job, Trigger.SCHEDULE);

    job.today = LocalDate.parse("2026-11-26"); // the app was down for three days
    runner().run(job, Trigger.STARTUP);

    assertThat(job.executed.get(1))
        .isEqualTo(
            new ScheduledJob.JobWindow(
                LocalDate.parse("2026-11-24"), LocalDate.parse("2026-11-26")));
  }

  @Test
  void aFailedRunIsRetriedByTheNextTrigger() {
    var job = new DailyJob();
    job.failWith = new IllegalStateException("provider down");

    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.FAILED);
    assertThat(lastStatus()).isEqualTo(Status.FAILED);
    assertThat(runs.recent(1).get(0).detail()).isEqualTo("IllegalStateException: provider down");

    job.failWith = null;
    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);
    assertThat(job.executed).hasSize(2);
    assertThat(job.executed.get(1).from()).isEqualTo(LocalDate.parse("2026-11-26"));
  }

  @Test
  void failedItemsMakeARunPartialAndStillMoveOn() {
    var job = new DailyJob();
    job.failedItems = 2;

    runner().run(job, Trigger.SCHEDULE);

    assertThat(lastStatus()).isEqualTo(Status.PARTIAL);
    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.NOTHING_DUE);
  }

  @Test
  void aHeldLeaseBlocksASecondRunUntilItExpires() {
    var job = new DailyJob();
    assertThat(runs.tryLock(job.name(), "other-instance", clock.instant(), Duration.ofMinutes(30)))
        .isTrue();

    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.LOCKED);
    assertThat(job.executed).isEmpty();

    // The other holder crashed: once its lease has expired, the next trigger takes over.
    var later =
        new JobRunner(runs, Clock.offset(clock, Duration.ofMinutes(31)), Duration.ofHours(1));
    assertThat(later.run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);
  }

  @Test
  void aRunLeftRunningByAStoppedProcessIsClosedByTheNextTrigger() {
    var job = new DailyJob();
    LocalDate d = LocalDate.parse("2026-11-26");
    // A process started this run, then was killed: no outcome, and its lease has lapsed.
    long orphan = runs.start(job.name(), Trigger.SCHEDULE, d, d, clock.instant());

    assertThat(runner().run(job, Trigger.STARTUP)).isEqualTo(Result.RAN);

    var closed = runs.recent(10).stream().filter(r -> r.id() == orphan).findFirst().orElseThrow();
    assertThat(closed.status()).isEqualTo(Status.FAILED);
    assertThat(closed.detail()).startsWith("abandoned:");
    assertThat(closed.finishedAt()).isNotNull();
    assertThat(job.executed).hasSize(1); // the window was still owed and ran again
  }

  @Test
  void theHeartbeatKeepsTheLeaseWhileAJobOutlastsIt() {
    var time = new MovingClock(clock.instant());
    Duration lease = Duration.ofMillis(300);
    var job =
        new DailyJob() {
          boolean stolen;

          @Override
          public JobOutcome execute(JobWindow window) {
            // Run past the original lease in two steps, giving the heartbeat time to renew.
            for (int i = 0; i < 2; i++) {
              time.advance(Duration.ofMillis(200));
              sleep(400);
            }
            stolen = runs.tryLock(name(), "other-instance", time.instant(), lease);
            return super.execute(window);
          }
        };

    assertThat(new JobRunner(runs, time, lease).run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);
    assertThat(job.stolen).isFalse();
  }

  @Test
  void aReleasedLeaseIsNeverRenewedByALateHeartbeat() {
    var time = new MovingClock(clock.instant());
    var runner = new JobRunner(runs, time, Duration.ofMinutes(10));
    assertThat(runs.tryLock("test-daily", runner.owner(), time.instant(), Duration.ofMinutes(10)))
        .isTrue();
    var guard = runner.new LeaseGuard("test-daily");

    guard.release();
    // A heartbeat that read the clock just before the release now arrives: it must do nothing.
    time.advance(Duration.ofSeconds(-1));
    guard.beat();
    time.advance(Duration.ofSeconds(1));

    assertThat(runs.tryLock("test-daily", "other-instance", time.instant(), Duration.ofMinutes(1)))
        .isTrue();
    runner.close();
  }

  @Test
  void theSameJobCannotRunTwiceInOneProcessEvenWithoutItsLease() {
    var runner = runner();
    var nested = new java.util.concurrent.atomic.AtomicReference<Result>();
    var job =
        new DailyJob() {
          @Override
          public JobOutcome execute(JobWindow window) {
            // The lease is lost (say, heartbeats failed): another trigger here must still wait.
            jdbc.sql("update job_lock set locked_until = :t")
                .param("t", java.sql.Timestamp.from(clock.instant().minusSeconds(1)))
                .update();
            nested.set(runner.run(this, Trigger.MANUAL));
            return super.execute(window);
          }
        };

    assertThat(runner.run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);

    assertThat(nested.get()).isEqualTo(Result.LOCKED);
    assertThat(job.executed).hasSize(1);
    assertThat(lastStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  void aRunClosedAsAbandonedElsewhereStaysClosedWhenItFinishes() {
    var job =
        new DailyJob() {
          @Override
          public JobOutcome execute(JobWindow window) {
            // Another process took the lapsed lease and closed this live run as abandoned.
            runs.closeAbandoned(name(), clock.instant());
            return super.execute(window);
          }
        };

    assertThat(runner().run(job, Trigger.SCHEDULE)).isEqualTo(Result.RAN);

    var run = runs.recent(1).get(0);
    assertThat(run.status()).isEqualTo(Status.FAILED);
    assertThat(run.detail())
        .startsWith("abandoned:")
        .contains("Finished after being closed as abandoned: SUCCEEDED, 10 ok, 0 failed.");
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** A clock the test moves forward by hand, read by the heartbeat thread too. */
  static final class MovingClock extends Clock {
    private volatile Instant now;

    MovingClock(Instant start) {
      this.now = start;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }
}
