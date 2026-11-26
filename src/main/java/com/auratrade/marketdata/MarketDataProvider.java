package com.auratrade.marketdata;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * A source of OHLCV candles (ADR 0015, 0016). Consumers depend on this interface only; Upstox is
 * the first implementation, Kite can follow. Implementations translate provider formats into {@link
 * Candle}s (BigDecimal prices, UTC instants, oldest first) and throw {@link ProviderException}
 * subtypes for failures. They never retry silently.
 */
public interface MarketDataProvider {

  /** Stable name, also stored as candle.source and instrument_provider_key.provider. */
  String name();

  Set<Interval> supportedIntervals();

  /**
   * Candles for one instrument between two trading dates, both inclusive, oldest first.
   *
   * @param providerKey this provider's own identifier for the instrument
   */
  List<Candle> candles(String providerKey, Interval interval, LocalDate from, LocalDate to);
}
