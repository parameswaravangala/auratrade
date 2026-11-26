package com.auratrade.marketdata;

import java.math.BigDecimal;

/**
 * One listed instrument, normalised by an {@link InstrumentFeed} into provider-neutral terms.
 * Untrusted until {@link InstrumentLoader} has checked it.
 *
 * @param segment provider-neutral segment: EQ for securities traded in the cash market, INDEX
 * @param series exchange series such as EQ, BE, SM, SG, N0; INDEX for indices
 * @param name company or index name; null when the feed leaves it empty
 * @param isin ISIN; required for securities, null for indices (ADR 0015)
 * @param exchangeToken the exchange's numeric code, if the feed provides it
 * @param tickSize in rupees, whatever unit the provider uses
 * @param providerKey the feed's own identifier, for example NSE_EQ|INE... for Upstox
 */
public record Instrument(
    String exchange,
    String segment,
    String tradingSymbol,
    String series,
    String name,
    String isin,
    Long exchangeToken,
    BigDecimal tickSize,
    int lotSize,
    String providerKey) {

  public static final String SEGMENT_EQ = "EQ";
  public static final String SEGMENT_INDEX = "INDEX";

  boolean isIndex() {
    return SEGMENT_INDEX.equals(segment);
  }
}
