package com.auratrade.api;

import com.auratrade.marketdata.CandleIngestionService;
import com.auratrade.screener.ScreenerService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only screener endpoints, plus loading benchmark index history for relative strength. */
@RestController
@Profile("dev")
@RequestMapping("/dev")
class ScreenerDevController {

  private final ScreenerService screener;
  private final CandleIngestionService candles;

  ScreenerDevController(ScreenerService screener, CandleIngestionService candles) {
    this.screener = screener;
    this.candles = candles;
  }

  @GetMapping("/screener/screens")
  List<ScreenerService.ScreenInfo> screens() {
    return screener.screens();
  }

  /** For example /dev/screener/52w-high-breakout?limit=10 (asOf defaults to the newest data). */
  @GetMapping("/screener/{name}")
  ScreenerService.Result run(
      @PathVariable String name,
      @RequestParam Optional<LocalDate> asOf,
      @RequestParam(defaultValue = "10") int limit) {
    return screener.run(name, asOf, limit);
  }

  /** Loads daily history for a benchmark index, for example ?symbol=NIFTY&from=2018-01-01&to=... */
  @PostMapping("/marketdata/benchmarks/load")
  CandleIngestionService.Result loadBenchmark(
      @RequestParam(defaultValue = "NIFTY") String symbol,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    return candles.ingestIndex(symbol, from, to);
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
