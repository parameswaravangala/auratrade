package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
class MarketDataConfig {

  private static final Logger log = LoggerFactory.getLogger(MarketDataConfig.class);

  /**
   * Loads the NSE calendar. The app starts even if the file is empty, but any question about an
   * uncovered year fails closed. Override the location with auratrade.marketdata.calendar-file, for
   * example file:/opt/auratrade/nse-calendar.csv.
   */
  @Bean
  TradingCalendar tradingCalendar(
      @Value("${auratrade.marketdata.calendar-file:classpath:calendar/nse-calendar.csv}")
          Resource file,
      Clock clock)
      throws IOException {
    try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
      TradingCalendar calendar = TradingCalendar.fromCsv(reader);
      int year = LocalDate.now(clock.withZone(MarketZones.IST)).getYear();
      if (!calendar.covers(year)) {
        log.warn(
            "Trading calendar has no data for {}. Market-data checks will fail closed until you add it.",
            year);
      }
      return calendar;
    }
  }

  @Bean
  CandleValidator candleValidator(TradingCalendar calendar) {
    return new CandleValidator(calendar, QualityRules.defaults());
  }
}
