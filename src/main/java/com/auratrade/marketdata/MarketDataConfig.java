package com.auratrade.marketdata;

import com.auratrade.platform.ratelimit.WindowRateLimiter;
import com.auratrade.platform.time.MarketZones;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

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
   * The instrument feed in use (ADR 0015). The loader depends only on {@link InstrumentFeed}, so
   * changing provider means changing this bean.
   */
  @Bean
  InstrumentFeed instrumentFeed(
      @Value(
              "${auratrade.marketdata.upstox.instruments-url:"
                  + "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz}")
          URI url) {
    return new UpstoxInstrumentFeed(url, Duration.ofSeconds(60));
  }

  /**
   * Instrument master loader. Defaults: at least 5,000 valid rows, at most a 10% drop since the
   * previous load, and at most 25 rejected rows. Override with auratrade.marketdata.instruments.*.
   */
  @Bean
  InstrumentLoader instrumentLoader(
      InstrumentFeed feed,
      @Value("${auratrade.marketdata.instruments.min-rows:5000}") int minRows,
      @Value("${auratrade.marketdata.instruments.max-shrink:0.10}") double maxShrink,
      @Value("${auratrade.marketdata.instruments.max-rejected:25}") int maxRejected,
      InstrumentRepository repository,
      PlatformTransactionManager transactions,
      Clock clock) {
    return new InstrumentLoader(
        feed,
        repository,
        new TransactionTemplate(transactions),
        clock,
        new InstrumentLoader.Rules("NSE", minRows, maxShrink, maxRejected));
  }

  /**
   * Upstox candles. The token comes from the environment (UPSTOX_ANALYTICS_TOKEN) and may be
   * absent: the app still starts, and candle calls fail with an auth error naming the variable. The
   * limiter stays at 80% of the published limits for this API group (50/s, 500/min, 2000/30 min).
   */
  @Bean
  UpstoxCandleProvider upstoxCandleProvider(
      @Value("${auratrade.marketdata.upstox.api-base:https://api.upstox.com}") String apiBase,
      @Value("${UPSTOX_ANALYTICS_TOKEN:}") String token,
      Clock clock) {
    Duration timeout = Duration.ofSeconds(30);
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    RestClient http =
        RestClient.builder()
            .baseUrl(apiBase)
            .requestFactory(factory)
            .defaultHeader("User-Agent", "auratrade")
            .build();
    WindowRateLimiter limiter =
        WindowRateLimiter.realTime(
            List.of(
                new WindowRateLimiter.Limit(40, Duration.ofSeconds(1)),
                new WindowRateLimiter.Limit(400, Duration.ofMinutes(1)),
                new WindowRateLimiter.Limit(1600, Duration.ofMinutes(30))),
            clock);
    return new UpstoxCandleProvider(http, token, limiter);
  }

  /**
   * The candle provider consumers use (ADR 0016), chosen by auratrade.marketdata.candles.provider.
   * Development defaults to upstox; production must set it explicitly. An unknown name stops
   * startup rather than falling back silently.
   */
  @Bean
  MarketDataProvider candleProvider(
      @Value("${auratrade.marketdata.candles.provider}") String name, UpstoxCandleProvider upstox) {
    return switch (name.trim().toUpperCase(Locale.ROOT)) {
      case UpstoxCandleProvider.NAME -> upstox;
      default ->
          throw new IllegalStateException(
              "Unknown auratrade.marketdata.candles.provider '" + name + "'; supported: upstox");
    };
  }

  @Bean
  CandleRepository candleRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    return new CandleRepository(jdbc, batch);
  }

  @Bean
  CandleIngestionService candleIngestionService(
      MarketDataProvider candleProvider,
      CandleRepository candles,
      CandleValidator validator,
      Clock clock) {
    return new CandleIngestionService(candleProvider, candles, validator, clock);
  }

  @Bean
  CandleSeriesReader candleSeriesReader(JdbcClient jdbc) {
    return new CandleSeriesReader(jdbc);
  }
}
