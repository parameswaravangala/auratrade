package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The provider-neutral loader against a real Postgres, fed by an in-memory feed of made-up
 * instruments (never real exchange data).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InstrumentLoaderIT extends PostgresIntegrationTest {

  private static final InstrumentLoader.Rules RULES = new InstrumentLoader.Rules("NSE", 2, 0.10, 3);

  @Autowired InstrumentRepository repository;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  void clean() {
    jdbc.sql(
            "truncate candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument"
                + " restart identity")
        .update();
  }

  /** A valid made-up ISIN for a number, so each test share has its own identity. */
  static String isin(int n) {
    String first11 = "INE%06dA01".formatted(n).substring(0, 11);
    return first11 + Isin.checkDigit(first11);
  }

  static Instrument share(int n, String symbol, String name) {
    return new Instrument(
        "NSE",
        Instrument.SEGMENT_EQ,
        symbol,
        "EQ",
        name,
        isin(n),
        (long) n,
        new BigDecimal("0.05"),
        1,
        "TEST|" + isin(n));
  }

  static Instrument index(String symbol) {
    return new Instrument(
        "NSE",
        Instrument.SEGMENT_INDEX,
        symbol,
        "INDEX",
        symbol,
        null,
        null,
        BigDecimal.ZERO,
        0,
        "TEST|" + symbol);
  }

  /** A loader that reads {@code rows} at noon UTC on {@code date} (17:30 IST, the same date). */
  private InstrumentLoader loader(String date, List<Instrument> rows) {
    InstrumentFeed feed =
        new InstrumentFeed() {
          @Override
          public String provider() {
            return "TEST";
          }

          @Override
          public List<Instrument> fetch() {
            return rows;
          }
        };
    return new InstrumentLoader(
        feed,
        repository,
        new TransactionTemplate(transactions),
        Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC),
        RULES);
  }

  private String symbolOf(String isin) {
    return jdbc.sql("select trading_symbol from instrument where isin = :isin")
        .param("isin", isin)
        .query(String.class)
        .single();
  }

  private LocalDate seen(String column, String isin) {
    return jdbc.sql("select " + column + " from instrument where isin = :isin")
        .param("isin", isin)
        .query(LocalDate.class)
        .single();
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  void firstLoadInsertsSecuritiesIndicesAndProviderKeys() {
    var result =
        loader("2026-11-26", List.of(share(1, "ALPHA", "Alpha"), index("TEST INDEX 50"))).load();

    assertThat(result.provider()).isEqualTo("TEST");
    assertThat(result.added()).isEqualTo(2);
    assertThat(count("instrument")).isEqualTo(2);
    assertThat(count("instrument_provider_key")).isEqualTo(2);
  }

  @Test
  void aSymbolChangeUpdatesTheSameInstrumentAndIsReported() {
    loader("2026-11-25", List.of(share(1, "OLDNAME", "Co"), share(2, "BETA", "Beta"))).load();

    var result =
        loader("2026-11-26", List.of(share(1, "NEWNAME", "Co"), share(2, "BETA", "Beta"))).load();

    assertThat(result.renamed()).containsExactly("OLDNAME -> NEWNAME (" + isin(1) + ")");
    assertThat(result.added()).isZero();
    assertThat(symbolOf(isin(1))).isEqualTo("NEWNAME");
    assertThat(seen("first_seen_on", isin(1))).isEqualTo(LocalDate.parse("2026-11-25"));
    assertThat(count("instrument")).isEqualTo(2);
  }

  @Test
  void instrumentsMissingFromTheFileStayAsInactive() {
    loader(
            "2026-11-25",
            List.of(share(1, "ALPHA", "A"), share(2, "BETA", "B"), share(3, "GAMMA", "G")))
        .load();

    var result =
        loader(
                "2026-11-26",
                List.of(share(1, "ALPHA", "A"), share(2, "BETA", "B"), share(4, "DELTA", "D")))
            .load();

    assertThat(result.notInFile()).isEqualTo(1);
    assertThat(seen("last_seen_on", isin(3))).isEqualTo(LocalDate.parse("2026-11-25"));
    assertThat(seen("last_seen_on", isin(1))).isEqualTo(LocalDate.parse("2026-11-26"));
  }

  @Test
  void aShareAndItsWarrantMayShareASymbolInDifferentSeries() {
    Instrument warrant =
        new Instrument(
            "NSE",
            Instrument.SEGMENT_EQ,
            "ALPHA",
            "W1",
            "Alpha warrants",
            isin(9),
            9L,
            new BigDecimal("0.05"),
            1,
            "TEST|" + isin(9));

    var result = loader("2026-11-26", List.of(share(1, "ALPHA", "Alpha"), warrant)).load();

    assertThat(result.added()).isEqualTo(2);
  }

  @Test
  void refusesAFileThatShrankTooMuchAndStoresNothing() {
    List<Instrument> big = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      big.add(share(i, "SYM" + i, "Co " + i));
    }
    loader("2026-11-25", big).load();

    assertThatThrownBy(() ->
            loader("2026-11-26", List.of(share(1, "CHANGED", "x"), share(2, "SYM2", "x"))).load())
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("refusing a drop");
    assertThat(symbolOf(isin(1))).isEqualTo("SYM1");
  }

  private static Instrument badIsin(int n) {
    return new Instrument(
        "NSE", "EQ", "BAD" + n, "EQ", "Bad", "INE000A01005", 1L, BigDecimal.ONE, 1, "TEST|bad" + n);
  }

  @Test
  void aRowWithABadIsinIsRejectedAndReportedWhileTheRestLoads() {
    var result =
        loader("2026-11-26", List.of(share(1, "A", "A"), share(2, "B", "B"), badIsin(1))).load();

    assertThat(result.rows()).isEqualTo(2);
    assertThat(result.rejected()).singleElement().asString().contains("BAD1", "invalid ISIN");
    assertThat(count("instrument")).isEqualTo(2);
  }

  @Test
  void tooManyRejectedRowsFailTheWholeLoad() {
    List<Instrument> rows = new ArrayList<>(List.of(share(1, "A", "A"), share(2, "B", "B")));
    for (int i = 1; i <= RULES.maxRejected() + 1; i++) {
      rows.add(badIsin(i));
    }

    assertThatThrownBy(() -> loader("2026-11-26", rows).load())
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("rows rejected");
    assertThat(count("instrument")).isZero();
  }

  @Test
  void refusesDuplicatesTooFewRowsAndOtherExchanges() {
    Instrument otherExchange =
        new Instrument(
            "BSE", "EQ", "B", "EQ", "B", isin(2), 2L, BigDecimal.ONE, 1, "TEST|" + isin(2));

    assertThatThrownBy(
            () -> loader("2026-11-26", List.of(share(1, "A", "A"), share(1, "B", "B"))).load())
        .hasMessageContaining("appears twice");
    assertThatThrownBy(() -> loader("2026-11-26", List.of(share(1, "A", "A"))).load())
        .hasMessageContaining("at least 2");
    assertThatThrownBy(
            () -> loader("2026-11-26", List.of(share(1, "A", "A"), otherExchange)).load())
        .hasMessageContaining("expected NSE");
    assertThat(count("instrument")).isZero();
  }

  @Test
  void reloadingOnTheSameDayChangesNothingButTheData() {
    loader("2026-11-26", List.of(share(1, "ALPHA", "A"), share(2, "BETA", "B"))).load();

    var again = loader("2026-11-26", List.of(share(1, "ALPHA", "A"), share(2, "BETA", "B"))).load();

    assertThat(again.added()).isZero();
    assertThat(again.updated()).isEqualTo(2);
    assertThat(count("instrument_provider_key")).isEqualTo(2);
  }
}
