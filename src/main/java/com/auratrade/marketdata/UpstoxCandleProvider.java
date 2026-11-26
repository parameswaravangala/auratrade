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
  private final java.time.Clock clock;
  private final WindowRateLimiter.Sleeper sleeper;

  /**
   * A 429 is retried after a pause, because one rejected call should not end a run of thousands.
   * Upstox's published limits (rate-limiting page, read 2026-11-26) are per second, per minute and
   * per 30 minutes; our limiter stays at 80% of them, yet Upstox answered 429 on 2026-11-26, so its
   * counting differs from ours in some way we cannot see. One minute clears its per-minute window.
   */
  static final java.time.Duration RATE_LIMIT_PAUSE = java.time.Duration.ofMinutes(1);

  static final int RATE_LIMIT_RETRIES = 3;

  /**
   * @param http a RestClient whose base URL is the Upstox API root, for example
   *     https://api.upstox.com
   * @param token the Analytics token; blank means not configured
   * @param clock decides which date is "today" (IST)
   */
  UpstoxCandleProvider(
      RestClient http, String token, WindowRateLimiter limiter, java.time.Clock clock) {
    this(http, token, limiter, clock, d -> Thread.sleep(d.toMillis()));
  }

  UpstoxCandleProvider(
      RestClient http,
      String token,
      WindowRateLimiter limiter,
      java.time.Clock clock,
      WindowRateLimiter.Sleeper sleeper) {
    this.sleeper = sleeper;
    this.http = http;
    this.token = token == null ? "" : token.trim();
    this.limiter = limiter;
    this.clock = clock;
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
    // The historical endpoint stops at yesterday; today's daily bar comes from the intraday
    // endpoint (checked live 2026-11-26). Split a DAY request that reaches today.
    LocalDate today = LocalDate.now(clock.withZone(com.auratrade.platform.time.MarketZones.IST));
    boolean includesToday = interval == Interval.DAY && !from.isAfter(today) && !to.isBefore(today);
    LocalDate historicalTo = includesToday ? today.minusDays(1) : to;
    List<Candle> out = new ArrayList<>();
    for (LocalDate start = from; !start.isAfter(historicalTo); ) {
      LocalDate end = start.plus(unit.maxSpan()).minusDays(1);
      if (end.isAfter(historicalTo)) {
        end = historicalTo;
      }
      out.addAll(
          fetch(
              providerKey,
              "/v3/historical-candle/{key}/{unit}/{interval}/{to}/{from}",
              providerKey,
              unit.unit(),
              unit.interval(),
              end,
              start));
      start = end.plusDays(1);
    }
    if (includesToday) {
      fetch(providerKey, "/v3/historical-candle/intraday/{key}/days/1", providerKey).stream()
          .filter(c -> c.tradingDate().equals(today))
          .forEach(out::add);
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

  private List<Candle> fetch(String key, String uriTemplate, Object... uriVariables) {
    for (int attempt = 0; ; attempt++) {
      try {
        return fetchOnce(key, uriTemplate, uriVariables);
      } catch (ProviderException.RateLimited e) {
        if (attempt >= RATE_LIMIT_RETRIES) {
          throw e;
        }
        try {
          sleeper.sleep(RATE_LIMIT_PAUSE);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  private List<Candle> fetchOnce(String key, String uriTemplate, Object... uriVariables) {
    limiter.acquire();
    return http.get()
        .uri(uriTemplate, uriVariables)
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
