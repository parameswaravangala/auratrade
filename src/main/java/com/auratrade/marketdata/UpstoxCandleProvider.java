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
final class UpstoxCandleProvider implements MarketDataProvider, SessionBarProvider {

  /** Documented maximum for the v3 OHLC quote ("up to 500 instruments", read 2026-11-27). */
  static final int MAX_QUOTE_KEYS = 500;

  @Override
  public int maxKeysPerCall() {
    return MAX_QUOTE_KEYS;
  }

  /**
   * One GET /v3/market-quote/ohlc?interval=1d call for up to 500 keys (Upstox docs, read
   * 2026-11-27; the Analytics token covers Market Quote). Bars are restamped to IST midnight of the
   * date in {@code ts}, our DAY convention.
   */
  @Override
  public Map<String, Candle> latestSessionBars(List<String> providerKeys) {
    if (providerKeys.isEmpty()) {
      return Map.of();
    }
    if (providerKeys.size() > MAX_QUOTE_KEYS) {
      throw new IllegalArgumentException(
          NAME + " quotes take at most " + MAX_QUOTE_KEYS + " keys, got " + providerKeys.size());
    }
    if (token.isEmpty()) {
      throw new ProviderException.Auth(
          NAME, "UPSTOX_ANALYTICS_TOKEN is not set; generate it in the Upstox developer console");
    }
    Map<String, Candle> bars =
        fetch(
            providerKeys.size() + " quote keys",
            UpstoxCandleParser::parseSessionBars,
            "/v3/market-quote/ohlc?interval=1d&instrument_key={keys}",
            String.join(",", providerKeys));
    Map<String, Candle> out = new java.util.HashMap<>();
    bars.forEach((key, bar) -> out.put(key, atIstMidnight(List.of(bar)).getFirst()));
    return out;
  }

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
   * Seen 2026-11-26: besides Upstox's published API limits, Cloudflare in front of api.upstox.com
   * answers 429 "error code: 1015" with a Retry-After header (151 s), at a threshold that is not
   * published. The pause is the Retry-After plus a margin, at least one minute; a provider asking
   * for longer than {@link #MAX_RATE_LIMIT_PAUSE} is not waited for.
   */
  static final java.time.Duration RATE_LIMIT_PAUSE = java.time.Duration.ofMinutes(1);

  static final java.time.Duration RETRY_AFTER_MARGIN = java.time.Duration.ofSeconds(5);
  static final java.time.Duration MAX_RATE_LIMIT_PAUSE = java.time.Duration.ofMinutes(10);
  static final int RATE_LIMIT_RETRIES = 3;

  static java.time.Duration pauseFor(ProviderException.RateLimited e) {
    return e.retryAfter()
        .map(d -> d.plus(RETRY_AFTER_MARGIN))
        .filter(d -> d.compareTo(RATE_LIMIT_PAUSE) > 0)
        .orElse(RATE_LIMIT_PAUSE);
  }

  /** Retry-After in delta-seconds; an HTTP date or anything else is ignored. */
  static java.time.Duration retryAfter(String header) {
    if (header == null || !header.trim().matches("\\d{1,6}")) {
      return null;
    }
    return java.time.Duration.ofSeconds(Long.parseLong(header.trim()));
  }

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
    return fetch(key, UpstoxCandleParser::parse, uriTemplate, uriVariables);
  }

  private <T> T fetch(
      String key,
      java.util.function.Function<String, T> parser,
      String uriTemplate,
      Object... uriVariables) {
    for (int attempt = 0; ; attempt++) {
      try {
        return fetchOnce(key, parser, uriTemplate, uriVariables);
      } catch (ProviderException.RateLimited e) {
        java.time.Duration pause = pauseFor(e);
        if (attempt >= RATE_LIMIT_RETRIES || pause.compareTo(MAX_RATE_LIMIT_PAUSE) > 0) {
          throw e;
        }
        try {
          sleeper.sleep(pause);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  private <T> T fetchOnce(
      String key,
      java.util.function.Function<String, T> parser,
      String uriTemplate,
      Object... uriVariables) {
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
            var after = retryAfter(response.getHeaders().getFirst("Retry-After"));
            throw new ProviderException.RateLimited(
                NAME,
                "HTTP 429 for "
                    + key
                    + (after == null ? "" : ", retry after " + after.toSeconds() + " s")
                    + ": "
                    + UpstoxCandleParser.abbreviate(body),
                after);
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
          return parser.apply(body);
        });
  }
}
