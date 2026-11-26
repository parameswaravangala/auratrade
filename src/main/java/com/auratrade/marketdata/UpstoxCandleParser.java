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
