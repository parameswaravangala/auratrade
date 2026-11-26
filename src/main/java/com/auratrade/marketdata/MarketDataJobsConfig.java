package com.auratrade.marketdata;

import com.auratrade.platform.jobs.JobRunRepository.Trigger;
import com.auratrade.platform.jobs.JobRunner;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Market data jobs (ADR 0017). The job beans always exist so they can be run by hand; schedules and
 * startup catch-up exist only with auratrade.jobs.enabled=true. Times are IST.
 */
@Configuration(proxyBeanMethods = false)
class MarketDataJobsConfig {

  @Bean
  InstrumentMasterJob instrumentMasterJob(
      InstrumentLoader loader, SymbolResolverService resolver, Clock clock) {
    return new InstrumentMasterJob(loader, resolver, clock);
  }

  /**
   * Defaults: today's candles are expected after 19:00 IST (not verified with Upstox; the run fails
   * and retries if most are missing), catch-up of at most 30 trading days, series EQ, all shares.
   */
  @Bean
  EodCandleJob eodCandleJob(
      CandleIngestionService ingestion,
      TradingCalendar calendar,
      Clock clock,
      @Value("${auratrade.jobs.eod-candles.cutoff:19:00}") LocalTime cutoff,
      @Value("${auratrade.jobs.eod-candles.max-catch-up-days:30}") int maxCatchUp,
      @Value("${auratrade.jobs.eod-candles.series:EQ}") List<String> series,
      @Value("${auratrade.jobs.eod-candles.max-instruments:0}") int maxInstruments,
      @Value("${auratrade.jobs.eod-candles.min-coverage:0.5}") double minCoverage,
      @Value("${auratrade.jobs.eod-candles.benchmarks:NIFTY}") List<String> benchmarks) {
    return new EodCandleJob(
        ingestion,
        calendar,
        clock,
        new EodCandleJob.Settings(
            cutoff, maxCatchUp, series, maxInstruments, minCoverage, benchmarks));
  }

  @Bean
  BackfillRepository backfillRepository(
      org.springframework.jdbc.core.simple.JdbcClient jdbc,
      org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate batch) {
    return new BackfillRepository(jdbc, batch);
  }

  @Bean
  BackfillService backfillService(
      BackfillRepository repository,
      CandleIngestionService ingestion,
      TradingCalendar calendar,
      Clock clock,
      @Value("${auratrade.jobs.backfill.max-attempts:3}") int maxAttempts) {
    return new BackfillService(repository, ingestion, calendar, clock, maxAttempts);
  }

  /** Default batch: 400 shares, about 7 minutes at the Upstox limit we use. */
  @Bean
  BackfillJob backfillJob(
      BackfillService backfills, @Value("${auratrade.jobs.backfill.batch-size:400}") int batch) {
    return new BackfillJob(backfills, batch);
  }

  /** Triggers: cron schedules and a catch-up right after startup. */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(name = "auratrade.jobs.enabled", havingValue = "true")
  static class Triggers {

    private static final Logger log = LoggerFactory.getLogger(Triggers.class);

    private final JobRunner runner;
    private final InstrumentMasterJob instruments;
    private final EodCandleJob eod;
    private final BackfillJob backfill;

    Triggers(
        JobRunner runner, InstrumentMasterJob instruments, EodCandleJob eod, BackfillJob backfill) {
      this.runner = runner;
      this.instruments = instruments;
      this.eod = eod;
      this.backfill = backfill;
    }

    /** Takes the next batch of any open backfill; does nothing when none is open. */
    @Scheduled(cron = "${auratrade.jobs.backfill.cron:0 */15 * * * *}", zone = "Asia/Kolkata")
    void backfill() {
      runner.run(backfill, Trigger.SCHEDULE);
    }

    @Scheduled(cron = "${auratrade.jobs.instrument-master.cron:0 0 7 * * *}", zone = "Asia/Kolkata")
    void instrumentMaster() {
      runner.run(instruments, Trigger.SCHEDULE);
    }

    @Scheduled(cron = "${auratrade.jobs.eod-candles.cron:0 30 19 * * *}", zone = "Asia/Kolkata")
    void eodCandles() {
      runner.run(eod, Trigger.SCHEDULE);
    }

    /** Heals anything missed while the app was down, without delaying startup. */
    @EventListener(ApplicationReadyEvent.class)
    void catchUpAfterStartup() {
      Thread.ofVirtual()
          .name("job-startup-catch-up")
          .start(() -> {
            log.info("Startup catch-up: checking what is owed");
            runner.run(instruments, Trigger.STARTUP);
            runner.run(eod, Trigger.STARTUP);
            runner.run(backfill, Trigger.STARTUP);
          });
    }
  }
}
