package com.auratrade.marketdata;

import com.auratrade.platform.ratelimit.WindowRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

/**
 * Upstox historical candles, v3 (ADR 0015). Facts used, from the Upstox docs and a live call on
 * 2026-11-26: GET /v3/historical-candle/{instrument_key}/{unit}/{interval}/{to_date}/{from_date};
 * days back to January 2000, at most one decade per request; minutes and hours from January 2022,
 * at most one month (1-15 minutes) or one quarter (hours) per request.
 *
 * <p>Uses the Analytics token (read-only, one year). The token is never logged. A missing token, or
 * a 401 or 403 from Upstox, raises {@link ProviderException.Auth}.
 */
final class UpstoxCandleProvider implements MarketDataProvider {

  static final String NAME = "UPSTOX";

  static final LocalDate DAILY_HISTORY_START = LocalDate.of(2000, 1, 1);
  static final LocalDate INTRADAY_HISTORY_START = LocalDate.of(2022, 1, 1);

  private record Unit(String unit, int interval, Period maxSpan, LocalDate historyStart) {}

  /**
   * Request spans stay a little inside the documented maxima ("1 decade", "1 month", "1 quarter")
   * so that month lengths and leap years never push a request over.
   */
  private static final Map<Interval, Unit> UNITS =
      Map.of(
          Interval.DAY, new Unit("days", 1, Period.ofYears(9), DAILY_HISTORY_START),
          Interval.MINUTE_1, new Unit("minutes", 1, Period.ofDays(27), INTRADAY_HISTORY_START),
          Interval.MINUTE_5, new Unit("minutes", 5, Period.ofDays(27), INTRADAY_HISTORY_START),
          Interval.MINUTE_15, new Unit("minutes", 15, Period.ofDays(27), INTRADAY_HISTORY_START),
          Interval.HOUR_1, new Unit("hours", 1, Period.ofDays(88), INTRADAY_HISTORY_START));

  private final RestClient http;
  private final String token;
  private final WindowRateLimiter limiter;

  /**
   * @param http a RestClient whose base URL is the Upstox API root, for example
   *     https://api.upstox.com
   * @param token the Analytics token; blank means not configured
   */
  UpstoxCandleProvider(RestClient http, String token, WindowRateLimiter limiter) {
    this.http = http;
    this.token = token == null ? "" : token.trim();
    this.limiter = limiter;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<Interval> supportedIntervals() {
    return EnumSet.copyOf(UNITS.keySet());
  }

  @Override
  public List<Candle> candles(String providerKey, Interval interval, LocalDate from, LocalDate to) {
    Unit unit = UNITS.get(interval);
    if (unit == null) {
      throw new IllegalArgumentException(NAME + " does not support " + interval);
    }
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from " + from + " is after to " + to);
    }
    if (from.isBefore(unit.historyStart())) {
      throw new IllegalArgumentException(
          NAME + " has " + interval + " history only from " + unit.historyStart());
    }
    if (token.isEmpty()) {
      throw new ProviderException.Auth(
          NAME, "UPSTOX_ANALYTICS_TOKEN is not set; generate it in the Upstox developer console");
    }
    List<Candle> out = new ArrayList<>();
    for (LocalDate start = from; !start.isAfter(to); ) {
      LocalDate end = start.plus(unit.maxSpan()).minusDays(1);
      if (end.isAfter(to)) {
        end = to;
      }
      out.addAll(fetch(providerKey, unit, start, end));
      start = end.plusDays(1);
    }
    return interval == Interval.DAY ? atIstMidnight(out) : out;
  }

  /**
   * Upstox stamps daily bars up to 2018-11-30 with the session open (09:15 IST) and from 2018-12-03
   * with midnight (checked 2026-11-26). The date part is the IST trading date in both cases, so
   * daily bars are restamped to IST midnight of that date, our convention for DAY.
   */
  static List<Candle> atIstMidnight(List<Candle> candles) {
    return candles.stream()
        .map(c ->
            new Candle(
                c.tradingDate()
                    .atStartOfDay(com.auratrade.platform.time.MarketZones.IST)
                    .toInstant(),
                c.open(),
                c.high(),
                c.low(),
                c.close(),
                c.volume()))
        .toList();
  }

  private List<Candle> fetch(String key, Unit unit, LocalDate from, LocalDate to) {
    limiter.acquire();
    return http.get()
        .uri(
            "/v3/historical-candle/{key}/{unit}/{interval}/{to}/{from}",
            key,
            unit.unit(),
            unit.interval(),
            to,
            from)
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .exchange((request, response) -> {
          String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
          HttpStatusCode status = response.getStatusCode();
          if (status.value() == 401 || status.value() == 403) {
            throw new ProviderException.Auth(
                NAME,
                "HTTP " + status.value() + ": the Analytics token is missing, expired or revoked");
          }
          if (status.value() == 429) {
            throw new ProviderException.RateLimited(NAME, "HTTP 429 for " + key);
          }
          if (status.isError()) {
            throw new ProviderException.BadResponse(
                NAME,
                "HTTP "
                    + status.value()
                    + " for "
                    + key
                    + ": "
                    + UpstoxCandleParser.abbreviate(body));
          }
          return UpstoxCandleParser.parse(body);
        });
  }
}
