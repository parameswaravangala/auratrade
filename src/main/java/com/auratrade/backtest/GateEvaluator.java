package com.auratrade.backtest;

import com.auratrade.backtest.PlanPortfolioSimulator.Day;
import com.auratrade.backtest.PlanPortfolioSimulator.Trade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pre-registered pass gate for one strategy version (ADR 0042, plan section 3). Pure: the
 * full-period results of every parameter setting in, a pass or fail with each check's value out.
 *
 * <p>Method. Settings are compared on the period before {@code oosStart} to choose the fixed
 * setting. Out of sample, that setting is judged once. Walk-forward re-chooses each year using only
 * trades that had closed before 1 January of that year. A setting's trades in a year come from one
 * full-period run; portfolio capital carried across years is not re-run, which is an approximation
 * stated in ADR 0042.
 *
 * <p>The six checks are the rules in plan section 3. Each is reported with its value, so a failure
 * is explained rather than just counted.
 */
public final class GateEvaluator {

  static final int MIN_OOS_TRADES = 100;
  static final BigDecimal MIN_PROFIT_FACTOR = new BigDecimal("1.2");
  static final BigDecimal MAX_DRAWDOWN = new BigDecimal("-0.25");
  static final int TOP_TRADES_REMOVED = 5;
  static final int MIN_SELECTION_TRADES = 30;

  private static final MathContext MC = MathContext.DECIMAL64;

  private GateEvaluator() {}

  /** One parameter setting's full-period result, with its daily equity. */
  public record SettingResult(String id, List<Trade> trades, List<Day> days) {
    public SettingResult {
      Objects.requireNonNull(id, "id");
      trades = List.copyOf(trades);
      days = List.copyOf(days);
    }
  }

  /**
   * @param oosStart first session of the out-of-sample period
   * @param end last session of the run
   * @param niftyOosCagr Nifty 50 buy and hold, annualised, over the same out-of-sample dates
   * @param defaultSettingId the spec-default setting, judged on drawdown
   */
  public record Input(
      String strategyRef,
      LocalDate oosStart,
      LocalDate end,
      BigDecimal initialCapital,
      List<SettingResult> settings,
      String defaultSettingId,
      double niftyOosCagr) {
    public Input {
      Objects.requireNonNull(strategyRef, "strategyRef");
      Objects.requireNonNull(oosStart, "oosStart");
      Objects.requireNonNull(end, "end");
      Objects.requireNonNull(initialCapital, "initialCapital");
      settings = List.copyOf(settings);
      if (settings.isEmpty()) {
        throw new IllegalArgumentException("at least one setting is required");
      }
    }
  }

  public record Check(String name, boolean passed, String value, String threshold) {}

  public record Metrics(
      int trades,
      BigDecimal winRate,
      BigDecimal expectancy,
      Optional<BigDecimal> profitFactor,
      BigDecimal totalNet) {}

  /**
   * @param chosenSettingId the setting picked on the period before {@code oosStart}
   * @param settingsBeatingNifty settings whose out-of-sample CAGR is above Nifty's
   */
  public record Report(
      String strategyRef,
      boolean passed,
      List<Check> checks,
      String chosenSettingId,
      Metrics outOfSample,
      Metrics walkForward,
      int settingsBeatingNifty,
      int settingCount,
      BigDecimal defaultMaxDrawdown) {}

  public static Report evaluate(Input in) {
    SettingResult defaults =
        in.settings().stream()
            .filter(s -> s.id().equals(in.defaultSettingId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("default setting not in the run"));

    SettingResult chosen =
        select(in.settings(), in.oosStart())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "no setting has enough trades before the out-of-sample period"));
    List<Trade> oos = inRange(chosen.trades(), in.oosStart(), in.end());
    Metrics oosMetrics = metrics(oos);

    List<Trade> walk = new ArrayList<>();
    for (int year = in.oosStart().getYear(); year <= in.end().getYear(); year++) {
      Optional<SettingResult> pick = select(in.settings(), LocalDate.of(year, 1, 1));
      if (pick.isEmpty()) {
        continue;
      }
      for (Trade t : pick.get().trades()) {
        if (t.entryDate().getYear() == year) {
          walk.add(t);
        }
      }
    }
    Metrics walkMetrics = metrics(walk);

    int beating = 0;
    for (SettingResult s : in.settings()) {
      if (oosCagr(s, in) > in.niftyOosCagr()) {
        beating++;
      }
    }
    BigDecimal defaultDd = maxDrawdownOos(defaults, in);

    List<Trade> withoutTop = new ArrayList<>(oos);
    withoutTop.sort(Comparator.comparing(Trade::netPnl).reversed());
    Metrics trimmed =
        metrics(
            withoutTop.subList(Math.min(TOP_TRADES_REMOVED, withoutTop.size()), withoutTop.size()));

