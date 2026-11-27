package com.auratrade.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The two read-only status endpoints over HTTP, with the real calendar file and a clock fixed in
 * 2027, a year the calendar does not cover yet: coverage reports it, freshness fails closed.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {com.auratrade.AuraTradeApplication.class, MarketDataStatusApiIT.FixedClock.class})
class MarketDataStatusApiIT extends PostgresIntegrationTest {

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2027-01-05T14:30:00Z"), ZoneOffset.UTC); // 20:00 IST
    }
  }

  @Value("${local.server.port}")
  int port;

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void coverageReportsAnUncoveredTodayInsteadOfFailing() throws Exception {
    var r = get("/api/v1/calendar/coverage");

    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body())
        .contains("\"today\":\"2027-01-05\"")
        .contains("\"todayCovered\":false")
        .contains("\"todayIsTradingDay\":null")
        .contains("\"firstDate\":\"2018-01-01\"")
        .contains("\"coverageWarning\":\"Trading calendar has no data for 2027");
  }

  @Test
  void freshnessFailsClosedWithAProblemWhenTheCalendarEnds() throws Exception {
    var r = get("/api/v1/marketdata/freshness");

    assertThat(r.statusCode()).isEqualTo(409);
    assertThat(r.headers().firstValue("Content-Type"))
        .hasValueSatisfying(v -> assertThat(v).startsWith("application/problem+json"));
    assertThat(r.body())
        .contains("\"type\":\"https://auratrade.dev/problems/calendar-not-covered\"")
        .contains("\"code\":\"CALENDAR_NOT_COVERED\"")
        .contains("\"year\":2027");
  }
}
