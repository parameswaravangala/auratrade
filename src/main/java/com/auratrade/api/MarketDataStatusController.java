package com.auratrade.api;

import com.auratrade.marketdata.CalendarCoverage;
import com.auratrade.marketdata.CalendarNotCoveredException;
import com.auratrade.marketdata.DataFreshnessService;
import com.auratrade.marketdata.TradingCalendar;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only data status (ADR 0026): how fresh the daily candles are, in trading sessions, and what
 * the trading calendar covers. Served in every profile: it changes nothing and holds no secrets.
 */
@RestController
@RequestMapping("/api/v1")
class MarketDataStatusController {

  static final URI CALENDAR_NOT_COVERED =
      URI.create("https://auratrade.dev/problems/calendar-not-covered");
  static final URI NO_INSTRUMENTS = URI.create("https://auratrade.dev/problems/no-instruments");

  private final DataFreshnessService freshness;
  private final TradingCalendar calendar;
  private final Clock clock;

  MarketDataStatusController(
      DataFreshnessService freshness, TradingCalendar calendar, Clock clock) {
    this.freshness = freshness;
    this.calendar = calendar;
    this.clock = clock;
  }

  @GetMapping("/marketdata/freshness")
  DataFreshnessService.Freshness freshness() {
    return freshness.freshness();
  }

  @GetMapping("/calendar/coverage")
  CalendarCoverage coverage() {
    return CalendarCoverage.of(calendar, clock.instant());
  }

  @ExceptionHandler(CalendarNotCoveredException.class)
  ProblemDetail notCovered(CalendarNotCoveredException e) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    p.setType(CALENDAR_NOT_COVERED);
    p.setTitle("Trading calendar does not cover the year");
    p.setProperty("code", "CALENDAR_NOT_COVERED");
    p.setProperty("year", e.year());
    return p;
  }

  @ExceptionHandler(DataFreshnessService.NoInstrumentsException.class)
  ProblemDetail noInstruments(DataFreshnessService.NoInstrumentsException e) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    p.setType(NO_INSTRUMENTS);
    p.setTitle("No instruments loaded");
    p.setProperty("code", "NO_INSTRUMENTS");
    return p;
  }
}
