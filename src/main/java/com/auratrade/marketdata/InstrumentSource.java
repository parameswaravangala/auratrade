package com.auratrade.marketdata;

import java.io.IOException;
import java.io.Reader;

/** Where the instrument file comes from. Production downloads it; tests pass synthetic text. */
@FunctionalInterface
public interface InstrumentSource {

  /** Opens the current instrument file. The caller closes the reader. */
  Reader open() throws IOException;
}
