package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Made-up records in the Upstox instrument-file shape. Not exchange data. */
class UpstoxInstrumentParserTest {

  static final String SHARE_ISIN = "INE000A01004";

  private static List<Instrument> parse(String json) {
    return UpstoxInstrumentParser.parse(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void keepsSharesAndIndicesAndSkipsOtherSegments() {
    var rows =
        parse(
            """
            [
             {"segment":"NSE_EQ","name":"ACME COOKERS LTD","exchange":"NSE","isin":"%s",
              "instrument_type":"EQ","instrument_key":"NSE_EQ|%s","lot_size":1,
              "freeze_quantity":100000.0,"exchange_token":"101","tick_size":5.0,
              "trading_symbol":"ACMECOOK","qty_multiplier":1.0,"security_type":"NORMAL"},
             {"segment":"NSE_INDEX","name":"Test Index 50","exchange":"NSE",
              "instrument_type":"INDEX","instrument_key":"NSE_INDEX|Test Index 50",
              "exchange_token":"26000","trading_symbol":"TEST INDEX 50"},
             {"segment":"NSE_FO","name":"ACME","exchange":"NSE","instrument_type":"FUT",
              "instrument_key":"NSE_FO|1","trading_symbol":"ACME FUT"}
            ]
            """
                .formatted(SHARE_ISIN, SHARE_ISIN));

    assertThat(rows).hasSize(2);
    Instrument share = rows.get(0);
    assertThat(share.segment()).isEqualTo(Instrument.SEGMENT_EQ);
    assertThat(share.series()).isEqualTo("EQ");
    assertThat(share.isin()).isEqualTo(SHARE_ISIN);
    assertThat(share.tickSize()).isEqualByComparingTo("0.05"); // 5 paise
    assertThat(share.exchangeToken()).isEqualTo(101L);
    assertThat(share.providerKey()).isEqualTo("NSE_EQ|" + SHARE_ISIN);
    Instrument index = rows.get(1);
    assertThat(index.isIndex()).isTrue();
    assertThat(index.series()).isEqualTo("INDEX");
    assertThat(index.isin()).isNull();
    assertThat(index.tickSize()).isEqualByComparingTo("0");
  }

  @Test
  void aSecurityWithoutIsinFailsTheFile() {
    assertThatThrownBy(() ->
            parse(
                """
                [{"segment":"NSE_EQ","exchange":"NSE","instrument_type":"EQ",
                  "instrument_key":"NSE_EQ|X","trading_symbol":"X","tick_size":1.0}]
                """))
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("without ISIN");
  }

  @Test
  void malformedJsonFailsTheFile() {
    assertThatThrownBy(() -> parse("[{\"segment\":\"NSE_EQ\","))
        .isInstanceOf(InstrumentFileException.class);
    assertThatThrownBy(() -> parse("{\"not\":\"an array\"}"))
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("array");
  }
}
