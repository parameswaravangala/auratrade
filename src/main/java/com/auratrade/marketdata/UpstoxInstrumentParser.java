package com.auratrade.marketdata;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Translates the Upstox instrument file (JSON array, ADR 0015) into provider-neutral instruments.
 * Streams the file, since the NSE file is about 40 MB and mostly derivatives, and keeps only the
 * cash-market (NSE_EQ) and index (NSE_INDEX) segments.
 *
 * <p>Upstox specifics handled here: tick sizes are in paise (checked against the Kite file on
 * 2026-11-26: 2,786 of 2,786 matching shares were exactly 100 times larger), the series is in
 * instrument_type, and instrument_key is the provider key.
 */
final class UpstoxInstrumentParser {

  private static final Set<String> SEGMENTS = Set.of("NSE_EQ", "NSE_INDEX");
  private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

  private static final JsonMapper JSON =
      JsonMapper.builder()
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          // We read one array element at a time, so the rest of the array follows each value.
          .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  /** Fields we read from each record; Upstox sends more. */
  record Row(
      String segment,
      String exchange,
      String name,
      String isin,
      String instrumentType,
      String instrumentKey,
      String exchangeToken,
      BigDecimal tickSize,
      Integer lotSize,
      String tradingSymbol) {}

  private UpstoxInstrumentParser() {}

  static List<Instrument> parse(InputStream json) {
    List<Instrument> out = new ArrayList<>();
    int index = 0;
    try (JsonParser p = JSON.createParser(json)) {
      if (p.nextToken() != JsonToken.START_ARRAY) {
        throw new InstrumentFileException("expected a JSON array of instruments");
      }
      while (p.nextToken() == JsonToken.START_OBJECT) {
        Row row = JSON.readValue(p, Row.class);
        if (row != null && SEGMENTS.contains(row.segment())) {
          try {
            out.add(toInstrument(row));
          } catch (RuntimeException e) {
            throw new InstrumentFileException(
                "record " + index + " (" + row.instrumentKey() + "): " + e.getMessage(), e);
          }
        }
        index++;
      }
    } catch (JacksonException e) {
      throw new InstrumentFileException(
          "malformed instrument JSON near record " + index + ": " + e.getOriginalMessage(), e);
    }
    return out;
  }

  static Instrument toInstrument(Row row) {
    boolean index = "NSE_INDEX".equals(row.segment());
    String isin = blankToNull(row.isin());
    if (!index && isin == null) {
      throw new IllegalArgumentException("security without ISIN");
    }
    return new Instrument(
        required(row.exchange(), "exchange"),
        index ? Instrument.SEGMENT_INDEX : Instrument.SEGMENT_EQ,
        required(row.tradingSymbol(), "trading_symbol"),
        index ? "INDEX" : required(row.instrumentType(), "instrument_type"),
        blankToNull(row.name()),
        index ? null : isin,
        row.exchangeToken() == null || row.exchangeToken().isBlank()
            ? null
            : Long.parseLong(row.exchangeToken().trim()),
        row.tickSize() == null ? BigDecimal.ZERO : row.tickSize().divide(PAISE_PER_RUPEE),
        row.lotSize() == null ? 0 : row.lotSize(),
        required(row.instrumentKey(), "instrument_key"));
  }

  private static String required(String value, String field) {
    String v = blankToNull(value);
    if (v == null) {
      throw new IllegalArgumentException(field + " is missing");
    }
    return v;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
