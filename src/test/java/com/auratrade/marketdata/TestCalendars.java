package com.auratrade.marketdata;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Loads the fixture calendar (not an official NSE list). */
final class TestCalendars {

  private TestCalendars() {}

  static TradingCalendar fixture() {
    try (Reader reader =
        new InputStreamReader(
            TestCalendars.class.getResourceAsStream("/calendar/test-calendar.csv"),
            StandardCharsets.UTF_8)) {
      return TradingCalendar.fromCsv(reader);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
