package com.auratrade.backtest;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The parameter grids the gate runs, per strategy (plan section 3: at most 18 combinations, and the
 * spec default must be among them so the drawdown check has its setting). Declared here, before any
 * result is seen; changing a grid is a new gate run, not a tuning step.
 *
 * <p>A setting is a set of strategy parameters plus a holding limit. Settings that share the
 * parameters, apart from the holding limit, share one replay of the history (see {@link
 * SignalKey}); the holding limit is applied to the trade plan afterwards.
 */
final class GateGrids {

  /** One grid point: strategy parameters (as text, to be validated) and the holding limit. */
  record Setting(String id, Map<String, String> params, int maxHold) {
    Setting {
      params = new TreeMap<>(params);
    }
  }

  /** The parameters that change which shares trigger and what their plan is. */
  record SignalKey(Map<String, String> params) {
    SignalKey {
      params = new TreeMap<>(params);
    }
  }

  static final String MOMENTUM_BREAKOUT = "momentum-breakout";
  static final String PULLBACK = "pullback-20ema";
  static final String RELATIVE_STRENGTH = "relative-strength";
  static final String HIGH_52W = "high-52w-momentum";

  private GateGrids() {}

  static List<Setting> forStrategy(String strategyId) {
    return switch (strategyId) {
      case MOMENTUM_BREAKOUT ->
          grid(
              List.of("resistanceLookback", "minVolumeRatio"),
              List.of(
                  List.of("20", "1.5"),
                  List.of("20", "2.0"),
                  List.of("30", "1.5"),
                  List.of("30", "2.0")),
              List.of(10, 15));
      case PULLBACK ->
          grid(
              List.of("pullbackTolerance"),
              List.of(List.of("0.01"), List.of("0.02")),
              List.of(8, 12));
      case RELATIVE_STRENGTH ->
          grid(List.of("trailAtrMultiple"), List.of(List.of("2"), List.of("3")), List.of(15, 20));
      case HIGH_52W ->
          grid(
              List.of("nearHighTolerance"),
              List.of(List.of("0.03"), List.of("0.05")),
              List.of(10, 20));
      default ->
          throw new IllegalArgumentException("no gate grid declared for '" + strategyId + "'");
    };
  }

  /** The default setting: the strategy's own default parameters, which must be in the grid. */
  static String defaultSettingId(String strategyId) {
    return switch (strategyId) {
      case MOMENTUM_BREAKOUT -> "resistanceLookback=20,minVolumeRatio=1.5,hold=15";
      case PULLBACK -> "pullbackTolerance=0.02,hold=12";
      case RELATIVE_STRENGTH -> "trailAtrMultiple=2,hold=20";
      case HIGH_52W -> "nearHighTolerance=0.05,hold=20";
      default ->
          throw new IllegalArgumentException("no gate grid declared for '" + strategyId + "'");
    };
  }

  static SignalKey signalKey(Setting s) {
    return new SignalKey(s.params());
  }

  /** Every combination of the listed values, each with every holding limit. */
  private static List<Setting> grid(
      List<String> names, List<List<String>> combos, List<Integer> holds) {
    List<Setting> out = new java.util.ArrayList<>();
    for (List<String> values : combos) {
      Map<String, String> params = new TreeMap<>();
      for (int i = 0; i < names.size(); i++) {
        params.put(names.get(i), values.get(i));
      }
      for (int hold : holds) {
        String id =
            names.stream()
                    .map(n -> n + "=" + params.get(n))
                    .collect(java.util.stream.Collectors.joining(","))
                + ",hold="
                + hold;
        out.add(new Setting(id, params, hold));
      }
    }
    return List.copyOf(out);
  }
}
