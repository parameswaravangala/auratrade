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

  /**
   * Adds quote snapshots, three keys per call. Script per key: "ok" (a bar for {@code session}),
   * "none" (left out), "old" (a bar for the session before), "bad" (fails validation).
   */
  static final class SnapshotProvider implements MarketDataProvider, SessionBarProvider {
    final ScriptedProvider history = new ScriptedProvider();
    final java.util.Map<String, String> snapshot = new java.util.HashMap<>();
    final List<List<String>> snapshotCalls = new java.util.ArrayList<>();
    final List<String> historyCalls = new java.util.ArrayList<>();
    LocalDate session;
    LocalDate before;
    LocalDate after;

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
      historyCalls.add(key + " " + from + ".." + to);
      return history.candles(key, interval, from, to);
    }

    @Override
    public int maxKeysPerCall() {
      return 3;
    }

    @Override
    public java.util.Map<String, Candle> latestSessionBars(List<String> keys) {
      snapshotCalls.add(List.copyOf(keys));
      java.util.Map<String, Candle> out = new java.util.HashMap<>();
      BigDecimal p = new BigDecimal("100");
      for (String k : keys) {
        switch (snapshot.getOrDefault(k, "ok")) {
          case "none" -> {}
          case "old" -> out.put(k, Candle.daily(before, p, p, p, p, 10));
          case "next" -> out.put(k, Candle.daily(after, p, p, p, p, 10));
          case "bad" ->
              out.put(
                  k, Candle.daily(session, p, new BigDecimal("90"), new BigDecimal("99"), p, 10));
          default ->
              out.put(
                  k, Candle.daily(session, p, new BigDecimal("101"), new BigDecimal("99"), p, 10));
        }
      }
      return out;
    }
  }

  private SnapshotProvider snapshots(LocalDate session) {
    var s = new SnapshotProvider();
    s.session = session;
    s.before = calendar.previousTradingDay(session);
    s.after = calendar.nextTradingDay(session);
    return s;
  }

  @Test
  void withSnapshotsTheLastSessionTakesAFewCallsAndThePreviousIsCorrectedLater() {
    addNiftyIndex();
    var s = snapshots(FRIDAY.to());

    var outcome = job(s, 0.9, List.of("NIFTY")).execute(FRIDAY);

    LocalDate thursday = calendar.previousTradingDay(FRIDAY.to());
    // Only NIFTY's previous session is re-read from history; shares get a correction backfill.
    assertThat(s.historyCalls).containsExactly("KN " + thursday + ".." + thursday);
    assertThat(s.snapshotCalls).hasSize(3); // NIFTY, then 4 shares in calls of 3 and 1
    assertThat(stored()).isEqualTo(6); // NIFTY Thursday (history) and Friday, 4 shares Friday
    assertThat(outcome.ok()).isEqualTo(4);
    assertThat(outcome.detail()).contains("4 with a candle for 2026-01-23 (quotes)");
    assertThat(corrected).containsExactly(calendar.previousTradingDay(FRIDAY.to()));
  }

  @Test
  void aSnapshotBarForAnotherSessionIsNotStored() {
    var s = snapshots(FRIDAY.to());
    s.snapshot.put("K1", "old"); // did not trade on Friday
    s.snapshot.put("K2", "none");
    s.snapshot.put("K3", "bad");

    var outcome = job(s, 0.25, List.of()).execute(FRIDAY);

    assertThat(stored()).isEqualTo(1);
    assertThat(outcome.failed()).isEqualTo(1);
    assertThat(outcome.detail()).contains("SHARE3 (validation)");
  }

  @Test
  void aCatchUpTakesEarlierDaysFromHistoryAndTheLastFromSnapshots() {
    LocalDate thursday = calendar.previousTradingDay(FRIDAY.to());
    var s = snapshots(FRIDAY.to());

    job(s, 0.9, List.of()).execute(new JobWindow(thursday, FRIDAY.to()));

    assertThat(corrected).isEmpty(); // history has just read Thursday

    assertThat(s.historyCalls)
        .hasSize(4)
        .allMatch(c -> c.endsWith(calendar.previousTradingDay(thursday) + ".." + thursday));
    assertThat(s.snapshotCalls)
        .hasSize(4); // a probe of the 4 shares, then the 4 shares (3 + 1 each)
    assertThat(stored()).isEqualTo(8);
  }

  @Test
  void tooFewSnapshotBarsFailTheRunAndNothingIsCorrected() {
    var s = snapshots(FRIDAY.to());
    s.snapshot.put("K1", "old");
    s.snapshot.put("K2", "old");

    assertThatThrownBy(() -> job(s, 0.9, List.of()).execute(FRIDAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("only 2 of 4");
    assertThat(corrected).isEmpty();
  }

  @Test
  void afterTheNextOpenTheRunFallsBackToHistory() {
    addNiftyIndex();
    var s = snapshots(FRIDAY.to());
    s.snapshot.put("KN", "next"); // the snapshot already holds Monday's unfinished session

    var outcome = job(s, 0.9, List.of("NIFTY")).execute(FRIDAY);

    assertThat(s.snapshotCalls).hasSize(1); // only the probe
    assertThat(s.historyCalls)
        .hasSize(5)
        .allMatch(c -> c.endsWith(calendar.previousTradingDay(FRIDAY.to()) + ".." + FRIDAY.to()));
    assertThat(stored()).isEqualTo(5);
    assertThat(outcome.detail()).doesNotContain("quotes");
    assertThat(corrected).isEmpty();
  }

  @Test
  void withoutBenchmarksAFewSharesDetectTheNextOpen() {
    var s = snapshots(FRIDAY.to());
    for (int n = 1; n <= 4; n++) {
      s.snapshot.put("K" + n, "next");
    }

    job(s, 0.9, List.of()).execute(FRIDAY);

    assertThat(s.snapshotCalls).hasSize(2); // only the probe, 4 shares in calls of 3 and 1
    assertThat(s.historyCalls).hasSize(4);
    assertThat(stored()).isEqualTo(4);
  }

  @Test
  void aBenchmarkMissingFromTheSnapshotFailsTheRun() {
    addNiftyIndex();
    var s = snapshots(FRIDAY.to());
    s.snapshot.put("KN", "none");

    assertThatThrownBy(() -> job(s, 0.5, List.of("NIFTY")).execute(FRIDAY))
        .hasMessageContaining("benchmark NIFTY has no candle for 2026-01-23");
    assertThat(stored()).isZero();
  }

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
    return job(minCoverage, List.of());
  }

  private void addNiftyIndex() {
    long id =
        jdbc.sql(
                """
                insert into instrument (exchange, segment, trading_symbol, series,
                                        instrument_type, name, isin, tick_size, lot_size,
                                        first_seen_on, last_seen_on)
                values ('NSE', 'INDEX', 'NIFTY', 'INDEX', 'INDEX', 'Nifty 50', null, 0.05, 1,
                        '2026-01-23', '2026-01-23')
                returning id
                """)
            .query(Long.class)
            .single();
    jdbc.sql(
            """
            insert into instrument_provider_key (provider, provider_key, instrument_id,
                                                 first_seen_on, last_seen_on)
            values ('FAKE', 'KN', :id, '2026-01-23', '2026-01-23')
            """)
        .param("id", id)
        .update();
  }

  @Test
  void theBenchmarkIsLoadedWithTheShares() {
    addNiftyIndex();

    job(0.5, List.of("NIFTY")).execute(FRIDAY);

    assertThat(stored()).isEqualTo(5);
  }

  @Test
  void aBenchmarkWithoutTheDayFailsTheRunBeforeAnyShareIsFetched() {
    addNiftyIndex();
    provider.script.put("KN", "none");

    assertThatThrownBy(() -> job(0.5, List.of("NIFTY")).execute(FRIDAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("benchmark NIFTY has no candle for 2026-01-23");
    assertThat(stored()).isZero();
  }

  private EodCandleJob job(double minCoverage, List<String> benchmarks) {
    return job(provider, minCoverage, benchmarks);
  }

  private final List<LocalDate> corrected = new java.util.ArrayList<>();

  private EodCandleJob job(MarketDataProvider active, double minCoverage, List<String> benchmarks) {
    var ingestion =
        new CandleIngestionService(
            active, candles, new CandleValidator(calendar, QualityRules.defaults()), clock);
    return new EodCandleJob(
        ingestion,
        calendar,
        clock,
        new EodCandleJob.Settings(
            LocalTime.of(19, 0), 30, List.of("EQ"), 0, minCoverage, benchmarks),
        session -> {
          corrected.add(session);
          return "correction for " + session;
        });
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
