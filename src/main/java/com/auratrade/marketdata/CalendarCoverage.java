package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * What the trading calendar covers, for GET /api/v1/calendar/coverage (ADR 0026). Pure: built from
 * the calendar and an instant. Never throws for an uncovered today, since reporting that is the
 * point; fields that would need an uncovered year are null instead of guessed.
 *
 * @param firstDate 1 January of the first covered year (years are covered whole), or null
 * @param lastDate 31 December of the last covered year, or null; years in between may be missing,
 *     see coveredYears
 * @param todayIsTradingDay null when today's year is not covered
 * @param lastClosedSession newest trading day whose 15:30 IST close has passed, or null
 * @param nextSession next trading day whose 09:15 IST open is still ahead, or null
 * @param coverageWarning the startup alerts of {@link CalendarCoverageCheck}, joined, or null
 */
public record CalendarCoverage(
    String exchange,
    String timezone,
    List<Integer> coveredYears,
    LocalDate firstDate,
    LocalDate lastDate,
    LocalDate today,
    boolean todayCovered,
    Boolean todayIsTradingDay,
    LocalDate lastClosedSession,
    LocalDate nextSession,
    boolean nextYearCovered,
    String coverageWarning,
    List<Year> years) {

  /** One covered year. Weekends are not listed; special sessions are trading days. */
  public record Year(
      int year,
      int tradingDays,
      List<TradingCalendar.NamedDay> holidays,
      List<TradingCalendar.NamedDay> specialSessions) {}

  public static CalendarCoverage of(TradingCalendar calendar, Instant now) {
    ZonedDateTime ist = now.atZone(MarketZones.IST);
    LocalDate today = ist.toLocalDate();
    LocalTime time = ist.toLocalTime();
    boolean covered = calendar.covers(today.getYear());

    Boolean tradingToday = covered ? calendar.isTradingDay(today) : null;
    LocalDate lastClosed = null;
    LocalDate next = null;
    if (covered) {
      boolean closedToday = tradingToday && !time.isBefore(TradingCalendar.SESSION_CLOSE);
      boolean opensToday = tradingToday && time.isBefore(TradingCalendar.SESSION_OPEN);
      lastClosed = closedToday ? today : orNull(() -> calendar.previousTradingDay(today));
      next = opensToday ? today : orNull(() -> calendar.nextTradingDay(today));
    }

    List<String> alerts = CalendarCoverageCheck.alerts(calendar, today);
    List<Year> years =
        calendar.coveredYears().stream()
            .map(y ->
                new Year(
                    y,
                    calendar
                        .tradingDaysBetween(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
                        .size(),
                    calendar.holidaysIn(y),
                    calendar.specialSessionsIn(y)))
            .toList();
    return new CalendarCoverage(
        "NSE",
        MarketZones.IST.getId(),
        calendar.coveredYears(),
        calendar.coveredYears().isEmpty()
            ? null
            : LocalDate.of(calendar.coveredYears().getFirst(), 1, 1),
        calendar.coveredYears().isEmpty()
            ? null
            : LocalDate.of(calendar.coveredYears().getLast(), 12, 31),
        today,
        covered,
        tradingToday,
        lastClosed,
        next,
        calendar.covers(today.getYear() + 1),
        alerts.isEmpty() ? null : String.join(" ", alerts),
        years);
  }

  /** A neighbouring trading day may fall in an uncovered year (1 January, 31 December). */
  private static LocalDate orNull(java.util.function.Supplier<LocalDate> day) {
    try {
      return day.get();
    } catch (CalendarNotCoveredException e) {
      return null;
    }
  }
}
