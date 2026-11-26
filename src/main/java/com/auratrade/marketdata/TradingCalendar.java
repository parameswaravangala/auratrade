package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NSE equity trading days. Weekends and listed holidays are closed; special sessions (for example a
 * Saturday session) are open. A year with no COVERS entry is unknown, and every query for it throws
 * CalendarNotCoveredException rather than guessing.
 *
 * <p>File format, one row per line, # starts a comment: COVERS,2026 / HOLIDAY,2026-01-26,reason /
 * SPECIAL,2026-03-14,reason
 */
public final class TradingCalendar {

  public static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
  public static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);

  private final Set<LocalDate> holidays;
  private final Set<LocalDate> specialSessions;
  private final Set<Integer> coveredYears;

  public TradingCalendar(
      Set<LocalDate> holidays, Set<LocalDate> specialSessions, Set<Integer> coveredYears) {
    this.holidays = Set.copyOf(holidays);
    this.specialSessions = Set.copyOf(specialSessions);
    this.coveredYears = Set.copyOf(coveredYears);
  }

  public boolean covers(int year) {
    return coveredYears.contains(year);
  }

  public boolean isTradingDay(LocalDate date) {
    requireCovered(date);
    if (specialSessions.contains(date)) {
      return true;
    }
    DayOfWeek day = date.getDayOfWeek();
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
      return false;
    }
    return !holidays.contains(date);
  }

  public boolean isSpecialSession(LocalDate date) {
    requireCovered(date);
    return specialSessions.contains(date);
  }

  public LocalDate nextTradingDay(LocalDate date) {
    LocalDate d = date.plusDays(1);
    while (!isTradingDay(d)) {
      d = d.plusDays(1);
    }
    return d;
  }

  public LocalDate previousTradingDay(LocalDate date) {
    LocalDate d = date.minusDays(1);
    while (!isTradingDay(d)) {
      d = d.minusDays(1);
    }
    return d;
  }

  /** Trading days from..toInclusive, in order. Empty if from is after to. */
  public List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate toInclusive) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(toInclusive); d = d.plusDays(1)) {
      if (isTradingDay(d)) {
        days.add(d);
      }
    }
    return days;
  }

  /**
   * True if the instant falls on a trading day within 09:15 to 15:30 IST. On special sessions the
   * hours are not known, so any time on that day is accepted.
   */
  public boolean isWithinSession(Instant ts) {
    ZonedDateTime z = ts.atZone(MarketZones.IST);
    LocalDate date = z.toLocalDate();
    if (!isTradingDay(date)) {
      return false;
    }
    if (specialSessions.contains(date)) {
      return true;
    }
    LocalTime t = z.toLocalTime();
    return !t.isBefore(SESSION_OPEN) && t.isBefore(SESSION_CLOSE);
  }

  private void requireCovered(LocalDate date) {
    if (!coveredYears.contains(date.getYear())) {
      throw new CalendarNotCoveredException(date.getYear());
    }
  }

  public static TradingCalendar fromCsv(Reader reader) throws IOException {
    Set<LocalDate> holidays = new HashSet<>();
    Set<LocalDate> specials = new HashSet<>();
    Set<Integer> covered = new HashSet<>();

    BufferedReader in = new BufferedReader(reader);
    String line;
    int lineNo = 0;
    while ((line = in.readLine()) != null) {
      lineNo++;
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      try {
        String[] parts = trimmed.split(",", 3);
        String type = parts[0].trim().toUpperCase();
        if (parts.length < 2) {
          throw new IllegalArgumentException("expected TYPE,VALUE[,description]");
        }
        String value = parts[1].trim();
        switch (type) {
          case "COVERS" -> covered.add(Integer.parseInt(value));
          case "HOLIDAY" -> holidays.add(LocalDate.parse(value));
          case "SPECIAL" -> specials.add(LocalDate.parse(value));
          default -> throw new IllegalArgumentException("unknown row type '" + type + "'");
        }
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("calendar line " + lineNo + ": " + e.getMessage(), e);
      }
    }

    for (LocalDate d : concat(holidays, specials)) {
      if (!covered.contains(d.getYear())) {
        throw new IllegalArgumentException(
            "calendar has an entry on "
                + d
                + " but no COVERS,"
                + d.getYear()
                + " row; a partly filled year is not allowed");
      }
    }
    return new TradingCalendar(holidays, specials, covered);
  }

  private static List<LocalDate> concat(Set<LocalDate> a, Set<LocalDate> b) {
    List<LocalDate> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }
}
