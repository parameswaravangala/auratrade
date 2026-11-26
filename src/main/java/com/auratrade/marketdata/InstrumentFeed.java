package com.auratrade.marketdata;

import java.io.IOException;
import java.util.List;

/**
 * A provider's instrument list, already translated into provider-neutral {@link Instrument}s.
 * Provider details (file format, units, identifiers) stay inside the implementation; the loader
 * never sees them. Implementations throw {@link InstrumentFileException} for a malformed file.
 */
public interface InstrumentFeed {

  /** Stable provider name stored with each provider key, for example UPSTOX. */
  String provider();

  List<Instrument> fetch() throws IOException;
}
