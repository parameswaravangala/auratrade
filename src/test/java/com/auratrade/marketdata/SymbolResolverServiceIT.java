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
    jdbc.sql(
            "truncate paper_review, paper_equity, paper_trade, paper_position, paper_order, paper_account,"
                + " candle, backfill_item, backfill_request, instrument_alias,"
                + " instrument_provider_key, instrument"
                + " restart identity")
        .update();
    insert("ACMECOOK", "EQ", "ACME COOKERS", InstrumentLoaderIT.isin(1), "2026-11-26");
    insert("NOISIN", "EQ", "NO ISIN COMPANY", null, "2026-11-26"); // legacy row
    insert("ACMEBOND", "N0", null, InstrumentLoaderIT.isin(2), "2026-11-26");
    insert("OLDCO", "EQ", "OLD COMPANY", InstrumentLoaderIT.isin(3), "2026-11-25"); // inactive
    service.refresh();
  }

  private void insert(String symbol, String series, String name, String isin, String seen) {
    jdbc.sql(
            """
            insert into instrument (exchange, trading_symbol, series, segment, instrument_type,
                                    name, isin, tick_size, lot_size, first_seen_on, last_seen_on)
            values ('NSE', :symbol, :series, 'EQ', :series, :name, :isin, 0.01, 1, :seen, :seen)
            """)
        .param("symbol", symbol)
        .param("series", series)
        .param("name", name)
        .param("isin", isin)
        .param("seen", LocalDate.parse(seen))
        .update();
  }

  @Test
  void onlyActiveCompanySharesAreCandidates() {
    assertThat(service.refresh().size()).isEqualTo(1);
    assertThat(service.resolve("Acme Cookers Ltd")).isInstanceOf(Resolved.class);
    assertThat(service.resolve("NOISIN")).isInstanceOf(Unresolved.class);
    assertThat(service.resolve("ACMEBOND")).isInstanceOf(Unresolved.class);
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
    assertThatThrownBy(() -> service.addAlias("Bond", "ACMEBOND", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.addAlias("...", "ACMECOOK", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
