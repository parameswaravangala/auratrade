package com.auratrade.marketdata;

import java.math.BigDecimal;

/**
 * One row of the instrument master as read from the broker file. Untrusted input until parsed and
 * checked by {@link InstrumentCsvParser}.
 *
 * @param name company or index name; null when the file leaves it empty (common for bonds)
 * @param kiteInstrumentToken Kite's numeric id for the instrument; an attribute, not our key
 */
public record Instrument(
    String exchange,
    String tradingSymbol,
    String series,
    String segment,
    String instrumentType,
    String name,
    long kiteInstrumentToken,
    long exchangeToken,
    BigDecimal tickSize,
    int lotSize) {}
