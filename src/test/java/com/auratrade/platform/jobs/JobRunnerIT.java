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
  static final class DailyJob implements ScheduledJob {
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
}
