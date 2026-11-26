package com.auratrade.api;

import com.auratrade.backtest.PortfolioSimulationService;
import com.auratrade.backtest.ScreenBacktestService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only: backtest the live screens over stored history. Takes a few minutes on full history. */
@RestController
@Profile("dev")
@RequestMapping("/dev/backtest")
class BacktestDevController {

  private final ScreenBacktestService backtests;
  private final PortfolioSimulationService portfolio;

  BacktestDevController(ScreenBacktestService backtests, PortfolioSimulationService portfolio) {
    this.backtests = backtests;
    this.portfolio = portfolio;
  }

  /** For example /dev/backtest/screens?from=2019-01-01&to=2026-08-31&step=5&horizons=5,20,60. */
  @GetMapping("/screens")
  ScreenBacktestService.Report screens(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "5") int step,
      @RequestParam(defaultValue = "5,20,60") List<Integer> horizons,
      @RequestParam(defaultValue = "10") int topN,
      @RequestParam(defaultValue = "0") int costBps) {
    return backtests.run(from, to, step, horizons, topN, costBps);
  }

  /**
   * Simulates an account trading one screen. Defaults: INR 10 lakh, 10 positions, 1% risk per
   * trade, 20% maximum position, stop 2 x ATR(14), 60-session holding limit, 5 bps slippage, costs
   * on. For example /dev/backtest/portfolio?screen=pullback-to-50dma&from=2019-01-01&to=2026-11-25.
   */
  @GetMapping("/portfolio")
  PortfolioSimulationService.Report portfolio(
      @RequestParam String screen,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "1000000") java.math.BigDecimal capital,
      @RequestParam(defaultValue = "10") int maxPositions,
      @RequestParam(defaultValue = "1") java.math.BigDecimal riskPct,
      @RequestParam(defaultValue = "20") java.math.BigDecimal maxPositionPct,
      @RequestParam(defaultValue = "2") java.math.BigDecimal atrMultiple,
      @RequestParam(defaultValue = "60") int maxHold,
      @RequestParam(defaultValue = "5") int slippageBps,
      @RequestParam(defaultValue = "true") boolean costs) {
    return portfolio.run(
        new PortfolioSimulationService.Request(
            screen,
            from,
            to,
            capital,
            maxPositions,
            riskPct,
            maxPositionPct,
            atrMultiple,
            maxHold,
            slippageBps,
            costs));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail notReady(IllegalStateException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }
}
