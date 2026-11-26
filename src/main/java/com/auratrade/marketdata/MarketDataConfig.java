package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class MarketDataConfig {

  /** Same pattern as the LLM budget alerts: a dedicated logger that a real channel can replace. */
  private static final Logger alerts =
      LoggerFactory.getLogger("com.auratrade.marketdata.CalendarAlerts");

  /**
   * Loads the NSE calendar once at startup. The app starts even if the file is empty, but any
   * question about an uncovered year fails closed. Override the location with
   * auratrade.marketdata.calendar-file, for example file:/opt/auratrade/nse-calendar.csv (see
   * docs/runbooks/trading-calendar.md).
   */
  @Bean
  TradingCalendar tradingCalendar(
      @Value("${auratrade.marketdata.calendar-file:classpath:calendar/nse-calendar.csv}")
          Resource file,
      Clock clock)
      throws IOException {
    try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
      TradingCalendar calendar = TradingCalendar.fromCsv(reader);
      LocalDate today = LocalDate.now(clock.withZone(MarketZones.IST));
      CalendarCoverageCheck.alerts(calendar, today).forEach(alerts::warn);
      return calendar;
    }
  }

  @Bean
  CandleValidator candleValidator(TradingCalendar calendar) {
    return new CandleValidator(calendar, QualityRules.defaults());
  }

  @Bean
  SymbolResolverService symbolResolverService(JdbcClient jdbc, Clock clock) {
    return new SymbolResolverService(new ResolverRepository(jdbc), clock);
  }

  @Bean
  InstrumentRepository instrumentRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    return new InstrumentRepository(jdbc, batch);
  }

  /**
   * Instrument master loader. Defaults: the Kite NSE file, at least 5,000 rows, and at most a 10%
   * drop since the previous load. Override with auratrade.marketdata.instruments.*.
   */
  @Bean
  InstrumentLoader instrumentLoader(
      @Value("${auratrade.marketdata.instruments.url:https://api.kite.trade/instruments/NSE}")
          URI url,
      @Value("${auratrade.marketdata.instruments.min-rows:5000}") int minRows,
      @Value("${auratrade.marketdata.instruments.max-shrink:0.10}") double maxShrink,
      InstrumentRepository repository,
      PlatformTransactionManager transactions,
      Clock clock) {
    return new InstrumentLoader(
        new KiteInstrumentSource(url, Duration.ofSeconds(30)),
        repository,
        new TransactionTemplate(transactions),
        clock,
        new InstrumentLoader.Rules("NSE", minRows, maxShrink));
  }
}
