package com.auratrade.platform.jobs;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A job the {@link JobRunner} runs on a schedule, at startup or by hand (ADR 0017). The job decides
 * what is still owed from the last window that succeeded, so a missed or failed run heals on the
 * next trigger. Implementations must be idempotent: re-running a window does no harm.
 */
public interface ScheduledJob {

  /** Stable name stored in job_run and job_lock. */
  String name();

  /**
   * The window still owed, or empty when nothing is due (for example not a trading day yet).
   *
   * @param lastSucceeded end of the last window that SUCCEEDED or was PARTIAL, if any
   */
  Optional<JobWindow> plan(Optional<LocalDate> lastSucceeded);

  /** Does the work for the window. Throw to fail the run; return failures for a PARTIAL run. */
  JobOutcome execute(JobWindow window);

  /** Business dates covered by one run, inclusive. */
  record JobWindow(LocalDate from, LocalDate to, String note) {
    public JobWindow {
      if (to.isBefore(from)) {
        throw new IllegalArgumentException("window ends before it starts");
      }
    }

    public JobWindow(LocalDate from, LocalDate to) {
      this(from, to, null);
    }
  }

  /** What a run achieved. Any failed item makes the run PARTIAL. */
  record JobOutcome(int ok, int failed, String detail) {}
}
