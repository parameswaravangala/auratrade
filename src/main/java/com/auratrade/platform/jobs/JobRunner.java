package com.auratrade.platform.jobs;

import com.auratrade.platform.jobs.JobRunRepository.JobRun;
import com.auratrade.platform.jobs.JobRunRepository.Status;
import com.auratrade.platform.jobs.JobRunRepository.Trigger;
import com.auratrade.platform.jobs.ScheduledJob.JobOutcome;
import com.auratrade.platform.jobs.ScheduledJob.JobWindow;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a {@link ScheduledJob} once (ADR 0017): take the job's lease, ask the job what is still owed
 * since the last completed window, run it, and record the result in job_run. Missed or failed runs
 * heal on the next trigger because the plan is computed from history, not from trigger times.
 *
 * <p>While a job runs, a heartbeat renews its lease every third of the lease, so the lease can be
 * short: a killed process stops renewing, and the first trigger after expiry takes over and closes
 * the run that process left RUNNING as FAILED (abandoned).
 *
 * <p>FAILED and PARTIAL runs, and jobs that cannot plan (for example an uncovered calendar year),
 * are reported on the JobAlerts logger, which a real channel can replace later.
 */
public class JobRunner implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
  private static final Logger alerts =
      LoggerFactory.getLogger("com.auratrade.platform.jobs.JobAlerts");
  private static final int MAX_DETAIL = 2000;

  /** What one trigger did. */
  public enum Result {
    RAN,
    NOTHING_DUE,
    LOCKED,
    FAILED
  }

  private final JobRunRepository runs;
  private final Clock clock;
  private final Duration lease;
  private final String owner = "auratrade-" + UUID.randomUUID();
  private final java.util.Set<String> runningHere =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "job-lease-heartbeat");
        t.setDaemon(true);
        return t;
      });

  public JobRunner(JobRunRepository runs, Clock clock, Duration lease) {
    this.runs = runs;
    this.clock = clock;
    this.lease = lease;
  }

  public Result run(ScheduledJob job, Trigger trigger) {
    // In this process a job never runs twice at once, even after its lease was lost; so the
    // abandoned-run cleanup below can never close a run that is still alive here.
    if (!runningHere.add(job.name())) {
      log.info("{} is already running in this process; {} trigger skipped", job.name(), trigger);
      return Result.LOCKED;
    }
    try {
      return runLocked(job, trigger);
    } finally {
      runningHere.remove(job.name());
    }
  }

  private Result runLocked(ScheduledJob job, Trigger trigger) {
    if (!runs.tryLock(job.name(), owner, clock.instant(), lease)) {
      log.info("{} is already running elsewhere; {} trigger skipped", job.name(), trigger);
      return Result.LOCKED;
    }
    LeaseGuard guard = new LeaseGuard(job.name());
    long period = Math.max(1, lease.toMillis() / 3);
    guard.renewal =
        heartbeat.scheduleAtFixedRate(guard::beat, period, period, TimeUnit.MILLISECONDS);
    try {
      int abandoned = runs.closeAbandoned(job.name(), clock.instant());
      if (abandoned > 0) {
        alerts.warn(
            "{}: closed {} run(s) left RUNNING by a stopped process as FAILED",
            job.name(),
            abandoned);
      }
      Optional<JobWindow> window;
      try {
        window = job.plan(runs.lastCompletedWindowEnd(job.name()));
      } catch (RuntimeException e) {
        alerts.warn("{} could not work out what is due: {}", job.name(), e.getMessage());
        return Result.FAILED;
      }
      if (window.isEmpty()) {
        log.debug("{}: nothing due", job.name());
        return Result.NOTHING_DUE;
      }
      return execute(job, trigger, window.get());
    } finally {
      guard.release();
    }
  }

  /**
   * One run's lease. Renewal and release share a monitor and a released flag, so a renewal already
   * in flight finishes before the release, and none can land after it: a released lease is never
   * extended again.
   */
  final class LeaseGuard {
    private final String job;
    private boolean released;
    ScheduledFuture<?> renewal;

    LeaseGuard(String job) {
      this.job = job;
    }

    synchronized void beat() {
      if (released) {
        return;
      }
      try {
        if (!runs.renew(job, owner, clock.instant(), lease)) {
          alerts.warn("{}: lease lost while running; another process may start it", job);
        }
      } catch (RuntimeException e) {
        // A failed heartbeat must not end the schedule: the next one tries again.
        log.warn("{}: could not renew the lease: {}", job, e.getMessage());
      }
    }

    synchronized void release() {
      released = true;
      if (renewal != null) {
        renewal.cancel(false);
      }
      runs.unlock(job, owner, clock.instant());
    }
  }

  /** This process's lease owner id (for tests). */
  String owner() {
    return owner;
  }

  /** Stops the heartbeat thread; Spring calls it when the context closes. */
  @Override
  public void close() {
    heartbeat.shutdownNow();
  }

  private Result execute(ScheduledJob job, Trigger trigger, JobWindow w) {
    long id = runs.start(job.name(), trigger, w.from(), w.to(), clock.instant());
    log.info("{} started ({}): {} to {}{}", job.name(), trigger, w.from(), w.to(), note(w));
    try {
      JobOutcome outcome = job.execute(w);
      Status status = outcome.failed() > 0 ? Status.PARTIAL : Status.SUCCEEDED;
      String detail = join(w.note(), outcome.detail());
      finish(job, id, status, outcome.ok(), outcome.failed(), clip(detail));
      if (status == Status.PARTIAL) {
        alerts.warn(
            "{} {} to {} PARTIAL: {} ok, {} failed. {}",
            job.name(),
            w.from(),
            w.to(),
            outcome.ok(),
            outcome.failed(),
            clip(detail));
      } else {
        log.info("{} succeeded: {} items", job.name(), outcome.ok());
      }
      if (w.note() != null) {
        alerts.warn("{}: {}", job.name(), w.note());
      }
      return Result.RAN;
    } catch (RuntimeException e) {
      finish(job, id, Status.FAILED, 0, 0, clip(describe(e)));
      alerts.warn("{} {} to {} FAILED: {}", job.name(), w.from(), w.to(), e.getMessage());
      return Result.FAILED;
    }
  }

  /**
   * Records the outcome, unless another process closed the run as abandoned meanwhile (its lease
   * had lapsed): then the closed status stands and the outcome is appended to the detail, with an
   * alert, so history never shows a falsely closed run as finished normally.
   */
  private void finish(ScheduledJob job, long id, Status status, int ok, int failed, String detail) {
    if (!runs.finish(id, status, ok, failed, detail, clock.instant())) {
      runs.appendDetail(
          id,
          " Finished after being closed as abandoned: "
              + status
              + ", "
              + ok
              + " ok, "
              + failed
              + " failed.");
      alerts.warn(
          "{}: run {} finished ({}) after another process had closed it as abandoned; check for a"
              + " second, overlapping run",
          job.name(),
          id,
          status);
    }
  }

  public java.util.List<JobRun> recent(int limit) {
    return runs.recent(limit);
  }

  /** "IllegalStateException: message": the package name means nothing to the person reading it. */
  static String describe(Throwable e) {
    String type = e.getClass().getSimpleName();
    return e.getMessage() == null ? type : type + ": " + e.getMessage();
  }

  private static String note(JobWindow w) {
    return w.note() == null ? "" : " (" + w.note() + ")";
  }

  private static String join(String a, String b) {
    if (a == null) {
      return b;
    }
    return b == null ? a : a + " " + b;
  }

  private static String clip(String s) {
    return s == null || s.length() <= MAX_DETAIL ? s : s.substring(0, MAX_DETAIL) + "...";
  }
}
