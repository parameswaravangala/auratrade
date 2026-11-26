package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Ingestion against a real Postgres with a fake provider: synthetic candles over the fixture
 * calendar, never real exchange data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CandleIngestionServiceIT extends PostgresIntegrationTest {

  @Autowired CandleRepository candles;
  @Autowired JdbcClient jdbc;

  private final TradingCalendar calendar = TestCalendars.fixture();
  private final CandleValidator validator = new CandleValidator(calendar, QualityRules.defaults());
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);

  /** Returns whatever the test sets, under a given provider name. */
  static final class FakeProvider implements MarketDataProvider {
    private final String name;
    List<Candle> next = List.of();

    FakeProvider(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Set<Interval> supportedIntervals() {
      return EnumSet.of(Interval.DAY);
    }

    @Override
    public List<Candle> candles(
        String providerKey, Interval interval, LocalDate from, LocalDate to) {
      return next;
    }
  }

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument"
                + " restart identity")
        .update();
    long id =
        jdbc.sql(
                """
                insert into instrument (exchange, segment, trading_symbol, series, instrument_type,
                                        name, isin, tick_size, lot_size, first_seen_on,
                                        last_seen_on)
                values ('NSE', 'EQ', 'ACMECOOK', 'EQ', 'EQ', 'ACME COOKERS', :isin, 0.05, 1,
                        '2026-11-26', '2026-11-26')
                returning id
                """)
            .param("isin", InstrumentLoaderIT.isin(1))
            .query(Long.class)
            .single();
    for (String provider : List.of("ALPHA", "BETA")) {
      jdbc.sql(
              """
              insert into instrument_provider_key (provider, provider_key, instrument_id,
                                                   first_seen_on, last_seen_on)
              values (:p, :k, :id, '2026-11-26', '2026-11-26')
              """)
          .param("p", provider)
          .param("k", provider + "|ACME")
          .param("id", id)
          .update();
    }
  }

  private CandleIngestionService service(FakeProvider provider) {
    return new CandleIngestionService(provider, candles, validator, clock);
  }

  private static Candle day(String date, String close) {
    BigDecimal c = new BigDecimal(close);
    return Candle.daily(
        LocalDate.parse(date), c, c.add(BigDecimal.ONE), c.subtract(BigDecimal.ONE), c, 100);
  }

  private long storedCount() {
    return jdbc.sql("select count(*) from candle").query(Long.class).single();
  }

  @Test
  void validCandlesAreStoredUnderTheProviderAsSource() {
    var alpha = new FakeProvider("ALPHA");
    alpha.next = SyntheticCandles.daily(calendar, LocalDate.parse("2026-01-05"), 10, 7L);

    var result =
        service(alpha)
            .ingest(
                "ACMECOOK",
                Interval.DAY,
                LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-16"));

    assertThat(result.stored()).isEqualTo(10);
    assertThat(result.issues()).isEmpty();
    assertThat(
            service(alpha)
                .stored(
                    "ACMECOOK",
                    Interval.DAY,
                    LocalDate.parse("2026-01-05"),
                    LocalDate.parse("2026-01-16")))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(alpha.next); // same values; the database stores 4 decimal places
  }

  @Test
  void twoProvidersKeepSeparateHistoriesForTheSameDays() {
    var alpha = new FakeProvider("ALPHA");
    var beta = new FakeProvider("BETA");
    alpha.next = List.of(day("2026-01-23", "100.00"));
    beta.next = List.of(day("2026-01-23", "100.05")); // slightly different close

    service(alpha)
        .ingest(
            "ACMECOOK", Interval.DAY, LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-23"));
    service(beta)
        .ingest(
            "ACMECOOK", Interval.DAY, LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-23"));

    assertThat(storedCount()).isEqualTo(2);
    LocalDate d = LocalDate.parse("2026-01-23");
    assertThat(service(alpha).stored("ACMECOOK", Interval.DAY, d, d).get(0).close())
        .isEqualByComparingTo("100.00");
    assertThat(service(beta).stored("ACMECOOK", Interval.DAY, d, d).get(0).close())
        .isEqualByComparingTo("100.05");
  }

  @Test
  void anyValidationErrorStoresNothing() {
    var alpha = new FakeProvider("ALPHA");
    alpha.next = List.of(day("2026-01-23", "100"), day("2026-01-26", "100")); // 26th: holiday

    var result =
        service(alpha)
            .ingest(
                "ACMECOOK",
                Interval.DAY,
                LocalDate.parse("2026-01-23"),
                LocalDate.parse("2026-01-26"));

    assertThat(result.stored()).isZero();
    assertThat(result.issues()).extracting(QualityIssue::code).contains("NOT_TRADING_DAY");
    assertThat(storedCount()).isZero();
  }

  @Test
  void reingestingTheSameDaysUpdatesInPlace() {
    var alpha = new FakeProvider("ALPHA");
    alpha.next = List.of(day("2026-01-23", "100"));
    service(alpha)
        .ingest(
            "ACMECOOK", Interval.DAY, LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-23"));
    alpha.next = List.of(day("2026-01-23", "101"));

    service(alpha)
        .ingest(
            "ACMECOOK", Interval.DAY, LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-23"));

    assertThat(storedCount()).isEqualTo(1);
  }

  @Test
  void unknownSharesAndUncoveredYearsAreRefused() {
    var alpha = new FakeProvider("ALPHA");
    alpha.next = List.of(day("2027-01-04", "100"));

    assertThatThrownBy(() ->
            service(alpha)
                .ingest(
                    "NOSUCH",
                    Interval.DAY,
                    LocalDate.parse("2026-01-23"),
                    LocalDate.parse("2026-01-23")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            service(alpha)
                .ingest(
                    "ACMECOOK",
                    Interval.DAY,
                    LocalDate.parse("2027-01-04"),
                    LocalDate.parse("2027-01-04")))
        .isInstanceOf(CalendarNotCoveredException.class);
    assertThat(storedCount()).isZero();
  }
}
