package com.auratrade.backtest;

import com.auratrade.marketdata.CandleSeriesReader;
import com.auratrade.marketdata.MarketDataProvider;
import com.auratrade.marketdata.TradingCalendar;
import com.auratrade.screener.ScreenerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BacktestConfig {

  @Bean
  ScreenBacktestService screenBacktestService(
      CandleSeriesReader reader,
      MarketDataProvider candleProvider,
      ScreenerService screener,
      TradingCalendar calendar) {
    return new ScreenBacktestService(reader, candleProvider, screener, calendar);
  }

  @Bean
  PortfolioSimulationService portfolioSimulationService(
      CandleSeriesReader reader,
      MarketDataProvider candleProvider,
      ScreenerService screener,
      TradingCalendar calendar) {
    return new PortfolioSimulationService(reader, candleProvider, screener, calendar);
  }

  @Bean
  StrategySweepService strategySweepService(PortfolioSimulationService simulation) {
    return new StrategySweepService(simulation);
  }
}
