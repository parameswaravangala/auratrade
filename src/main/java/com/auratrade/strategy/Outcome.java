package com.auratrade.strategy;

/** Result of one condition. UNKNOWN means the data to decide was missing; it is never a pass. */
public enum Outcome {
  PASS,
  FAIL,
  UNKNOWN
}
