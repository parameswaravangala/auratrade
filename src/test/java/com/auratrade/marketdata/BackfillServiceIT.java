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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Backfill against a real Postgres, made-up shares and a scripted provider over the fixture
 * calendar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BackfillServiceIT extends PostgresIntegrationTest {

  @Autowired CandleRepository candles;
  @Autowired JdbcClient jdbc;
  @Autowired NamedParameterJdbcTemplate batch;

  private final TradingCalendar calendar = TestCalendars.fixture();
  private final Clock clock = Clock.fixed(Instant.parse("2026-02-02T06:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate FROM = LocalDate.parse("2026-01-19");
  private static final LocalDate TO = LocalDate.parse("2026-01-23");

  /** Valid daily candles for every trading day asked for, unless scripted otherwise per key. */
  final class ScriptedProvider implements MarketDataProvider {
    final Map<String, RuntimeException> fail = new HashMap<>();
    final Set<String> bad = new java.util.HashSet<>();
    int calls;

    @Override
    public String name() {
      return "FAKE";
    }

    @Override
    public Set<Interval> supportedIntervals() {
      return EnumSet.of(Interval.DAY);
    }

    @Override
    public List<Candle> candles(String key, Interval interval, LocalDate from, LocalDate to) {
      calls++;
      if (fail.containsKey(key)) {
        throw fail.get(key);
      }
      BigDecimal p = new BigDecimal("100");
      BigDecimal high = bad.contains(key) ? new BigDecimal("90") : new BigDecimal("101");
      return calendar.tradingDaysBetween(from, to).stream()
          .map(d -> Candle.daily(d, p, high, new BigDecimal("99"), p, 10))
          .toList();
    }
  }

  private final ScriptedProvider provider = new ScriptedProvider();
  private BackfillService service;

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument restart identity")
        .update();
    for (int n = 1; n <= 3; n++) {
      long id =
          jdbc.sql(
                  """
                  insert into instrument (exchange, segment, trading_symbol, series,
                                          instrument_type, name, isin, tick_size, lot_size,
                                          first_seen_on, last_seen_on)
                  values ('NSE', 'EQ', :symbol, 'EQ', 'EQ', :symbol, :isin, 0.05, 1,
                          '2026-02-01', '2026-02-01')
                  returning id
                  """)
              .param("symbol", "SHARE" + n)
              .param("isin", InstrumentLoaderIT.isin(n))
              .query(Long.class)
              .single();
      jdbc.sql(
              """
              insert into instrument_provider_key (provider, provider_key, instrument_id,
                                                   first_seen_on, last_seen_on)
              values ('FAKE', :key, :id, '2026-02-01', '2026-02-01')
              """)
          .param("key", "K" + n)
          .param("id", id)
          .update();
    }
    var ingestion =
        new CandleIngestionService(
            provider, candles, new CandleValidator(calendar, QualityRules.defaults()), clock);
    service =
        new BackfillService(new BackfillRepository(jdbc, batch), ingestion, calendar, clock, 2);
  }

  private long storedCandles() {
    return jdbc.sql("select count(*) from candle").query(Long.class).single();
  }

  @Test
  void aRequestIsWorkedThroughInBatchesAndFinishes() {
    var created = service.create(FROM, TO, List.of("EQ"));
    assertThat(created.pending()).isEqualTo(3);

    var first = service.runBatch(2).orElseThrow();
    assertThat(first.done()).isEqualTo(2);
    assertThat(first.pending()).isEqualTo(1);

    var second = service.runBatch(2).orElseThrow();
    assertThat(second.pending()).isZero();
    assertThat(service.status(created.id()).orElseThrow().status()).isEqualTo("DONE");
    // 2026-01-19..23: Mon-Fri, all trading days in the fixture.
    assertThat(storedCandles()).isEqualTo(3 * 5);
    assertThat(service.runBatch(2)).isEmpty();
  }

  @Test
  void transientErrorsAreRetriedThenFailedAndValidationFailuresAreNotRetried() {
    provider.fail.put("K1", new ProviderException.BadResponse("FAKE", "timeout"));
    provider.bad.add("K2");
    var created = service.create(FROM, TO, List.of("EQ"));

    service.runBatch(10); // K1 attempt 1 (retrying), K2 failed (validation), K3 done
    service.runBatch(10); // K1 attempt 2 -> failed (max 2)

    var status = service.status(created.id()).orElseThrow();
    assertThat(status.status()).isEqualTo("DONE_WITH_FAILURES");
    assertThat(status.done()).isEqualTo(1);
    assertThat(status.failed()).isEqualTo(2);
    assertThat(status.firstFailures())
        .anyMatch(s -> s.startsWith("SHARE2: validation: OHLC_INCONSISTENT"))
        .anyMatch(s -> s.startsWith("SHARE1:"));
  }

  @Test
  void anAuthFailureStopsTheBatchAndLeavesItemsPendingForLater() {
    provider.fail.put("K1", new ProviderException.Auth("FAKE", "token expired"));
    var created = service.create(FROM, TO, List.of("EQ"));

    assertThatThrownBy(() -> service.runBatch(10)).isInstanceOf(ProviderException.Auth.class);
    assertThat(service.status(created.id()).orElseThrow().pending()).isEqualTo(3);

    provider.fail.clear(); // token renewed
    service.runBatch(10);
    assertThat(service.status(created.id()).orElseThrow().status()).isEqualTo("DONE");
  }

  @Test
  void refusesUncoveredYearsOpenDuplicatesAndRangesReachingToday() {
    assertThatThrownBy(() -> service.create(LocalDate.parse("2025-12-01"), TO, List.of("EQ")))
        .isInstanceOf(CalendarNotCoveredException.class);
    assertThatThrownBy(() -> service.create(FROM, LocalDate.parse("2026-02-02"), List.of("EQ")))
        .hasMessageContaining("before today");

    service.create(FROM, TO, List.of("EQ"));
    assertThatThrownBy(() -> service.create(FROM, TO, List.of("EQ")))
        .hasMessageContaining("still running");
  }

  @Test
  void aCancelledRequestIsNotWorkedOn() {
    var created = service.create(FROM, TO, List.of("EQ"));

    service.cancel(created.id());

    assertThat(service.runBatch(10)).isEmpty();
    assertThat(provider.calls).isZero();
  }
}
