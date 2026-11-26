package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.auratrade.platform.ratelimit.WindowRateLimiter;
import com.auratrade.platform.ratelimit.WindowRateLimiter.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The Upstox client against a mock server. Response bodies copy the shape of a live response
 * (2026-11-26) with made-up numbers; no exchange data.
 */
class UpstoxCandleProviderTest {

  private static final String KEY = "NSE_EQ|INE000A01004";
  private static final String BASE = "https://api.upstox.test";

  private MockRestServiceServer server;
  private UpstoxCandleProvider provider;

  @BeforeEach
  void setUp() {
    provider = providerWithToken("test-token");
  }

  private UpstoxCandleProvider providerWithToken(String token) {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    var limiter =
        new WindowRateLimiter(
            List.of(new Limit(1000, Duration.ofSeconds(1))), Clock.systemUTC(), d -> {});
    return new UpstoxCandleProvider(builder.build(), token, limiter);
  }

  private static String url(String unit, int interval, String to, String from) {
    return BASE
        + "/v3/historical-candle/NSE_EQ%7CINE000A01004/"
        + unit
        + "/"
        + interval
        + "/"
        + to
        + "/"
        + from;
  }

  @Test
  void parsesCandlesOldestFirstWithExactPrices() {
    server
        .expect(requestTo(url("days", 1, "2026-11-20", "2026-11-19")))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andRespond(
            withSuccess(
                """
                {"status":"success","data":{"candles":[
                  ["2026-11-20T00:00:00+05:30",100.05,101.1,99.95,100.75,1200,0],
                  ["2026-11-19T00:00:00+05:30",99.5,100.2,99.1,100.05,900,0]]}}
                """,
                MediaType.APPLICATION_JSON));

    var candles =
        provider.candles(
            KEY, Interval.DAY, LocalDate.parse("2026-11-19"), LocalDate.parse("2026-11-20"));

    server.verify();
    assertThat(candles).hasSize(2);
    assertThat(candles.get(0).tradingDate()).isEqualTo(LocalDate.parse("2026-11-19"));
    assertThat(candles.get(1).close()).isEqualByComparingTo("100.75");
    assertThat(candles.get(1).close().scale()).isEqualTo(2); // straight from the text, no double
    assertThat(candles.get(1).volume()).isEqualTo(1200);
  }

  @Test
  void splitsLongRangesIntoRequestsWithinTheDocumentedMaximum() {
    server
        .expect(requestTo(url("days", 1, "2008-12-31", "2000-01-01")))
        .andRespond(
            withSuccess(
                "{\"status\":\"success\",\"data\":{\"candles\":[]}}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(url("days", 1, "2010-06-30", "2009-01-01")))
        .andRespond(
            withSuccess(
                "{\"status\":\"success\",\"data\":{\"candles\":[]}}", MediaType.APPLICATION_JSON));

    provider.candles(
        KEY, Interval.DAY, LocalDate.parse("2000-01-01"), LocalDate.parse("2010-06-30"));

    server.verify();
  }

  @Test
  void anUnauthorisedAnswerIsAnAuthFailure() {
    server
        .expect(requestTo(url("days", 1, "2026-11-20", "2026-11-20")))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() ->
            provider.candles(
                KEY, Interval.DAY, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-20")))
        .isInstanceOf(ProviderException.Auth.class);
  }

  @Test
  void aMissingTokenFailsBeforeAnyCall() {
    var noToken = providerWithToken("  ");

    assertThatThrownBy(() ->
            noToken.candles(
                KEY, Interval.DAY, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-20")))
        .isInstanceOf(ProviderException.Auth.class)
        .hasMessageContaining("UPSTOX_ANALYTICS_TOKEN");
    server.verify(); // no request was made
  }

  @Test
  void errorAnswersAndChangedFormatsAreRejected() {
    server
        .expect(requestTo(url("days", 1, "2026-11-20", "2026-11-20")))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"status\":\"error\",\"errors\":[{\"errorCode\":\"UDAPI1021\","
                        + "\"message\":\"Invalid instrument key\"}]}"));

    assertThatThrownBy(() ->
            provider.candles(
                KEY, Interval.DAY, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-20")))
        .isInstanceOf(ProviderException.BadResponse.class)
        .hasMessageContaining("UDAPI1021");
  }

  @Test
  void parserRejectsShapesItDoesNotKnow() {
    assertThatThrownBy(() -> UpstoxCandleParser.parse("{\"status\":\"error\"}"))
        .isInstanceOf(ProviderException.BadResponse.class);
    assertThatThrownBy(() ->
            UpstoxCandleParser.parse(
                "{\"status\":\"success\",\"data\":{\"candles\":[[\"2026-11-20T00:00:00+05:30\",1,2,3]]}}"))
        .hasMessageContaining("7 fields");
    assertThatThrownBy(() ->
            UpstoxCandleParser.parse(
                "{\"status\":\"success\",\"data\":{\"candles\":[[1763577000,1,2,0.5,1,10,0]]}}"))
        .hasMessageContaining("timestamp");
    assertThatThrownBy(() ->
            UpstoxCandleParser.parse(
                "{\"status\":\"success\",\"data\":{\"candles\":[[\"2026-11-20T00:00:00+05:30\",1,2,0.5,1,10.5,0]]}}"))
        .hasMessageContaining("volume");
  }

  @Test
  void refusesRangesBeforeTheDocumentedHistory() {
    assertThatThrownBy(() ->
            provider.candles(
                KEY,
                Interval.MINUTE_5,
                LocalDate.parse("2021-12-31"),
                LocalDate.parse("2022-01-05")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2022-01-01");
  }
}
