package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Made-up instruments and candles; checks lookback, as-of cut-off, filters and the index read. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CandleSeriesReaderIT extends PostgresIntegrationTest {

  @Autowired CandleRepository candles;
  @Autowired CandleSeriesReader reader;
  @Autowired JdbcClient jdbc;

  private long insert(String symbol, String segment, String series, String isin, String seen) {
    return jdbc.sql(
            """
            insert into instrument (exchange, segment, trading_symbol, series, instrument_type,
                                    name, isin, tick_size, lot_size, first_seen_on, last_seen_on)
            values ('NSE', :segment, :symbol, :series, :series, :symbol, :isin, 0.05, 1,
                    :seen, :seen)
            returning id
            """)
        .param("segment", segment)
        .param("symbol", symbol)
        .param("series", series)
        .param("isin", isin)
        .param("seen", LocalDate.parse(seen))
        .query(Long.class)
        .single();
  }

  private void days(long id, int count) {
    List<Candle> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      BigDecimal p = BigDecimal.valueOf(100 + i);
      list.add(Candle.daily(LocalDate.parse("2026-01-01").plusDays(i), p, p, p, p, 10));
    }
    candles.upsert(id, Interval.DAY, "TESTSRC", list, Instant.parse("2026-11-26T00:00:00Z"));
  }

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument restart identity")
        .update();
    days(insert("ALPHA", "EQ", "EQ", InstrumentLoaderIT.isin(1), "2026-11-26"), 10);
    days(insert("BETA", "EQ", "EQ", InstrumentLoaderIT.isin(2), "2026-11-26"), 3);
    days(insert("BOND", "EQ", "N0", InstrumentLoaderIT.isin(3), "2026-11-26"), 10);
    days(insert("GONE", "EQ", "EQ", InstrumentLoaderIT.isin(4), "2026-11-20"), 10);
    days(insert("NIFTY", "INDEX", "INDEX", null, "2026-11-26"), 10);
  }

  @Test
  void returnsTheLastNCandlesUpToAsOfForActiveSharesOnly() {
    List<CandleSeriesReader.Series> seen = new ArrayList<>();

    reader.forEachShare(
        "TESTSRC", Interval.DAY, LocalDate.parse("2026-01-08"), 5, List.of("EQ"), seen::add);

    assertThat(seen).extracting(CandleSeriesReader.Series::symbol).containsExactly("ALPHA", "BETA");
    var alpha = seen.get(0);
    assertThat(alpha.candles()).hasSize(5);
    assertThat(alpha.lastDate()).isEqualTo(LocalDate.parse("2026-01-08"));
    assertThat(alpha.candles().get(0).tradingDate()).isEqualTo(LocalDate.parse("2026-01-04"));
    assertThat(seen.get(1).candles()).hasSize(3); // BETA has only three days
  }

  @Test
  void readsAnIndexAndTheLatestDate() {
    var nifty = reader.index("TESTSRC", Interval.DAY, "NIFTY", LocalDate.parse("2026-01-10"), 4);

    assertThat(nifty).isPresent();
    assertThat(nifty.get().candles()).hasSize(4);
    assertThat(reader.latestDate("TESTSRC", Interval.DAY)).contains(LocalDate.parse("2026-01-10"));
    assertThat(reader.latestDate("NOSUCH", Interval.DAY)).isEmpty();
  }
}