    List<Check> checks = new ArrayList<>();
    checks.add(
        new Check(
            "Out-of-sample trades",
            oosMetrics.trades() >= MIN_OOS_TRADES,
            String.valueOf(oosMetrics.trades()),
            ">= " + MIN_OOS_TRADES));
    checks.add(
        new Check(
            "Out-of-sample expectancy after costs (INR per trade)",
            oosMetrics.expectancy().signum() > 0,
            fmt(oosMetrics.expectancy()),
            "> 0"));
    checks.add(
        new Check(
            "Walk-forward expectancy after costs (INR per trade)",
            walkMetrics.expectancy().signum() > 0,
            fmt(walkMetrics.expectancy()),
            "> 0"));
    checks.add(
        new Check(
            "Out-of-sample profit factor",
            oosMetrics
                .profitFactor()
                .map(pf -> pf.compareTo(MIN_PROFIT_FACTOR) > 0)
                .orElse(oosMetrics.trades() > 0),
            oosMetrics.profitFactor().map(GateEvaluator::fmt).orElse("no losing trades"),
            "> " + MIN_PROFIT_FACTOR));
    boolean majority = beating * 2 > in.settings().size();
    checks.add(
        new Check(
            "Settings beating Nifty out of sample (after costs)",
            majority,
            beating + " of " + in.settings().size(),
            "more than half"));
    checks.add(
        new Check(
            "Expectancy without the " + TOP_TRADES_REMOVED + " best trades (INR per trade)",
            trimmed.expectancy().signum() > 0,
            fmt(trimmed.expectancy()),
            "> 0"));
    checks.add(
        new Check(
            "Max drawdown at spec defaults, out of sample",
            defaultDd.compareTo(MAX_DRAWDOWN) >= 0,
            pct(defaultDd),
            ">= " + pct(MAX_DRAWDOWN)));

    boolean passed = checks.stream().allMatch(Check::passed);
    return new Report(
        in.strategyRef(),
        passed,
        checks,
        chosen.id(),
        oosMetrics,
        walkMetrics,
        beating,
        in.settings().size(),
        defaultDd);
  }

  /**
   * The best setting by expectancy among those with enough trades that closed before {@code
   * cutoff}; ties go to the lower id. Empty when none qualifies.
   */
  static Optional<SettingResult> select(List<SettingResult> settings, LocalDate cutoff) {
    SettingResult best = null;
    BigDecimal bestExp = null;
    for (SettingResult s : settings) {
      List<Trade> closed = s.trades().stream().filter(t -> t.exitDate().isBefore(cutoff)).toList();
      if (closed.size() < MIN_SELECTION_TRADES) {
        continue;
      }
      BigDecimal exp = metrics(closed).expectancy();
      if (best == null
          || exp.compareTo(bestExp) > 0
          || (exp.compareTo(bestExp) == 0 && s.id().compareTo(best.id()) < 0)) {
        best = s;
        bestExp = exp;
      }
    }
    return Optional.ofNullable(best);
  }

  static Metrics metrics(List<Trade> trades) {
    int n = trades.size();
    if (n == 0) {
      return new Metrics(0, BigDecimal.ZERO, BigDecimal.ZERO, Optional.empty(), BigDecimal.ZERO);
    }
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal wins = BigDecimal.ZERO;
    BigDecimal losses = BigDecimal.ZERO;
    int winCount = 0;
    for (Trade t : trades) {
      total = total.add(t.netPnl());
      if (t.netPnl().signum() > 0) {
        winCount++;
        wins = wins.add(t.netPnl());
      } else {
        losses = losses.add(t.netPnl().negate());
      }
    }
    BigDecimal count = BigDecimal.valueOf(n);
    BigDecimal winRate = BigDecimal.valueOf(winCount).divide(count, MC);
    BigDecimal expectancy = total.divide(count, MC);
    Optional<BigDecimal> pf =
        losses.signum() == 0 ? Optional.empty() : Optional.of(wins.divide(losses, MC));
    return new Metrics(n, winRate, expectancy, pf, total);
  }

  /** Out-of-sample CAGR of a setting, starting from its equity on the last day before oosStart. */
  static double oosCagr(SettingResult s, Input in) {
    BigDecimal start = startEquity(s, in);
    BigDecimal end = s.days().isEmpty() ? start : s.days().get(s.days().size() - 1).equity();
    double years = years(in.oosStart(), in.end());
    if (years <= 0 || start.signum() <= 0) {
      return Double.NaN;
    }
    return Math.pow(end.doubleValue() / start.doubleValue(), 1.0 / years) - 1.0;
  }

  static BigDecimal maxDrawdownOos(SettingResult s, Input in) {
    BigDecimal peak = startEquity(s, in);
    BigDecimal worst = BigDecimal.ZERO;
    for (Day d : s.days()) {
      if (d.date().isBefore(in.oosStart())) {
        continue;
      }
      peak = peak.max(d.equity());
      BigDecimal dd = d.equity().subtract(peak).divide(peak, MC);
      worst = worst.min(dd);
    }
    return worst;
  }

  private static BigDecimal startEquity(SettingResult s, Input in) {
    BigDecimal start = in.initialCapital();
    for (Day d : s.days()) {
      if (d.date().isBefore(in.oosStart())) {
        start = d.equity();
      }
    }
    return start;
  }

  private static List<Trade> inRange(List<Trade> trades, LocalDate from, LocalDate to) {
    return trades.stream()
        .filter(t -> !t.entryDate().isBefore(from) && !t.entryDate().isAfter(to))
        .toList();
  }

  private static double years(LocalDate from, LocalDate to) {
    return java.time.temporal.ChronoUnit.DAYS.between(from, to) / 365.25;
  }

  private static String fmt(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static String pct(BigDecimal fraction) {
    return fraction
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP)
            .toPlainString()
        + "%";
  }
}
