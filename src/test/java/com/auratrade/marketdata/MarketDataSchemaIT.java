package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.platform.time.MarketZones;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks that V3 applies and that its constraints reject bad rows. Synthetic data only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MarketDataSchemaIT extends PostgresIntegrationTest {

  private static final Instant INGESTED = Instant.parse("2026-01-24T00:00:00Z");

  @Autowired JdbcClient jdbc;

  private long instrumentId;

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate candle, instrument_alias, instrument_provider_key, instrument"
                + " restart identity")
        .update();
    instrumentId = insertInstrument("TESTCO", InstrumentLoaderIT.isin(1));
  }

  private long insertInstrument(String symbol, String isin) {
    return jdbc.sql(
            """
            insert into instrument (exchange, trading_symbol, series, segment, instrument_type,
                                    name, isin, tick_size, lot_size, first_seen_on, last_seen_on)
            values ('NSE', :symbol, 'EQ', 'EQ', 'EQ', 'Test Company', :isin, 0.01, 1,
                    :seen, :seen)
            returning id
            """)
        .param("symbol", symbol)
        .param("isin", isin)
        .param("seen", LocalDate.parse("2026-01-23"))
        .query(Long.class)
        .single();
  }

  private int insertCandle(String interval, Instant ts, String o, String h, String l, String c) {
    return jdbc.sql(
            """
            insert into candle (instrument_id, bar_interval, ts, open, high, low, close, volume,
                                source, ingested_at)
            values (:id, :interval, :ts, :o, :h, :l, :c, 100, 'SYNTHETIC', :ingested)
            """)
        .param("id", instrumentId)
        .param("interval", interval)
        .param("ts", Timestamp.from(ts))
        .param("o", new BigDecimal(o))
        .param("h", new BigDecimal(h))
        .param("l", new BigDecimal(l))
        .param("c", new BigDecimal(c))
        .param("ingested", Timestamp.from(INGESTED))
        .update();
  }

  private static Instant istMidnight(String date) {
    return LocalDate.parse(date).atStartOfDay(MarketZones.IST).toInstant();
  }

  private static Instant ist(String dateTime) {
    return LocalDateTime.parse(dateTime).atZone(MarketZones.IST).toInstant();
  }

  @Test
  void storesADailyCandleAndKeepsPricesExact() {
    insertCandle("DAY", istMidnight("2026-01-23"), "100.05", "101.10", "99.95", "100.75");

    BigDecimal close =
        jdbc.sql("select close from candle where instrument_id = :id")
            .param("id", instrumentId)
            .query(BigDecimal.class)
            .single();
    assertThat(close).isEqualByComparingTo("100.75");
  }

  @Test
  void rejectsADuplicateCandle() {
    insertCandle("DAY", istMidnight("2026-01-23"), "100", "101", "99", "100");

    assertThatThrownBy(
            () -> insertCandle("DAY", istMidnight("2026-01-23"), "100", "101", "99", "100"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsInconsistentOrNonPositivePrices() {
    assertThatThrownBy(
            () -> insertCandle("DAY", istMidnight("2026-01-23"), "100", "99", "95", "98"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_candle_ohlc");
    assertThatThrownBy(() -> insertCandle("DAY", istMidnight("2026-01-27"), "0", "10", "0", "5"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_candle_positive_prices");
  }

  @Test
  void dailyCandlesMustSitAtIstMidnightButIntradayNeedNot() {
    assertThatThrownBy(() ->
            insertCandle("DAY", Instant.parse("2026-01-23T00:00:00Z"), "100", "101", "99", "100"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_candle_day_at_ist_midnight");

    assertThat(insertCandle("MINUTE_5", ist("2026-01-23T09:15:00"), "100", "101", "99", "100"))
        .isEqualTo(1);
  }

  @Test
  void rejectsAnUnknownInterval() {
    assertThatThrownBy(
            () -> insertCandle("WEEK", istMidnight("2026-01-23"), "100", "101", "99", "100"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void intervalCheckMatchesTheJavaEnum() {
    for (Interval interval : Interval.values()) {
      Instant ts = interval == Interval.DAY ? istMidnight("2026-01-27") : ist("2026-01-27T10:00");
      assertThat(insertCandle(interval.name(), ts, "100", "101", "99", "100")).isEqualTo(1);
    }
  }

  @Test
  void isinsAreUniquePerExchangeButSymbolsNeedNotBe() {
    assertThatThrownBy(() -> insertInstrument("OTHER", InstrumentLoaderIT.isin(1)))
        .isInstanceOf(DataIntegrityViolationException.class);
    // A share and its warrants can share a symbol; the ISIN tells them apart.
    assertThat(insertInstrument("TESTCO", InstrumentLoaderIT.isin(2))).isPositive();
  }

  @Test
  void rejectsAMalformedIsin() {
    assertThatThrownBy(() -> insertInstrument("BAD", "not-an-isin"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
