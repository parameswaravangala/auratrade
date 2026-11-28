package com.auratrade.marketdata;

/**
 * The calendar was asked about a year it has no data for. Guessing would silently treat every
 * weekday as a trading day, so we fail closed instead.
 */
public class CalendarNotCoveredException extends IllegalStateException {

  private final int year;

  public CalendarNotCoveredException(int year) {
    super(
        "No trading calendar loaded for "
            + year
            + ". Add a COVERS,"
            + year
            + " row and the holidays from the official NSE holiday list to the calendar file.");
    this.year = year;
  }

  /** The year the calendar does not cover. */
  public int year() {
    return year;
  }
}
