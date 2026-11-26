package com.auratrade.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parameter sweep with an out-of-sample check (ADR 0023). Signals and prices are prepared once; the
 * fast simulator then runs every combination on the in-sample period and, separately with fresh
 * capital, on the out-of-sample period. The combination with the best in-sample Sharpe is reported
 * as "chosen", next to how it did out of sample, and every combination's out-of-sample result is
 * listed so overfitting is visible. Plain Java, no LLM.
 */
public class StrategySweepService {

  static final List<BigDecimal> ATR_MULTIPLES =
      List.of(new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("3"));
  static final List<Integer> MAX_HOLDS = List.of(20, 60, 120);
  static final List<Boolean> REGIME = List.of(false, true);

  static final BigDecimal CAPITAL = new BigDecimal("1000000");
  static final int POSITIONS = 10;

  public record Window(
      BigDecimal cagrPct,
      BigDecimal maxDrawdownPct,
      BigDecimal sharpe,
      int trades,
      BigDecimal benchmarkCagrPct,
      BigDecimal benchmarkMaxDrawdownPct) {}

  public record Row(
      BigDecimal atrMultiple,
      int maxHold,
      boolean regimeFilter,
      Window inSample,
      Window outOfSample) {}

  public record Report(
      String screen,
      LocalDate from,
      LocalDate split,
      LocalDate to,
      String fixedSettings,
      Row chosen,
      int combinations,
      int combinationsBeatingBenchmarkOutOfSample,
      long seconds,
      List<Row> rowsByInSampleSharpe,
      List<String> caveats) {}

  private final PortfolioSimulationService simulation;

  StrategySweepService(PortfolioSimulationService simulation) {
    this.simulation = simulation;
  }

  public Report sweep(String screen, LocalDate from, LocalDate split, LocalDate to) {
    if (!from.isBefore(split) || !split.isBefore(to)) {
      throw new IllegalArgumentException("need from < split < to");
    }
    long started = System.nanoTime();
    var prepared = simulation.prepare(screen, from, to, POSITIONS * 3);
    List<LocalDate> inSessions =
        prepared.sessions().stream().filter(d -> d.isBefore(split)).toList();
    List<LocalDate> outSessions =
        prepared.sessions().stream().filter(d -> !d.isBefore(split)).toList();

    List<Object[]> grid = new ArrayList<>();
    for (BigDecimal atr : ATR_MULTIPLES) {
      for (int hold : MAX_HOLDS) {
        for (boolean regime : REGIME) {
          grid.add(new Object[] {atr, hold, regime});
        }
      }
    }
    List<Row> rows =
        grid.parallelStream()
            .map(g -> {
              BigDecimal atr = (BigDecimal) g[0];
              int hold = (Integer) g[1];
              boolean regime = (Boolean) g[2];
              var settings =
                  new PortfolioSimulator.Settings(
                      CAPITAL,
                      POSITIONS,
                      new BigDecimal("0.01"),
                      new BigDecimal("0.20"),
                      14,
                      atr,
                      hold,
                      5,
                      CostModel.zerodhaDelivery(),
                      regime);
              return new Row(
                  atr,
                  hold,
                  regime,
                  window(prepared, inSessions, settings),
                  window(prepared, outSessions, settings));
            })
            .sorted(
                Comparator.comparing((Row r) -> nullLow(r.inSample().sharpe()))
                    .reversed()
                    .thenComparing(r -> nullLow(r.inSample().cagrPct()), Comparator.reverseOrder()))
            .toList();
    int beat =
        (int)
            rows.stream()
                .filter(r ->
                    r.outOfSample().cagrPct() != null
                        && r.outOfSample().benchmarkCagrPct() != null
                        && r.outOfSample().cagrPct().compareTo(r.outOfSample().benchmarkCagrPct())
                            > 0)
                .count();
    return new Report(
        screen,
        from,
        split,
        to,
        "INR 10 lakh, 10 positions, 1% risk per trade, 20% maximum position, ATR(14), 5 bps"
            + " slippage, Zerodha delivery charges",
        rows.get(0),
        rows.size(),
        beat,
        (System.nanoTime() - started) / 1_000_000_000L,
        rows,
        PortfolioSimulationService.CAVEATS);
  }

  private static Window window(
      PortfolioSimulationService.Prepared p,
      List<LocalDate> sessions,
      PortfolioSimulator.Settings settings) {
    var outcome =
        PortfolioSimulator.run(sessions, p.prices(), p.signalsByDate(), p.benchmark(), settings);
    var s = PortfolioMetrics.of(outcome, settings.initialCapital());
    return new Window(
        s.cagrPct(),
        s.maxDrawdownPct(),
        s.sharpe(),
        s.trades(),
        s.benchmarkCagrPct(),
        s.benchmarkMaxDrawdownPct());
  }

  private static BigDecimal nullLow(BigDecimal v) {
    return v == null ? new BigDecimal("-1000000") : v;
  }
}
