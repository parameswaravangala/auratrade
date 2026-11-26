package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Read side for analysis (screens, later backtests): the most recent candles of many instruments,
 * handed over one instrument at a time. Instruments are read in chunks, because the PostgreSQL
 * driver holds a whole result set in memory; chunking keeps memory bounded for thousands of shares.
 */
public class CandleSeriesReader {

  /** One instrument's candles, oldest first, ending on or before the as-of date. */
  public record Series(long instrumentId, String symbol, String name, List<Candle> candles) {

    public LocalDate lastDate() {
      return candles.isEmpty() ? null : candles.get(candles.size() - 1).tradingDate();
    }
  }

  private static final String EXCHANGE = "NSE";
  private static final int CHUNK = 200;

  private final JdbcClient jdbc;

  CandleSeriesReader(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The newest candle date from this source, if any candles exist. */
  public Optional<LocalDate> latestDate(String source, Interval interval) {
    return jdbc.sql(
            "select max(ts) from candle where source = :source and bar_interval = :interval")
        .param("source", source)
        .param("interval", interval.name())
        .query(Timestamp.class)
        .optional()
        .map(t -> t.toInstant().atZone(MarketZones.IST).toLocalDate());
  }

  /**
   * Hands over the last {@code lookback} candles up to {@code asOf} for every active company share
   * in {@code series}, one instrument at a time.
   */
  public void forEachShare(
      String source,
      Interval interval,
      LocalDate asOf,
      int lookback,
      List<String> series,
      Consumer<Series> consumer) {
    List<Long> ids =
        jdbc.sql(
                """
                select i.id from instrument i
                where i.exchange = :exchange and i.isin is not null and i.series in (:series)
                  and i.last_seen_on =
                      (select max(last_seen_on) from instrument where exchange = :exchange)
                order by i.id
                """)
            .param("exchange", EXCHANGE)
            .param("series", series)
            .query(Long.class)
            .list();
    for (int from = 0; from < ids.size(); from += CHUNK) {
      List<Long> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
      SeriesCollector collector = new SeriesCollector(consumer);
      jdbc.sql(
              """
              with recent as (
                select c.*, row_number() over (partition by c.instrument_id order by c.ts desc) rn
                from candle c
                where c.instrument_id in (:ids) and c.source = :source
                  and c.bar_interval = :interval and c.ts < :until
              )
              select i.id, i.trading_symbol, i.name, r.ts, r.open, r.high, r.low, r.close, r.volume
              from recent r join instrument i on i.id = r.instrument_id
              where r.rn <= :lookback
              order by i.id, r.ts
              """)
          .param("ids", chunk)
          .param("source", source)
          .param("interval", interval.name())
          .param("until", until(asOf))
          .param("lookback", lookback)
          .query(collector::accept);
      collector.finish();
    }
  }

  /** The last {@code lookback} candles of an index (for example NIFTY) up to {@code asOf}. */
  public Optional<Series> index(
      String source, Interval interval, String indexSymbol, LocalDate asOf, int lookback) {
    List<Series> found = new ArrayList<>();
    SeriesCollector collector = new SeriesCollector(found::add);
    jdbc.sql(
            """
            select i.id, i.trading_symbol, i.name, c.ts, c.open, c.high, c.low, c.close, c.volume
            from instrument i
            join lateral (
              select * from candle c
              where c.instrument_id = i.id and c.source = :source and c.bar_interval = :interval
                and c.ts < :until
              order by c.ts desc limit :lookback
            ) c on true
            where i.exchange = :exchange and i.segment = 'INDEX' and i.trading_symbol = :symbol
            order by c.ts
            """)
        .param("source", source)
        .param("interval", interval.name())
        .param("until", until(asOf))
        .param("lookback", lookback)
        .param("exchange", EXCHANGE)
        .param("symbol", indexSymbol)
        .query(collector::accept);
    collector.finish();
    return found.stream().findFirst();
  }

  private static Timestamp until(LocalDate asOf) {
    return Timestamp.from(asOf.plusDays(1).atStartOfDay(MarketZones.IST).toInstant());
  }

  /** Groups consecutive rows of one instrument into a Series; call finish() after the rows. */
  private static final class SeriesCollector {
    private final Consumer<Series> consumer;
    private long currentId = -1;
    private String symbol;
    private String name;
    private List<Candle> candles = new ArrayList<>();

    SeriesCollector(Consumer<Series> consumer) {
      this.consumer = consumer;
    }

    void accept(ResultSet rs) throws SQLException {
      long id = rs.getLong(1);
      if (id != currentId) {
        finish();
        currentId = id;
        symbol = rs.getString(2);
        name = rs.getString(3);
        candles = new ArrayList<>();
      }
      candles.add(
          new Candle(
              rs.getTimestamp(4).toInstant(),
              rs.getBigDecimal(5),
              rs.getBigDecimal(6),
              rs.getBigDecimal(7),
              rs.getBigDecimal(8),
              rs.getLong(9)));
    }

    void finish() {
      if (currentId >= 0 && !candles.isEmpty()) {
        consumer.accept(new Series(currentId, symbol, name, List.copyOf(candles)));
      }
      currentId = -1;
      candles = new ArrayList<>();
    }
  }
}
