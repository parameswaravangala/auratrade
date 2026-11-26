package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Candles per instrument, interval and source (ADR 0016). Plain JDBC. */
public class CandleRepository {

  /** An instrument and this provider's key for it. */
  public record Target(long instrumentId, String tradingSymbol, String providerKey) {}

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  CandleRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  /**
   * The active company share with this symbol and its key at {@code provider}. Empty if there is
   * none; more than one is an error (it would be ambiguous).
   */
  Optional<Target> shareTarget(String exchange, String tradingSymbol, String provider) {
    List<Target> found =
        jdbc.sql(
                """
                select i.id, i.trading_symbol, k.provider_key
                from instrument i
                join instrument_provider_key k
                  on k.instrument_id = i.id and k.provider = :provider
                where i.exchange = :exchange
                  and i.trading_symbol = :symbol
                  and i.isin is not null
                  and i.series in ('EQ', 'BE', 'SM', 'ST', 'BZ')
                  and i.last_seen_on =
                      (select max(last_seen_on) from instrument where exchange = :exchange)
                """)
            .param("provider", provider)
            .param("exchange", exchange)
            .param("symbol", tradingSymbol)
            .query((rs, n) -> new Target(rs.getLong(1), rs.getString(2), rs.getString(3)))
            .list();
    if (found.size() > 1) {
      throw new IllegalStateException(found.size() + " active shares match " + tradingSymbol);
    }
    return found.stream().findFirst();
  }

  /** Inserts candles or refreshes them; the key is instrument, interval, source and time. */
  void upsert(
      long instrumentId,
      Interval interval,
      String source,
      List<Candle> candles,
      Instant ingestedAt) {
    SqlParameterSource[] params =
        candles.stream()
            .map(c ->
                new MapSqlParameterSource()
                    .addValue("id", instrumentId)
                    .addValue("interval", interval.name())
                    .addValue("source", source)
                    .addValue("ts", Timestamp.from(c.ts()))
                    .addValue("o", c.open())
                    .addValue("h", c.high())
                    .addValue("l", c.low())
                    .addValue("c", c.close())
                    .addValue("v", c.volume())
                    .addValue("ingested", Timestamp.from(ingestedAt)))
            .toArray(SqlParameterSource[]::new);
    batch.batchUpdate(
        """
        insert into candle (instrument_id, bar_interval, source, ts, open, high, low, close, volume,
                            ingested_at)
        values (:id, :interval, :source, :ts, :o, :h, :l, :c, :v, :ingested)
        on conflict (instrument_id, bar_interval, source, ts) do update set
          open = excluded.open, high = excluded.high, low = excluded.low, close = excluded.close,
          volume = excluded.volume, ingested_at = excluded.ingested_at
        """,
        params);
  }

  /** Stored candles from one source between two IST trading dates, inclusive, oldest first. */
  public List<Candle> find(
      long instrumentId, Interval interval, String source, LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            select ts, open, high, low, close, volume
            from candle
            where instrument_id = :id and bar_interval = :interval and source = :source
              and ts >= :from and ts < :until
            order by ts
            """)
        .param("id", instrumentId)
        .param("interval", interval.name())
        .param("source", source)
        .param("from", Timestamp.from(from.atStartOfDay(MarketZones.IST).toInstant()))
        .param("until", Timestamp.from(to.plusDays(1).atStartOfDay(MarketZones.IST).toInstant()))
        .query((rs, n) ->
            new Candle(
                rs.getTimestamp(1).toInstant(),
                rs.getBigDecimal(2),
                rs.getBigDecimal(3),
                rs.getBigDecimal(4),
                rs.getBigDecimal(5),
                rs.getLong(6)))
        .list();
  }
}
