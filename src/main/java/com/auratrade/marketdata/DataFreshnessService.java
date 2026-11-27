package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * How fresh the daily candles are, in trading sessions, for GET /api/v1/marketdata/freshness (ADR
 * 0026). The expected session is the newest trading day whose EOD cutoff has passed; a share is
 * behind by the number of trading days after its newest candle up to that session. Uses the trading
 * calendar and fails closed: an uncovered year needed for the expected session raises {@link
 * CalendarNotCoveredException}; a gap reaching into an uncovered year is reported as unknown
 * (null), never guessed.
 */
public class DataFreshnessService {

  static final int MOST_BEHIND = 20;

  public record Freshness(
      Instant asOf,
      LocalDate expectedSession,
      String source,
      LocalDate latestSession,
      Integer sessionsBehind,
      Shares shares,
      List<Lag> benchmarks,
      List<OtherSource> otherSources) {}

  /**
   * @param behind counts keyed "1", "2-5" and "6+" (unknown gaps count as "6+")
   */
  public record Shares(
      int total, int current, Map<String, Integer> behind, int noData, List<Lag> mostBehind) {}

  /** A share or index and its gap; latestSession and sessionsBehind are null when unknown. */
  public record Lag(String symbol, LocalDate latestSession, Integer sessionsBehind) {}

  public record OtherSource(String source, LocalDate latestSession) {}

  /** The universe is empty: nothing to measure yet. */
  public static class NoInstrumentsException extends RuntimeException {
    NoInstrumentsException(String message) {
      super(message);
    }
  }

  private final CandleIngestionService ingestion;
  private final JdbcClient jdbc;
  private final TradingCalendar calendar;
  private final Clock clock;
  private final LocalTime cutoff;
  private final List<String> series;
  private final List<String> benchmarks;

  DataFreshnessService(
      CandleIngestionService ingestion,
      JdbcClient jdbc,
      TradingCalendar calendar,
      Clock clock,
      LocalTime cutoff,
      List<String> series,
      List<String> benchmarks) {
    this.ingestion = ingestion;
    this.jdbc = jdbc;
    this.calendar = calendar;
    this.clock = clock;
    this.cutoff = cutoff;
    this.series = List.copyOf(series);
    this.benchmarks = List.copyOf(benchmarks);
  }

  /** Newest trading day whose EOD cutoff (IST) has passed at {@code now}. */
  static LocalDate expectedSession(TradingCalendar calendar, Instant now, LocalTime cutoff) {
    ZonedDateTime ist = now.atZone(MarketZones.IST);
    LocalDate today = ist.toLocalDate();
    if (calendar.isTradingDay(today) && !ist.toLocalTime().isBefore(cutoff)) {
      return today;
    }
    return calendar.previousTradingDay(today);
  }

  public Freshness freshness() {
    Instant now = clock.instant();
    LocalDate expected = expectedSession(calendar, now, cutoff);
    String source = ingestion.providerName();
    List<CandleRepository.Target> universe = ingestion.universe(series, 0);
    if (universe.isEmpty()) {
      throw new NoInstrumentsException(
          "no active shares with a " + source + " key; load instruments first");
    }
    Map<Long, LocalDate> latest = latestSessions(source, universe);
    Map<LocalDate, Integer> gaps = new HashMap<>();

    int current = 0;
    int noData = 0;
    Map<String, Integer> behind = new LinkedHashMap<>();
    behind.put("1", 0);
    behind.put("2-5", 0);
    behind.put("6+", 0);
    List<Lag> lagging = new ArrayList<>();
    LocalDate newest = null;
    for (CandleRepository.Target t : universe) {
      LocalDate l = latest.get(t.instrumentId());
      if (l == null) {
        noData++;
        continue;
      }
      if (newest == null || l.isAfter(newest)) {
        newest = l;
      }
      Integer gap = gaps.computeIfAbsent(l, d -> gap(d, expected));
      if (gap != null && gap == 0) {
        current++;
        continue;
      }
      behind.merge(bucket(gap), 1, Integer::sum);
      lagging.add(new Lag(t.tradingSymbol(), l, gap));
    }
    lagging.sort(
        Comparator.comparing(
                (Lag g) -> g.sessionsBehind() == null ? Integer.MAX_VALUE : g.sessionsBehind())
            .reversed()
            .thenComparing(Lag::symbol));

    List<Lag> indexLags = new ArrayList<>();
    for (String symbol : benchmarks) {
      CandleRepository.Target index = ingestion.indexTarget(symbol);
      LocalDate l = latestSessions(source, List.of(index)).get(index.instrumentId());
      indexLags.add(new Lag(symbol, l, l == null ? null : gap(l, expected)));
    }

    return new Freshness(
        now,
        expected,
        source,
        newest,
        newest == null ? null : gap(newest, expected),
        new Shares(
            universe.size(),
            current,
            behind,
            noData,
            List.copyOf(lagging.subList(0, Math.min(MOST_BEHIND, lagging.size())))),
        indexLags,
        otherSources(source));
  }

  private Integer gap(LocalDate latest, LocalDate expected) {
    return sessionsBehind(calendar, latest, expected);
  }

  /** Trading days after {@code latest} up to {@code expected}; null if a year is not covered. */
  static Integer sessionsBehind(TradingCalendar calendar, LocalDate latest, LocalDate expected) {
    if (!latest.isBefore(expected)) {
      return 0;
    }
    try {
      return calendar.tradingDaysBetween(latest.plusDays(1), expected).size();
    } catch (CalendarNotCoveredException e) {
      return null;
    }
  }

  private static String bucket(Integer gap) {
    if (gap == null || gap >= 6) {
      return "6+";
    }
    return gap == 1 ? "1" : "2-5";
  }

  /**
   * Newest DAY candle per instrument: one index probe each (key instrument, interval, source, ts).
   */
  private Map<Long, LocalDate> latestSessions(
      String source, List<CandleRepository.Target> targets) {
    Map<Long, LocalDate> out = new HashMap<>();
    List<Long> ids = targets.stream().map(CandleRepository.Target::instrumentId).toList();
    for (int i = 0; i < ids.size(); i += 1000) {
      jdbc.sql(
              """
              select i.id,
                     (select max(c.ts) from candle c
                      where c.instrument_id = i.id and c.bar_interval = 'DAY'
                        and c.source = :source) as newest
              from instrument i where i.id in (:ids)
              """)
          .param("source", source)
          .param("ids", ids.subList(i, Math.min(i + 1000, ids.size())))
          .query((rs, n) -> {
            Timestamp ts = rs.getTimestamp(2);
            if (ts != null) {
              out.put(rs.getLong(1), ts.toInstant().atZone(MarketZones.IST).toLocalDate());
            }
            return null;
          })
          .list();
    }
    return out;
  }

  private List<OtherSource> otherSources(String source) {
    return jdbc.sql(
            """
            select source, max(ts) from candle
            where bar_interval = 'DAY' and source <> :source group by source order by source
            """)
        .param("source", source)
        .query((rs, n) ->
            new OtherSource(
                rs.getString(1),
                rs.getTimestamp(2).toInstant().atZone(MarketZones.IST).toLocalDate()))
        .list();
  }
}
