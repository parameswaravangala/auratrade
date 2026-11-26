package com.auratrade.marketdata;

/** The instrument file is malformed or fails a sanity check. Nothing from it is stored. */
public class InstrumentFileException extends RuntimeException {

  public InstrumentFileException(String message) {
    super(message);
  }

  public InstrumentFileException(String message, Throwable cause) {
    super(message, cause);
  }
}
