package com.auratrade.marketdata;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

/**
 * Warns before the calendar runs out. The current year must be covered; from 1 December the next
 * year must be covered too, so the new year does not start with every market-data check failing
 * closed. Pure logic: the caller passes today's date in IST and decides where alerts go.
 */
final class CalendarCoverageCheck {

  private CalendarCoverageCheck() {}

  static List<String> alerts(TradingCalendar calendar, LocalDate todayIst) {
    List<String> alerts = new ArrayList<>();
    int year = todayIst.getYear();
    if (!calendar.covers(year)) {
      alerts.add(
          "Trading calendar has no data for "
              + year
              + ". Market-data checks fail closed until you add it. See"
              + " docs/runbooks/trading-calendar.md.");
    }
    if (todayIst.getMonth() == Month.DECEMBER && !calendar.covers(year + 1)) {
      alerts.add(
          "Trading calendar has no data for "
              + (year + 1)
              + ". From 1 January every market-data check will fail closed. Add the official NSE"
              + " holiday list for "
              + (year + 1)
              + ". See docs/runbooks/trading-calendar.md.");
    }
    return alerts;
  }
}
