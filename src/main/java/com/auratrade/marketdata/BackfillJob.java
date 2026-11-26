package com.auratrade.marketdata;

import com.auratrade.platform.jobs.ScheduledJob;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Drives open backfill requests in batches (ADR 0018). Unlike the daily jobs it does not owe dates:
 * it owes whatever items are still pending, so each trigger takes the next batch until the request
 * is finished. Runs under the same lease and history as every other job.
 */
public class BackfillJob implements ScheduledJob {

  static final String NAME = "backfill";

  private final BackfillService backfills;
  private final int batchSize;

  BackfillJob(BackfillService backfills, int batchSize) {
    this.backfills = backfills;
    this.batchSize = batchSize;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<JobWindow> plan(Optional<LocalDate> lastCompleted) {
    return backfills.openRequest().map(r -> new JobWindow(r.from(), r.to(), null));
  }

  @Override
  public JobOutcome execute(JobWindow window) {
    var batch = backfills.runBatch(batchSize).orElseThrow();
    return new JobOutcome(
        batch.done(),
        batch.failed(),
        String.format(
            "backfill #%d: %d done, %d failed, %d retrying; %d still pending",
            batch.requestId(), batch.done(), batch.failed(), batch.retrying(), batch.pending()));
  }
}
