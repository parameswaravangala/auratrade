package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Made-up rows in the Kite format. Not exchange data. */
class InstrumentCsvParserTest {

  static final String HEADER =
      "instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,"
          + "lot_size,instrument_type,segment,exchange\n";

  private static List<Instrument> parse(String rows) throws Exception {
    return InstrumentCsvParser.parse(new StringReader(HEADER + rows));
  }

  @Test
  void parsesAShareAnIndexAndABond() throws Exception {
    var rows =
        parse(
            """
            1001,11,TESTCO,"TEST COMPANY, LTD",0,,0,0.05,1,EQ,NSE,NSE
            1002,12,TEST INDEX 50,"TEST INDEX 50",0,,0,0,0,EQ,INDICES,NSE
            1003,13,TESTBOND-N0,,0,,0,0.01,1,EQ,NSE,NSE
            """);

    assertThat(rows).hasSize(3);
    Instrument share = rows.get(0);
    assertThat(share.tradingSymbol()).isEqualTo("TESTCO");
    assertThat(share.name()).isEqualTo("TEST COMPANY, LTD"); // quoted comma kept
    assertThat(share.series()).isEqualTo("EQ");
    assertThat(share.tickSize()).isEqualByComparingTo("0.05");
    assertThat(share.kiteInstrumentToken()).isEqualTo(1001);
    assertThat(rows.get(1).series()).isEqualTo("INDEX");
    assertThat(rows.get(2).series()).isEqualTo("N0");
    assertThat(rows.get(2).name()).isNull();
  }

  @Test
  void seriesComesFromATwoCharacterSuffixOnly() {
    assertThat(InstrumentCsvParser.seriesOf("TESTCO-BE", "NSE")).isEqualTo("BE");
    assertThat(InstrumentCsvParser.seriesOf("TESTCO-SM", "NSE")).isEqualTo("SM");
    assertThat(InstrumentCsvParser.seriesOf("TEST-AUTO", "NSE")).isEqualTo("EQ");
    assertThat(InstrumentCsvParser.seriesOf("TESTCO-B", "NSE")).isEqualTo("EQ");
    assertThat(InstrumentCsvParser.seriesOf("TESTCO", "NSE")).isEqualTo("EQ");
    assertThat(InstrumentCsvParser.seriesOf("TEST-SG", "INDICES")).isEqualTo("INDEX");
  }

  @Test
  void rejectsAChangedHeader() {
    assertThatThrownBy(() ->
            InstrumentCsvParser.parse(
                new StringReader("instrument_token,tradingsymbol\n1,TESTCO\n")))
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("unexpected header");
  }

  @Test
  void namesTheLineOfABadRow() {
    assertThatThrownBy(() ->
            parse(
                """
                1001,11,TESTCO,"TEST",0,,0,0.05,1,EQ,NSE,NSE
                oops,12,OTHER,"OTHER",0,,0,0.05,1,EQ,NSE,NSE
                """))
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("line 3");
  }

  @Test
  void rejectsSymbolsWithUnexpectedCharacters() {
    assertThatThrownBy(() -> parse("1001,11,TEST;DROP,\"X\",0,,0,0.05,1,EQ,NSE,NSE\n"))
        .isInstanceOf(InstrumentFileException.class)
        .hasMessageContaining("invalid trading symbol");
  }

  @Test
  void rejectsRowsWithMissingFields() {
    assertThatThrownBy(() -> parse("1001,11,TESTCO\n")).isInstanceOf(InstrumentFileException.class);
  }
}
