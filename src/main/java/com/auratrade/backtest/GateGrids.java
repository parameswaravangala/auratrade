package com.auratrade.backtest;

import java.util.ArrayList;
import java.util.List;

/**
 * The parameter grids the gate runs, per strategy (plan section 3: at most 18 combinations, and the
 * spec defaults must be among them so the drawdown check has its setting). The grid is declared
 * here, before any result is seen; changing it is a new gate run, not a tuning step.
 */
final class GateGrids {

  /**
   * One grid point. Strategy parameters, plus the holding limit (a plan detail, not a signal
   * input).
   */
  record Setting(String id, int resistanceLookback, String minVolumeRatio, int maxHold) {}

  /** Signal-generating parameters: settings that share these values share one replay. */
  record SignalKey(int resistanceLookback, String minVolumeRatio) {}

  static final String MOMENTUM_BREAKOUT = "momentum-breakout";
  static final String DEFAULT_MOMENTUM_BREAKOUT = "res20-vol1.5-hold15";

  private GateGrids() {}

  static List<Setting> forStrategy(String strategyId) {
    if (!MOMENTUM_BREAKOUT.equals(strategyId)) {
      throw new IllegalArgumentException("no gate grid declared for '" + strategyId + "'");
    }
    List<Setting> out = new ArrayList<>();
    for (int res : new int[] {20, 30}) {
      for (String vol : new String[] {"1.5", "2.0"}) {
        for (int hold : new int[] {10, 15}) {
          out.add(new Setting("res" + res + "-vol" + vol + "-hold" + hold, res, vol, hold));
        }
      }
    }
    return List.copyOf(out);
  }

  static String defaultSettingId(String strategyId) {
    if (!MOMENTUM_BREAKOUT.equals(strategyId)) {
      throw new IllegalArgumentException("no gate grid declared for '" + strategyId + "'");
    }
    return DEFAULT_MOMENTUM_BREAKOUT;
  }

  static SignalKey signalKey(Setting s) {
    return new SignalKey(s.resistanceLookback(), s.minVolumeRatio());
  }
}
