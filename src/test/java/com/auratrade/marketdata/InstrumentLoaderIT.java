package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Loads made-up instrument files (never real exchange data) into a real Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InstrumentLoaderIT extends PostgresIntegrationTest {

  private static final String HEADER = InstrumentCsvParserTest.HEADER;
  private static final InstrumentLoader.Rules RULES = new InstrumentLoader.Rules("NSE", 2, 0.10);

  @Autowired InstrumentRepository repository;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  void clean() {
    jdbc.sql("truncate candle, instrument_alias, instrument restart identity").update();
  }

  /** A loader that reads {@code csv} at noon UTC on {@code date} (17:30 IST, the same date). */
  private InstrumentLoader loader(String date, String csv) {
    Clock clock = Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC);
    return new InstrumentLoader(
        () -> new StringReader(HEADER + csv),
        repository,
        new TransactionTemplate(transactions),
        clock,
        RULES);
  }

  private static String row(long token, String symbol, String name) {
    return token + ",1," + symbol + ",\"" + name + "\",0,,0,0.05,1,EQ,NSE,NSE\n";
  }

  private String nameOf(String symbol) {
    return jdbc.sql("select name from instrument where trading_symbol = :s")
        .param("s", symbol)
        .query(String.class)
        .single();
  }

  private LocalDate seen(String column, String symbol) {
    return jdbc.sql("select " + column + " from instrument where trading_symbol = :s")
        .param("s", symbol)
        .query(LocalDate.class)
        .single();
  }

  @Test
  void firstLoadInsertsEverything() {
    var result = loader("2026-11-26", row(1, "ALPHA", "Alpha") + row(2, "BETA-SM", "Beta")).load();

    assertThat(result.rows()).isEqualTo(2);
    assertThat(result.added()).isEqualTo(2);
    assertThat(result.seenOn()).isEqualTo(LocalDate.parse("2026-11-26"));
    assertThat(repository.seriesCounts("NSE"))
        .extracting(InstrumentRepository.SeriesCount::series)
        .containsExactlyInAnyOrder("EQ", "SM");
  }

  @Test
  void laterLoadsUpdateRowsAndKeepMissingOnesAsInactive() {
    loader("2026-11-25", row(1, "ALPHA", "Alpha") + row(2, "BETA", "Beta") + row(3, "GAMMA", "G"))
        .load();

    var result =
        loader(
                "2026-11-26",
                row(1, "ALPHA", "Alpha Renamed") + row(2, "BETA", "Beta") + row(4, "DELTA", "D"))
            .load();

    assertThat(result.added()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(2);
    assertThat(result.notInFile()).isEqualTo(1);
    assertThat(nameOf("ALPHA")).isEqualTo("Alpha Renamed");
    assertThat(seen("first_seen_on", "ALPHA")).isEqualTo(LocalDate.parse("2026-11-25"));
    assertThat(seen("last_seen_on", "ALPHA")).isEqualTo(LocalDate.parse("2026-11-26"));
    assertThat(seen("last_seen_on", "GAMMA")).isEqualTo(LocalDate.parse("2026-11-25"));
  }

  @Test
  void reportsATokenThatMovedToAnotherSymbol() {
    loader("2026-11-25", row(1, "OLDNAME", "Co") + row(2, "BETA", "Beta")).load();

    var result = loader("2026-11-26", row(1, "NEWNAME", "Co") + row(2, "BETA", "Beta")).load();

    assertThat(result.tokenMoves()).containsExactly("OLDNAME -> NEWNAME");
  }

  @Test
  void refusesAFileThatShrankTooMuchAndStoresNothing() {
    String big = "";
    for (int i = 1; i <= 20; i++) {
      big += row(i, "SYM" + i, "Co " + i);
    }
    loader("2026-11-25", big).load();

    assertThatThrownBy(
            () -> loader("2026-11-26", row(1, "SYM1", "Changed") + row(2, "SYM2", "x")).load())
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("refusing a drop");
    assertThat(nameOf("SYM1")).isEqualTo("Co 1");
  }

  @Test
  void refusesDuplicateTokensTooFewRowsAndOtherExchanges() {
    assertThatThrownBy(() -> loader("2026-11-26", row(1, "A", "A") + row(1, "B", "B")).load())
        .hasMessageContaining("appears twice");
    assertThatThrownBy(() -> loader("2026-11-26", row(1, "A", "A")).load())
        .hasMessageContaining("at least 2");
    assertThatThrownBy(() ->
            loader("2026-11-26", row(1, "A", "A") + "2,1,B,\"B\",0,,0,0.05,1,EQ,BSE,BSE\n").load())
        .hasMessageContaining("expected NSE");
    assertThat(jdbc.sql("select count(*) from instrument").query(Long.class).single()).isZero();
  }

  @Test
  void reloadingOnTheSameDayChangesNothingButTheData() {
    loader("2026-11-26", row(1, "ALPHA", "Alpha") + row(2, "BETA", "Beta")).load();

    var again = loader("2026-11-26", row(1, "ALPHA", "Alpha") + row(2, "BETA", "Beta")).load();

    assertThat(again.added()).isZero();
    assertThat(again.updated()).isEqualTo(2);
    assertThat(again.notInFile()).isZero();
  }
}
