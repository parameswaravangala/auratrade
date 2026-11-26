package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.marketdata.Resolution.MatchKind;
import com.auratrade.marketdata.Resolution.Resolved;
import com.auratrade.marketdata.Resolution.Unresolved;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The resolver against a real Postgres, with made-up instruments only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SymbolResolverServiceIT extends PostgresIntegrationTest {

  @Autowired SymbolResolverService service;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void setUp() {
    jdbc.sql("truncate candle, instrument_alias, instrument restart identity").update();
    insert("ACMECOOK", "EQ", "ACME COOKERS", "2026-11-26");
    insert("ACMEINAV", "EQ", "ACMEAMC - ACMEINAV", "2026-11-26");
    insert("ACMEBOND-N0", "N0", null, "2026-11-26");
    insert("OLDCO", "EQ", "OLD COMPANY", "2026-11-25"); // not in the latest load
    service.refresh();
  }

  private void insert(String symbol, String series, String name, String seen) {
    jdbc.sql(
            """
            insert into instrument (exchange, trading_symbol, series, segment, instrument_type,
                                    name, tick_size, lot_size, first_seen_on, last_seen_on)
            values ('NSE', :symbol, :series, 'NSE', 'EQ', :name, 0.01, 1, :seen, :seen)
            """)
        .param("symbol", symbol)
        .param("series", series)
        .param("name", name)
        .param("seen", LocalDate.parse(seen))
        .update();
  }

  @Test
  void onlyActiveCompanySharesAreCandidates() {
    assertThat(service.refresh().size()).isEqualTo(1);
    assertThat(service.resolve("Acme Cookers Ltd")).isInstanceOf(Resolved.class);
    assertThat(service.resolve("ACMEINAV")).isInstanceOf(Unresolved.class);
    assertThat(service.resolve("ACMEBOND-N0")).isInstanceOf(Unresolved.class);
    assertThat(service.resolve("Old Company")).isInstanceOf(Unresolved.class);
  }

  @Test
  void anAliasResolvesAfterItIsAdded() {
    assertThat(service.resolve("Shiny Kitchens")).isInstanceOf(Unresolved.class);

    service.addAlias("Shiny Kitchens", "ACMECOOK", "brand name used in news");

    var r = (Resolved) service.resolve("shiny kitchens");
    assertThat(r.share().tradingSymbol()).isEqualTo("ACMECOOK");
    assertThat(r.kind()).isEqualTo(MatchKind.ALIAS);
  }

  @Test
  void aliasesMustPointAtAnActiveCompanyShare() {
    assertThatThrownBy(() -> service.addAlias("Old", "OLDCO", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an active");
    assertThatThrownBy(() -> service.addAlias("Bond", "ACMEBOND-N0", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.addAlias("...", "ACMECOOK", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
