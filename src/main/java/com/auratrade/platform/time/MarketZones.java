package com.auratrade.platform.time;

import java.time.ZoneId;

/** Time zones used across the platform. Store timestamps in UTC; schedule and display in IST. */
public final class MarketZones {

  public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private MarketZones() {}
}
