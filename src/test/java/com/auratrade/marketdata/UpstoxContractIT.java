package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.platform.ratelimit.WindowRateLimiter;
import com.auratrade.platform.ratelimit.WindowRateLimiter.Limit;
import com.auratrade.platform.time.MarketZones;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Live check that Upstox still behaves as our code assumes (ADR 0015). Excluded from normal builds;
 * run with {@code ./mvnw verify -Pcontract} and UPSTOX_ANALYTICS_TOKEN in the environment. Reads
 * live data but stores nothing.
 */
@Tag("contract")
class UpstoxContractIT {

  @Test
  void instrumentFileAndDailyCandlesHaveTheExpectedShape() throws Exception {
    String token = System.getenv("UPSTOX_ANALYTICS_TOKEN");
    assertThat(token).as("UPSTOX_ANALYTICS_TOKEN must be set for contract tests").isNotBlank();

    List<Instrument> instruments =
        new UpstoxInstrumentFeed(
                URI.create(
                    "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz"),
                Duration.ofSeconds(60))
            .fetch();
    Instrument share =
        instruments.stream()
            .filter(i -> "EQ".equals(i.series()) && Isin.isValid(i.isin()))
            .findFirst()
            .orElseThrow();

    var provider =
        new UpstoxCandleProvider(
            RestClient.builder().baseUrl("https://api.upstox.com").build(),
            token,
            WindowRateLimiter.realTime(
                List.of(new Limit(5, Duration.ofSeconds(1))), Clock.systemUTC()));
    LocalDate to = LocalDate.now(MarketZones.IST).minusDays(1);
    List<Candle> candles =
        provider.candles(share.providerKey(), Interval.DAY, to.minusDays(14), to);

    assertThat(candles).as("daily candles for " + share.tradingSymbol()).isNotEmpty();
    for (int i = 0; i < candles.size(); i++) {
      Candle c = candles.get(i);
      assertThat(c.ts().atZone(MarketZones.IST).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
      assertThat(c.low()).isPositive();
      assertThat(c.high()).isGreaterThanOrEqualTo(c.open().max(c.close()));
      assertThat(c.low()).isLessThanOrEqualTo(c.open().min(c.close()));
      if (i > 0) {
        assertThat(c.ts()).isAfter(candles.get(i - 1).ts()); // oldest first after sorting
      }
    }
  }
}
