package com.auratrade.marketdata;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Plain JDBC access to the instrument table (ADR 0013). */
public class InstrumentRepository {

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  InstrumentRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  /** Symbol to Kite token for every instrument on the exchange, active or not. */
  Map<String, Long> tokensBySymbol(String exchange) {
    Map<String, Long> out = new HashMap<>();
    jdbc.sql(
            "select trading_symbol, kite_instrument_token from instrument where exchange = :exchange")
        .param("exchange", exchange)
        .query(rs -> {
          long token = rs.getLong(2);
          out.put(rs.getString(1), rs.wasNull() ? null : token);
        });
    return out;
  }

  /** The most recent load date before {@code date}, if any. */
  Optional<LocalDate> lastLoadBefore(String exchange, LocalDate date) {
    return jdbc.sql(
            "select max(last_seen_on) from instrument where exchange = :exchange"
                + " and last_seen_on < :date")
        .param("exchange", exchange)
        .param("date", date)
        .query(LocalDate.class)
        .optional();
  }

  int countSeenOn(String exchange, LocalDate date) {
    return jdbc.sql(
            "select count(*) from instrument where exchange = :exchange and last_seen_on = :date")
        .param("exchange", exchange)
        .param("date", date)
        .query(Integer.class)
        .single();
  }

  /** Inserts new instruments and refreshes existing ones; first_seen_on is never changed. */
  void upsertAll(List<Instrument> instruments, LocalDate seenOn) {
    SqlParameterSource[] params =
        instruments.stream()
            .map(i ->
                new MapSqlParameterSource()
                    .addValue("exchange", i.exchange())
                    .addValue("symbol", i.tradingSymbol())
                    .addValue("series", i.series())
                    .addValue("segment", i.segment())
                    .addValue("type", i.instrumentType())
                    .addValue("name", i.name())
                    .addValue("token", i.kiteInstrumentToken())
                    .addValue("exchangeToken", i.exchangeToken())
                    .addValue("tick", i.tickSize())
                    .addValue("lot", i.lotSize())
                    .addValue("seen", seenOn))
            .toArray(SqlParameterSource[]::new);
    batch.batchUpdate(
        """
        insert into instrument (exchange, trading_symbol, series, segment, instrument_type, name,
                                kite_instrument_token, exchange_token, tick_size, lot_size,
                                first_seen_on, last_seen_on)
        values (:exchange, :symbol, :series, :segment, :type, :name, :token, :exchangeToken,
                :tick, :lot, :seen, :seen)
        on conflict (exchange, trading_symbol) do update set
          series = excluded.series,
          segment = excluded.segment,
          instrument_type = excluded.instrument_type,
          name = excluded.name,
          kite_instrument_token = excluded.kite_instrument_token,
          exchange_token = excluded.exchange_token,
          tick_size = excluded.tick_size,
          lot_size = excluded.lot_size,
          last_seen_on = greatest(instrument.last_seen_on, excluded.last_seen_on)
        """,
        params);
  }

  /** Instrument counts per series, split into active (seen on the latest load) and inactive. */
  public List<SeriesCount> seriesCounts(String exchange) {
    return jdbc.sql(
            """
            select series,
                   count(*) filter (where last_seen_on = latest.d) as active,
                   count(*) filter (where last_seen_on < latest.d) as inactive
            from instrument,
                 (select max(last_seen_on) as d from instrument where exchange = :exchange) latest
            where exchange = :exchange
            group by series
            order by active desc, series
            """)
        .param("exchange", exchange)
        .query(SeriesCount.class)
        .list();
  }

  public record SeriesCount(String series, long active, long inactive) {}
}
