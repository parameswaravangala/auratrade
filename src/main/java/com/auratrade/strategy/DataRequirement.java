package com.auratrade.strategy;

/**
 * Data a strategy needs. A screening run uses this to report what was unavailable; a strategy whose
 * source does not exist yet records the affected conditions as UNKNOWN.
 */
public enum DataRequirement {
  DAILY_CANDLES,
  BENCHMARK,
  SECTOR_MAPPING,
  SECTOR_INDEX,
  FUNDAMENTALS,
  RESULTS_CALENDAR
}
