package com.auratrade.paperbroker;

import com.auratrade.backtest.CostModel;
import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.platform.jobs.JobRunRepository.Trigger;
import com.auratrade.platform.jobs.JobRunner;
import com.auratrade.screener.ScreenerService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PaperTradingConfig {

  @Bean
  PaperRepository paperRepository(JdbcClient jdbc) {
    return new PaperRepository(jdbc);
  }

  /** Costs: Zerodha's published equity-delivery charges (see CostModel). */
  @Bean
  PaperTradingService paperTradingService(
      PaperRepository repo,
      CandleSeriesReader reader,
      MarketDataProvider candleProvider,
      ScreenerService screener,
      TradingCalendar calendar,
      PlatformTransactionManager transactions,
      JdbcClient jdbc,
      Clock clock) {
    return new PaperTradingService(
        repo,
        reader,
        candleProvider,
        screener,
        calendar,
        new TransactionTemplate(transactions),
        jdbc,
        clock,
        CostModel.zerodhaDelivery());
  }

  @Bean
  PaperTradingJob paperTradingJob(
      PaperTradingService paper,
      TradingCalendar calendar,
      com.auratrade.platform.jobs.JobRunRepository runs) {
    return new PaperTradingJob(paper, calendar, runs);
  }

  /** 20:15 IST, after the 19:30 EOD job. Exists only when jobs are enabled. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "auratrade.jobs.enabled", havingValue = "true")
  static class Trigger_ {
    private final JobRunner runner;
    private final PaperTradingJob job;

    Trigger_(JobRunner runner, PaperTradingJob job) {
      this.runner = runner;
      this.job = job;
    }

    @Scheduled(cron = "${auratrade.jobs.paper-trading.cron:0 15 20 * * *}", zone = "Asia/Kolkata")
    void paperTrading() {
      runner.run(job, Trigger.SCHEDULE);
    }
  }
}
