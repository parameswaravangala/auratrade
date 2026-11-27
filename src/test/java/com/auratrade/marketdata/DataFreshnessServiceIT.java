package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.platform.time.MarketZones;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Freshness over a real Postgres, made-up shares and the fixture calendar (2026 only). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataFreshnessServiceIT extends PostgresIntegrationTest {

  @Autowired CandleRepository candles;
  @Autowired JdbcClient jdbc;

  private final TradingCalendar calendar = TestCalendars.fixture();

  private long instrument(String symbol, String segment, String isin) {
    long id =
        jdbc.sql(
                """
                insert into instrument (exchange, segment, trading_symbol, series,
                                        instrument_type, name, isin, tick_size, lot_size,
                                        first_seen_on, last_seen_on)
                values ('NSE', :segment, :symbol, :series, :segment, :symbol, :isin, 0.05, 1,
                        '2026-01-23', '2026-01-23')
                returning id
                """)
            .param("segment", segment)
            .param("symbol", symbol)
            .param("series", segment.equals("INDEX") ? "INDEX" : "EQ")
            .param("isin", isin)
            .query(Long.class)
            .single();
    jdbc.sql(
            """
            insert into instrument_provider_key (provider, provider_key, instrument_id,
                                                 first_seen_on, last_seen_on)
            values ('FAKE', :key, :id, '2026-01-23', '2026-01-23')
            """)
        .param("key", "K-" + symbol)
        .param("id", id)
        .update();
    return id;
  }

  private void bar(long id, String date) {
    BigDecimal p = new BigDecimal("100");
    candles.upsert(
        id,
        Interval.DAY,
        "FAKE",
        List.of(Candle.daily(LocalDate.parse(date), p, p, p, p, 10)),
        java.time.Instant.parse("2026-01-23T00:00:00Z"));
  }

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate paper_equity, paper_trade, paper_position, paper_order, paper_account,"
                + " candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument restart identity")
        .update();
    bar(instrument("CURRENT", "EQ", InstrumentLoaderIT.isin(1)), "2026-01-23");
    bar(instrument("ONEBEHIND", "EQ", InstrumentLoaderIT.isin(2)), "2026-01-22");
    bar(instrument("FIVEBEHIND", "EQ", InstrumentLoaderIT.isin(3)), "2026-01-16");
    instrument("NODATA", "EQ", InstrumentLoaderIT.isin(4));
    bar(instrument("NIFTY", "INDEX", null), "2026-01-22");
  }

  private DataFreshnessService service(String istDateTime) {
    var clock =
        Clock.fixed(
            LocalDateTime.parse(istDateTime).atZone(MarketZones.IST).toInstant(), ZoneOffset.UTC);
    var ingestion =
        new CandleIngestionService(
            new EodCandleJobIT.ScriptedProvider(),
            candles,
            new CandleValidator(calendar, QualityRules.defaults()),
            clock);
    return new DataFreshnessService(
        ingestion, jdbc, calendar, clock, LocalTime.of(19, 0), List.of("EQ"), List.of("NIFTY"));
  }

  @Test
  void afterTheCutoffTodayIsExpectedAndSharesAreCountedInSessions() {
    var f = service("2026-01-23T20:00").freshness(); // Friday, after 19:00 IST

    assertThat(f.expectedSession()).isEqualTo(LocalDate.parse("2026-01-23"));
    assertThat(f.eodCutoff()).isEqualTo("19:00");
    assertThat(f.source()).isEqualTo("FAKE");
    assertThat(f.latestSession()).isEqualTo(LocalDate.parse("2026-01-23"));
    assertThat(f.sessionsBehind()).isZero();
    assertThat(f.shares().total()).isEqualTo(4);
    assertThat(f.shares().current()).isEqualTo(1);
    assertThat(f.shares().noData()).isEqualTo(1);
    assertThat(f.shares().behind()).containsEntry("1", 1).containsEntry("2-5", 1);
    assertThat(f.shares().behind()).containsEntry("6+", 0);
    // Mon 19 to Fri 23 January: five sessions after the 16th.
    assertThat(f.shares().mostBehind())
        .containsExactly(
            new DataFreshnessService.Lag("FIVEBEHIND", LocalDate.parse("2026-01-16"), 5),
            new DataFreshnessService.Lag("ONEBEHIND", LocalDate.parse("2026-01-22"), 1));
    assertThat(f.benchmarks())
        .containsExactly(new DataFreshnessService.Lag("NIFTY", LocalDate.parse("2026-01-22"), 1));
    assertThat(f.otherSources()).isEmpty();
  }

  @Test
  void beforeTheCutoffYesterdayIsExpected() {
    var f = service("2026-01-23T18:59").freshness();

    assertThat(f.expectedSession()).isEqualTo(LocalDate.parse("2026-01-22"));
    assertThat(f.shares().current()).isEqualTo(2); // the 23rd counts as current too
    assertThat(f.benchmarks().getFirst().sessionsBehind()).isZero();
  }

  @Test
  void aGapIntoAnUncoveredYearIsUnknownNotGuessed() {
    BigDecimal p = new BigDecimal("100");
    long old = instrument("OLD", "EQ", InstrumentLoaderIT.isin(5));
    candles.upsert(
        old,
        Interval.DAY,
        "FAKE",
        List.of(Candle.daily(LocalDate.parse("2025-12-30"), p, p, p, p, 10)),
        java.time.Instant.parse("2026-01-23T00:00:00Z"));

    var f = service("2026-01-23T20:00").freshness();

    assertThat(f.shares().mostBehind().getFirst())
        .isEqualTo(new DataFreshnessService.Lag("OLD", LocalDate.parse("2025-12-30"), null));
    assertThat(f.shares().behind()).containsEntry("6+", 1);
  }

  @Test
  void anExpectedSessionInAnUncoveredYearFailsClosed() {
    assertThatThrownBy(() -> service("2027-01-05T20:00").freshness())
        .isInstanceOf(CalendarNotCoveredException.class)
        .satisfies(e -> assertThat(((CalendarNotCoveredException) e).year()).isEqualTo(2027));
  }

  @Test
  void otherSourcesAreListedWithTheirNewestSession() {
    BigDecimal p = new BigDecimal("100");
    long id =
        jdbc.sql("select id from instrument where trading_symbol = 'CURRENT'")
            .query(Long.class)
            .single();
    candles.upsert(
        id,
        Interval.DAY,
        "OTHER",
        List.of(Candle.daily(LocalDate.parse("2026-01-20"), p, p, p, p, 10)),
        java.time.Instant.parse("2026-01-23T00:00:00Z"));

    assertThat(service("2026-01-23T20:00").freshness().otherSources())
        .containsExactly(
            new DataFreshnessService.OtherSource("OTHER", LocalDate.parse("2026-01-20")));
  }
}
