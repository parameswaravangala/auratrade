package com.auratrade.marketdata;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses the Kite instruments CSV (https://api.kite.trade/instruments/NSE). Fails on the first bad
 * row, naming the line, rather than loading a partial file.
 *
 * <p>The file types most NSE rows as EQ, including government securities, bonds and treasury bills.
 * The series is in the symbol suffix: a two-character code after the last hyphen (-BE, -SM, -SG,
 * -N0...). Longer or shorter suffixes are part of the share's own symbol (BAJAJ-AUTO, NAM-INDIA,
 * KLBRENG-B), so those are series EQ. Index rows (segment INDICES) are series INDEX.
 */
public final class InstrumentCsvParser {

  /** Exact header we understand. Any change in the broker's format stops the load. */
  static final List<String> HEADER =
      List.of(
          "instrument_token",
          "exchange_token",
          "tradingsymbol",
          "name",
          "last_price",
          "expiry",
          "strike",
          "tick_size",
          "lot_size",
          "instrument_type",
          "segment",
          "exchange");

  private static final Pattern SERIES_SUFFIX = Pattern.compile(".+-([A-Z0-9]{2})");
  private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9&\\-. ]{1,40}");

  private InstrumentCsvParser() {}

  public static List<Instrument> parse(Reader reader) throws IOException {
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
    try (CSVParser parser = CSVParser.parse(reader, format)) {
      if (!parser.getHeaderNames().equals(HEADER)) {
        throw new InstrumentFileException(
            "unexpected header " + parser.getHeaderNames() + ", expected " + HEADER);
      }
      List<Instrument> out = new ArrayList<>();
      for (CSVRecord row : parser) {
        try {
          out.add(toInstrument(row));
        } catch (RuntimeException e) {
          throw new InstrumentFileException(
              "line " + (row.getRecordNumber() + 1) + ": " + e.getMessage(), e);
        }
      }
      return out;
    }
  }

  static String seriesOf(String tradingSymbol, String segment) {
    if ("INDICES".equals(segment)) {
      return "INDEX";
    }
    var m = SERIES_SUFFIX.matcher(tradingSymbol);
    return m.matches() ? m.group(1) : "EQ";
  }

  private static Instrument toInstrument(CSVRecord row) {
    if (!row.isConsistent()) {
      throw new IllegalArgumentException("expected " + HEADER.size() + " fields");
    }
    String symbol = row.get("tradingsymbol").trim();
    if (!SYMBOL.matcher(symbol).matches()) {
      throw new IllegalArgumentException("invalid trading symbol '" + symbol + "'");
    }
    String exchange = required(row, "exchange");
    String segment = required(row, "segment");
    String name = row.get("name").trim();
    BigDecimal tickSize = new BigDecimal(row.get("tick_size").trim());
    int lotSize = Integer.parseInt(row.get("lot_size").trim());
    if (tickSize.signum() < 0 || lotSize < 0) {
      throw new IllegalArgumentException("negative tick size or lot size");
    }
    return new Instrument(
        exchange,
        symbol,
        seriesOf(symbol, segment),
        segment,
        required(row, "instrument_type"),
        name.isEmpty() ? null : name,
        Long.parseLong(row.get("instrument_token").trim()),
        Long.parseLong(row.get("exchange_token").trim()),
        tickSize,
        lotSize);
  }

  private static String required(CSVRecord row, String column) {
    String value = row.get(column).trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(column + " is empty");
    }
    return value;
  }
}
