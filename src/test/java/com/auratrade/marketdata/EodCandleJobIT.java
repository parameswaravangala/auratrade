package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.platform.jobs.ScheduledJob.JobWindow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The EOD job over a real Postgres, made-up shares and a scripted fake provider. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EodCandleJobIT extends PostgresIntegrationTest {

  @Autowired CandleRepository candles;
  @Autowired JdbcClient jdbc;

  private final TradingCalendar calendar = TestCalendars.fixture();
  private final Clock clock = Clock.fixed(Instant.parse("2026-01-23T14:30:00Z"), ZoneOffset.UTC);
  private static final JobWindow FRIDAY =
      new JobWindow(LocalDate.parse("2026-01-23"), LocalDate.parse("2026-01-23"));

  /** Behaviour per provider key: a candle for the day, nothing, a bad candle, or an exception. */
  static final class ScriptedProvider implements MarketDataProvider {
    final java.util.Map<String, Object> script = new java.util.HashMap<>();

    @Override
    public String name() {
      return "FAKE";
    }

    @Override
    public Set<Interval> supportedIntervals() {
      return EnumSet.of(Interval.DAY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Candle> candles(String key, Interval interval, LocalDate from, LocalDate to) {
      Object s = script.getOrDefault(key, "ok");
      if (s instanceof RuntimeException e) {
        throw e;
      }
      if ("none".equals(s)) {
        return List.of();
      }
      BigDecimal p = new BigDecimal("100");
      BigDecimal high = "bad".equals(s) ? new BigDecimal("90") : new BigDecimal("101");
      return List.of(Candle.daily(to, p, high, new BigDecimal("99"), p, 10));
    }
  }

  private final ScriptedProvider provider = new ScriptedProvider();

  @BeforeEach
  void setUp() {
    jdbc.sql(
            "truncate paper_equity, paper_trade, paper_position, paper_order, paper_account,"
                + " candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument"
                + " restart identity")
        .update();
    for (int n = 1; n <= 4; n++) {
      long id =
          jdbc.sql(
                  """
                  insert into instrument (exchange, segment, trading_symbol, series,
                                          instrument_type, name, isin, tick_size, lot_size,
                                          first_seen_on, last_seen_on)
                  values ('NSE', 'EQ', :symbol, 'EQ', 'EQ', :symbol, :isin, 0.05, 1,
                          '2026-01-23', '2026-01-23')
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
              values ('FAKE', :key, :id, '2026-01-23', '2026-01-23')
              """)
          .param("key", "K" + n)
          .param("id", id)
          .update();
    }
  }

  private EodCandleJob job(double minCoverage) {
    var ingestion =
        new CandleIngestionService(
            provider, candles, new CandleValidator(calendar, QualityRules.defaults()), clock);
    return new EodCandleJob(
        ingestion,
        calendar,
        clock,
        new EodCandleJob.Settings(
            LocalTime.of(19, 0), 30, List.of("EQ"), 0, minCoverage, List.of()));
  }

  private long stored() {
    return jdbc.sql("select count(*) from candle where source = 'FAKE'").query(Long.class).single();
  }

  @Test
  void loadsEveryShareInTheUniverse() {
    var outcome = job(0.5).execute(FRIDAY);

    assertThat(outcome.ok()).isEqualTo(4);
    assertThat(outcome.failed()).isZero();
    assertThat(stored()).isEqualTo(4);
  }

  @Test
  void badSharesMakeThePartListAndTheRestIsStored() {
    provider.script.put("K2", "bad"); // fails validation
    provider.script.put("K3", new ProviderException.BadResponse("FAKE", "boom"));

    var outcome = job(0.5).execute(FRIDAY);

    assertThat(outcome.ok()).isEqualTo(2);
    assertThat(outcome.failed()).isEqualTo(2);
    assertThat(outcome.detail()).contains("SHARE2 (validation)", "SHARE3 (BadResponse)");
    assertThat(stored()).isEqualTo(2);
  }

  @Test
  void anAuthFailureFailsTheWholeRun() {
    provider.script.put("K1", new ProviderException.Auth("FAKE", "token expired"));

    assertThatThrownBy(() -> job(0.5).execute(FRIDAY)).isInstanceOf(ProviderException.Auth.class);
  }

  @Test
  void ifMostSharesHaveNoCandleYetTheRunFailsSoItIsRetried() {
    provider.script.put("K1", "none");
    provider.script.put("K2", "none");
    provider.script.put("K3", "none");

    assertThatThrownBy(() -> job(0.5).execute(FRIDAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not published yet");
  }

  @Test
  void plansFromTheCalendarAndTheCutoff() {
    // 14:30 UTC is 20:00 IST on Friday 2026-01-23, after the cutoff.
    assertThat(job(0.5).plan(java.util.Optional.of(LocalDate.parse("2026-01-22"))))
        .contains(FRIDAY);
  }
}
