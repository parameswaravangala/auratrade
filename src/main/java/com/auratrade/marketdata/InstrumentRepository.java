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

/**
 * Plain JDBC access to the instrument master (ADR 0013, 0015). Securities are identified by
 * (exchange, isin), indices by (exchange, segment, trading_symbol). Provider keys are kept in
 * instrument_provider_key.
 */
public class InstrumentRepository {

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  InstrumentRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  /** Identity (see {@link InstrumentLoader#identity}) to current symbol, active or not. */
  Map<String, String> symbolsByIdentity(String exchange) {
    Map<String, String> out = new HashMap<>();
    jdbc.sql(
            """
            select case when isin is not null then 'ISIN|' || isin
                        else 'INDEX|' || trading_symbol end,
                   trading_symbol
            from instrument
            where exchange = :exchange and (isin is not null or segment = 'INDEX')
            """)
        .param("exchange", exchange)
        .query(rs -> {
          out.put(rs.getString(1), rs.getString(2));
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

  /**
   * Inserts new instruments and refreshes existing ones (first_seen_on never changes), then records
   * each instrument's key at {@code provider}.
   */
  void upsertAll(List<Instrument> instruments, String provider, LocalDate seenOn) {
    SqlParameterSource[] securities = params(instruments, false, provider, seenOn);
    SqlParameterSource[] indices = params(instruments, true, provider, seenOn);

    batch.batchUpdate(
        """
        insert into instrument (exchange, segment, trading_symbol, series, instrument_type, name,
                                isin, exchange_token, tick_size, lot_size, first_seen_on,
                                last_seen_on)
        values (:exchange, :segment, :symbol, :series, :series, :name, :isin, :exchangeToken,
                :tick, :lot, :seen, :seen)
        on conflict (exchange, isin) where isin is not null do update set
          segment = excluded.segment,
          trading_symbol = excluded.trading_symbol,
          series = excluded.series,
          instrument_type = excluded.instrument_type,
          name = excluded.name,
          exchange_token = excluded.exchange_token,
          tick_size = excluded.tick_size,
          lot_size = excluded.lot_size,
          last_seen_on = greatest(instrument.last_seen_on, excluded.last_seen_on)
        """,
        securities);
    batch.batchUpdate(
        """
        insert into instrument (exchange, segment, trading_symbol, series, instrument_type, name,
                                isin, exchange_token, tick_size, lot_size, first_seen_on,
                                last_seen_on)
        values (:exchange, :segment, :symbol, :series, :series, :name, null, :exchangeToken,
                :tick, :lot, :seen, :seen)
        on conflict (exchange, segment, trading_symbol) where isin is null do update set
          series = excluded.series,
          instrument_type = excluded.instrument_type,
          name = excluded.name,
          exchange_token = excluded.exchange_token,
          tick_size = excluded.tick_size,
          lot_size = excluded.lot_size,
          last_seen_on = greatest(instrument.last_seen_on, excluded.last_seen_on)
        """,
        indices);

    SqlParameterSource[] all = params(instruments, null, provider, seenOn);
    batch.batchUpdate(
        """
        insert into instrument_provider_key (provider, provider_key, instrument_id,
                                             first_seen_on, last_seen_on)
        select :provider, :providerKey, i.id, :seen, :seen
        from instrument i
        where i.exchange = :exchange
          and (i.isin = cast(:isin as text)
               or (cast(:isin as text) is null and i.isin is null
                   and i.segment = :segment and i.trading_symbol = :symbol))
        on conflict (provider, provider_key) do update set
          instrument_id = excluded.instrument_id,
          last_seen_on = greatest(instrument_provider_key.last_seen_on, excluded.last_seen_on)
        """,
        all);
  }

  /**
   * @param indices true for indices only, false for securities only, null for all
   */
  private static SqlParameterSource[] params(
      List<Instrument> instruments, Boolean indices, String provider, LocalDate seenOn) {
    return instruments.stream()
        .filter(i -> indices == null || i.isIndex() == indices)
        .map(i ->
            new MapSqlParameterSource()
                .addValue("exchange", i.exchange())
                .addValue("segment", i.segment())
                .addValue("symbol", i.tradingSymbol())
                .addValue("series", i.series())
                .addValue("name", i.name())
                .addValue("isin", i.isin())
                .addValue("exchangeToken", i.exchangeToken())
                .addValue("tick", i.tickSize())
                .addValue("lot", i.lotSize())
                .addValue("provider", provider)
                .addValue("providerKey", i.providerKey())
                .addValue("seen", seenOn))
        .toArray(SqlParameterSource[]::new);
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
