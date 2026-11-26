package com.auratrade.marketdata;

import com.auratrade.platform.jobs.ScheduledJob;
import com.auratrade.platform.time.MarketZones;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Daily instrument master load (ADR 0017, backlog B-025). The provider file is a snapshot, so only
 * today is ever owed: a missed day is healed by loading today's file, not by replaying old ones.
 * Rebuilds the symbol resolver after a load.
 */
public class InstrumentMasterJob implements ScheduledJob {

  static final String NAME = "instrument-master";

  private final InstrumentLoader loader;
  private final SymbolResolverService resolver;
  private final Clock clock;

  InstrumentMasterJob(InstrumentLoader loader, SymbolResolverService resolver, Clock clock) {
    this.loader = loader;
    this.resolver = resolver;
    this.clock = clock;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<JobWindow> plan(Optional<LocalDate> lastCompleted) {
    LocalDate today = LocalDate.now(clock.withZone(MarketZones.IST));
    if (lastCompleted.isPresent() && !lastCompleted.get().isBefore(today)) {
      return Optional.empty();
    }
    return Optional.of(new JobWindow(today, today));
  }

  @Override
  public JobOutcome execute(JobWindow window) {
    InstrumentLoader.Result r = loader.load();
    resolver.refresh();
    String detail =
        String.format(
            "%s: %d rows, %d added, %d not in file, %d renamed, %d rejected%s",
            r.provider(),
            r.rows(),
            r.added(),
            r.notInFile(),
            r.renamed().size(),
            r.rejected().size(),
            r.rejected().isEmpty() ? "" : " " + r.rejected());
    // Rejected rows are expected occasionally (a bad ISIN in the provider file) and are reported
    // in the detail; they do not make the run PARTIAL. Too many fail the load inside the loader.
    return new JobOutcome(r.rows(), 0, detail);
  }
}
