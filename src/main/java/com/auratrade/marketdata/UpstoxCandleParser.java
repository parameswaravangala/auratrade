package com.auratrade.marketdata;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an Upstox historical-candle response. Shape verified against a live response on
 * 2026-11-26: {"status":"success","data":{"candles":[["2026-11-20T00:00:00+05:30", open, high, low,
 * close, volume, open_interest], ...]}}, newest first. Strict: anything else fails, so a format
 * change at Upstox stops ingestion instead of storing bad data.
 */
final class UpstoxCandleParser {

  /** Prices are read as BigDecimal straight from the JSON text, never through double. */
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private static final int FIELDS = 7;

  private UpstoxCandleParser() {}

  /** Candles oldest first. */
  static List<Candle> parse(String body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException e) {
      throw bad("response is not JSON: " + e.getOriginalMessage(), e);
    }
    if (!"success".equals(root.path("status").asString(""))) {
      throw bad("status is not success: " + abbreviate(body), null);
    }
    JsonNode candles = root.path("data").path("candles");
    if (!candles.isArray()) {
      throw bad("data.candles is missing or not an array", null);
    }
    List<Candle> out = new ArrayList<>(candles.size());
    int i = 0;
    for (JsonNode c : candles) {
      try {
        out.add(toCandle(c));
      } catch (RuntimeException e) {
        throw bad("candle " + i + " " + abbreviate(c.toString()) + ": " + e.getMessage(), e);
      }
      i++;
    }
    out.sort(Comparator.comparing(Candle::ts));
    return out;
  }

  /**
   * Parses a v3 OHLC quote response (GET /v3/market-quote/ohlc?interval=1d). Shape verified live on
   * 2026-11-27 at 04:42 IST: {"status":"success","data":{"NSE_EQ:RUPA":{"last_price":117.55,
   * "instrument_token":"NSE_EQ|INE895B01021","prev_ohlc":null,"live_ohlc":{"open":118.12,
   * "high":118.89,"low":117.12,"close":117.55,"volume":98838,"ts":1795631400000}}}}. {@code
   * live_ohlc} was then the 26 Nov session, and {@code ts} its IST midnight. The data map is keyed
   * by exchange:symbol, so instruments are matched on {@code instrument_token}, our provider key.
   * An instrument without {@code live_ohlc} is left out; anything malformed fails the response.
   *
   * @return daily bars keyed by provider key, stamped with {@code ts}
   */
  static java.util.Map<String, Candle> parseSessionBars(String body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException e) {
      throw bad("response is not JSON: " + e.getOriginalMessage(), e);
    }
    if (!"success".equals(root.path("status").asString(""))) {
      throw bad("status is not success: " + abbreviate(body), null);
    }
    JsonNode data = root.path("data");
    if (!data.isObject()) {
      throw bad("data is missing or not an object", null);
    }
    java.util.Map<String, Candle> out = new java.util.HashMap<>();
    for (var entry : data.properties()) {
      JsonNode q = entry.getValue();
      try {
        String key = q.path("instrument_token").asString("");
        if (key.isEmpty()) {
          throw new IllegalArgumentException("instrument_token is missing");
        }
        JsonNode bar = q.path("live_ohlc");
        if (bar.isMissingNode() || bar.isNull()) {
          continue;
        }
        JsonNode ts = bar.path("ts");
        if (!ts.isIntegralNumber()) {
          throw new IllegalArgumentException("live_ohlc.ts is not a whole number");
        }
        out.put(
            key,
            new Candle(
                java.time.Instant.ofEpochMilli(ts.longValue()),
                price(bar.path("open"), "open"),
                price(bar.path("high"), "high"),
                price(bar.path("low"), "low"),
                price(bar.path("close"), "close"),
                volume(bar.path("volume"))));
      } catch (RuntimeException e) {
        throw bad(entry.getKey() + " " + abbreviate(q.toString()) + ": " + e.getMessage(), e);
      }
    }
    return out;
  }

  private static Candle toCandle(JsonNode c) {
    if (!c.isArray() || c.size() != FIELDS) {
      throw new IllegalArgumentException("expected an array of " + FIELDS + " fields");
    }
    if (!c.get(0).isString()) {
      throw new IllegalArgumentException("timestamp is not a string");
    }
    try {
      return new Candle(
          OffsetDateTime.parse(c.get(0).asString()).toInstant(),
          price(c.get(1), "open"),
          price(c.get(2), "high"),
          price(c.get(3), "low"),
          price(c.get(4), "close"),
          volume(c.get(5)));
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("bad timestamp '" + c.get(0).asString() + "'");
    }
  }

  private static BigDecimal price(JsonNode n, String field) {
    if (!n.isNumber()) {
      throw new IllegalArgumentException(field + " is not a number");
    }
    return n.decimalValue();
  }

  private static long volume(JsonNode n) {
    if (!n.isIntegralNumber() || !n.canConvertToLong()) {
      throw new IllegalArgumentException("volume is not a whole number");
    }
    return n.longValue();
  }

  private static ProviderException.BadResponse bad(String message, Throwable cause) {
    return new ProviderException.BadResponse(UpstoxCandleProvider.NAME, message, cause);
  }

  static String abbreviate(String s) {
    return s.length() <= 200 ? s : s.substring(0, 200) + "...";
  }
}
