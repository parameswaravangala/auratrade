package com.auratrade.screener;

import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.MarketDataProvider;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Screener defaults: series EQ, a minimum 20-day average traded value of INR 1 crore (10,000,000),
 * and Nifty 50 (trading symbol NIFTY) as the relative-strength benchmark.
 */
@Configuration(proxyBeanMethods = false)
class ScreenerConfig {

  @Bean
  ScreenerService screenerService(
      CandleSeriesReader reader,
      MarketDataProvider candleProvider,
      @Value("${auratrade.screener.series:EQ}") List<String> series,
      @Value("${auratrade.screener.min-traded-value:10000000}") BigDecimal minTradedValue,
      @Value("${auratrade.screener.benchmark:NIFTY}") String benchmark) {
    return new ScreenerService(
        reader, candleProvider, Screens.all(), series, minTradedValue, benchmark);
  }
}
