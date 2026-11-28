package com.auratrade.api;

import com.auratrade.backtest.GateEvaluator;
import com.auratrade.backtest.GateRunService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only strategy gate: runs a strategy's grid and returns the six checks with their values. */
@RestController
@Profile("dev")
@RequestMapping("/dev")
class GateDevController {

  private final GateRunService gate;

  GateDevController(GateRunService gate) {
    this.gate = gate;
  }

  /**
   * For example /dev/strategies/momentum-breakout/gate?from=2019-01-01&oosStart=2023-01-01&to=...
   * Computed on each call (it replays the history, so it takes minutes).
   */
  @GetMapping("/strategies/{id}/gate")
  View gate(
      @PathVariable String id,
      @RequestParam(defaultValue = "2019-01-01") LocalDate from,
      @RequestParam(defaultValue = "2023-01-01") LocalDate oosStart,
      @RequestParam(defaultValue = "2026-12-31") LocalDate to) {
    GateRunService.Run run = gate.run(new GateRunService.Request(id, from, oosStart, to));
    GateEvaluator.Report r = run.report();
    return new View(
        r.strategyRef(),
        r.passed(),
        run.from(),
        run.oosStart(),
        run.to(),
        run.source(),
        run.benchmark(),
        run.settings(),
        run.candidateShares(),
        run.seconds(),
        r.chosenSettingId(),
        r.checks().stream()
            .map(c -> new CheckView(c.name(), c.passed(), c.value(), c.threshold()))
            .toList(),
        metrics(r.outOfSample()),
        metrics(r.walkForward()),
        r.settingsBeatingNifty(),
        r.settingCount(),
        r.defaultMaxDrawdown(),
        run.caveats());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail unavailable(IllegalStateException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  private static MetricsView metrics(GateEvaluator.Metrics m) {
    return new MetricsView(
        m.trades(), m.winRate(), m.expectancy(), m.profitFactor().orElse(null), m.totalNet());
  }

  record View(
      String strategy,
      boolean passed,
      LocalDate from,
      LocalDate oosStart,
      LocalDate to,
      String source,
      String benchmark,
      int settings,
      int candidateShares,
      long seconds,
      String chosenSetting,
      List<CheckView> checks,
      MetricsView outOfSample,
      MetricsView walkForward,
      int settingsBeatingNifty,
      int settingCount,
      BigDecimal defaultMaxDrawdown,
      List<String> caveats) {}

  record CheckView(String name, boolean passed, String value, String threshold) {}

  record MetricsView(
      int trades,
      BigDecimal winRate,
      BigDecimal expectancy,
      BigDecimal profitFactor,
      BigDecimal totalNet) {}
}
