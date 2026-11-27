package com.auratrade.marketdata;

import java.util.List;
import java.util.Map;

/**
 * Optional capability of a {@link MarketDataProvider}: the daily bar of the latest session for many
 * instruments in one call, from a quote snapshot (ADR 0025). Used by the EOD job so the evening run
 * makes a handful of calls instead of one per share. The bar names its own session (IST midnight),
 * so a caller checks the date instead of trusting the clock; after the next open the snapshot holds
 * the new, unfinished session.
 *
 * <p>Snapshot bars may differ from the provider's final historical bar for the same session
 * (unverified), so callers treat them as provisional and correct them from history later.
 */
public interface SessionBarProvider {

  /** Most provider keys one call accepts. */
  int maxKeysPerCall();

  /**
   * Daily bars of the latest session, keyed by provider key, stamped at IST midnight of their
   * session. Instruments without a bar are left out. At most {@link #maxKeysPerCall()} keys.
   */
  Map<String, Candle> latestSessionBars(List<String> providerKeys);
}
