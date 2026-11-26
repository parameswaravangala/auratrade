package com.auratrade.marketdata;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** backfill_request and backfill_item (V8). Plain JDBC. */
class BackfillRepository {

  record Request(
      long id,
      String source,
      Interval interval,
      LocalDate from,
      LocalDate to,
      String series,
      String status,
      Instant createdAt,
      Instant finishedAt) {}

  record Item(long instrumentId, String tradingSymbol, String providerKey, int attempts) {}

  /** Item counts by status for one request. */
  record Progress(long pending, long done, long failed, long candlesStored) {}

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  BackfillRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  long createRequest(
      String source,
      Interval interval,
      LocalDate from,
      LocalDate to,
      String series,
      List<CandleRepository.Target> universe,
      Instant now) {
    long id =
        jdbc.sql(
                """
                insert into backfill_request (source, bar_interval, date_from, date_to, series,
                                              status, created_at)
                values (:source, :interval, :from, :to, :series, 'PENDING', :now)
                returning id
                """)
            .param("source", source)
            .param("interval", interval.name())
            .param("from", from)
            .param("to", to)
            .param("series", series)
            .param("now", Timestamp.from(now))
            .query(Long.class)
            .single();
    SqlParameterSource[] items =
        universe.stream()
            .map(t ->
                new MapSqlParameterSource()
                    .addValue("req", id)
                    .addValue("inst", t.instrumentId())
                    .addValue("symbol", t.tradingSymbol())
                    .addValue("key", t.providerKey())
                    .addValue("now", Timestamp.from(now)))
            .toArray(SqlParameterSource[]::new);
    batch.batchUpdate(
        """
        insert into backfill_item (request_id, instrument_id, trading_symbol, provider_key, status,
                                   updated_at)
        values (:req, :inst, :symbol, :key, 'PENDING', :now)
        """,
        items);
    return id;
  }

  Optional<Request> openRequest(String source, Interval interval) {
    return requests("where status = 'PENDING' and source = :source and bar_interval = :interval")
        .param("source", source)
        .param("interval", interval.name())
        .query(BackfillRepository::request)
        .list()
        .stream()
        .findFirst();
  }

  /** The oldest request that still has work. */
  Optional<Request> nextOpenRequest(String source) {
    return requests("where status = 'PENDING' and source = :source")
        .param("source", source)
        .query(BackfillRepository::request)
        .list()
        .stream()
        .findFirst();
  }

  List<Request> recentRequests(int limit) {
    return jdbc.sql(
            "select id, source, bar_interval, date_from, date_to, series, status, created_at,"
                + " finished_at from backfill_request order by id desc limit :limit")
        .param("limit", limit)
        .query(BackfillRepository::request)
        .list();
  }

  private JdbcClient.StatementSpec requests(String where) {
    return jdbc.sql(
        "select id, source, bar_interval, date_from, date_to, series, status, created_at,"
            + " finished_at from backfill_request "
            + where
            + " order by id");
  }

  private static Request request(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Request(
        rs.getLong(1),
        rs.getString(2),
        Interval.valueOf(rs.getString(3)),
        rs.getObject(4, LocalDate.class),
        rs.getObject(5, LocalDate.class),
        rs.getString(6),
        rs.getString(7),
        rs.getTimestamp(8).toInstant(),
        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant());
  }

  List<Item> pendingItems(long requestId, int limit) {
    return jdbc.sql(
            """
            select instrument_id, trading_symbol, provider_key, attempts from backfill_item
            where request_id = :req and status = 'PENDING'
            order by trading_symbol limit :limit
            """)
        .param("req", requestId)
        .param("limit", limit)
        .query((rs, n) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)))
        .list();
  }

  void markItem(
      long requestId,
      long instrumentId,
      String status,
      int attempts,
      int stored,
      String error,
      Instant now) {
    jdbc.sql(
            """
            update backfill_item set status = :status, attempts = :attempts,
                   candles_stored = :stored, last_error = :error, updated_at = :now
            where request_id = :req and instrument_id = :inst
            """)
        .param("status", status)
        .param("attempts", attempts)
        .param("stored", stored)
        .param("error", error)
        .param("now", Timestamp.from(now))
        .param("req", requestId)
        .param("inst", instrumentId)
        .update();
  }

  Progress progress(long requestId) {
    return jdbc.sql(
            """
            select count(*) filter (where status = 'PENDING'),
                   count(*) filter (where status = 'DONE'),
                   count(*) filter (where status = 'FAILED'),
                   coalesce(sum(candles_stored), 0)
            from backfill_item where request_id = :req
            """)
        .param("req", requestId)
        .query((rs, n) -> new Progress(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
        .single();
  }

  void finishRequest(long requestId, String status, Instant now) {
    jdbc.sql("update backfill_request set status = :status, finished_at = :now where id = :req")
        .param("status", status)
        .param("now", Timestamp.from(now))
        .param("req", requestId)
        .update();
  }

  List<String> failedSymbols(long requestId, int limit) {
    return jdbc.sql(
            """
            select trading_symbol || ': ' || coalesce(last_error, '?') from backfill_item
            where request_id = :req and status = 'FAILED' order by trading_symbol limit :limit
            """)
        .param("req", requestId)
        .param("limit", limit)
        .query(String.class)
        .list();
  }
}
