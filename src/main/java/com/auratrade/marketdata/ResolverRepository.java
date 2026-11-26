package com.auratrade.marketdata;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the shares the resolver may return and the curated aliases (ADR 0014). */
class ResolverRepository {

  /**
   * Company shares only (backlog B-024): equity series, not ETF iNAV rows, not rights entitlements,
   * and active, meaning present in the latest instrument load.
   */
  private static final String ACTIVE_SHARES =
      """
      from instrument i
      where i.exchange = :exchange
        and i.series in ('EQ', 'BE', 'SM', 'ST', 'BZ')
        and i.trading_symbol not like '%INAV'
        and i.trading_symbol !~ '-RE[0-9]*(-|$)'
        and i.last_seen_on = (select max(last_seen_on) from instrument where exchange = :exchange)
      """;

  private final JdbcClient jdbc;

  ResolverRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<ShareRef> activeShares(String exchange) {
    return jdbc.sql("select i.id, i.trading_symbol, i.name, i.series " + ACTIVE_SHARES)
        .param("exchange", exchange)
        .query((rs, n) ->
            new ShareRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
        .list();
  }

  Optional<Long> activeShareId(String exchange, String tradingSymbol) {
    return jdbc.sql("select i.id " + ACTIVE_SHARES + " and i.trading_symbol = :symbol")
        .param("exchange", exchange)
        .param("symbol", tradingSymbol)
        .query(Long.class)
        .optional();
  }

  Map<String, Long> aliases() {
    Map<String, Long> out = new HashMap<>();
    jdbc.sql("select alias_key, instrument_id from instrument_alias")
        .query(rs -> {
          out.put(rs.getString(1), rs.getLong(2));
        });
    return out;
  }

  /** Inserts or repoints an alias. */
  void saveAlias(String key, String alias, long instrumentId, String note, Instant now) {
    jdbc.sql(
            """
            insert into instrument_alias (alias_key, alias, instrument_id, note, created_at)
            values (:key, :alias, :id, :note, :now)
            on conflict (alias_key) do update set
              alias = excluded.alias,
              instrument_id = excluded.instrument_id,
              note = excluded.note,
              created_at = excluded.created_at
            """)
        .param("key", key)
        .param("alias", alias)
        .param("id", instrumentId)
        .param("note", note)
        .param("now", Timestamp.from(now))
        .update();
  }
}
