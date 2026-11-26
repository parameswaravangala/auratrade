package com.auratrade.marketdata;

/** A company share the resolver may return. Only rows that exist in the instrument table. */
public record ShareRef(long instrumentId, String tradingSymbol, String name, String series) {}
