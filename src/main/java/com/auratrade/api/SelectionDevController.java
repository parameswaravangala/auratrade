package com.auratrade.api;

import com.auratrade.selection.Selection;
import com.auratrade.selection.StockSelectionService;
import com.auratrade.strategy.ConditionResult;
import com.auratrade.strategy.StrategyEvaluation;
import com.auratrade.strategy.TradePlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only stock selection: every strategy's result per share, and the ranked shortlist. */
@RestController
@Profile("dev")
@RequestMapping("/dev")
class SelectionDevController {

  private final StockSelectionService selection;

  SelectionDevController(StockSelectionService selection) {
    this.selection = selection;
  }

  /** For example /dev/selection?limit=10 (asOf defaults to the newest data). */
  @GetMapping("/selection")
  View run(@RequestParam Optional<LocalDate> asOf, @RequestParam(defaultValue = "10") int limit) {
    StockSelectionService.Run run = selection.run(asOf, limit);
    Selection.Result r = run.result();
    return new View(
        run.asOf(),
        run.source(),
        run.approvedStrategies(),
        r.evaluated(),
        r.passing(),
        r.skipped(),
        r.shortlist().stream().map(SelectionDevController::candle).toList(),
        r.almostQualified().stream().map(SelectionDevController::candle).toList());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail unavailable(IllegalStateException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  private static CandidateView candle(Selection.Candidate c) {
    return new CandidateView(
        c.instrumentId(),
        c.symbol(),
        c.name(),
        c.bestScore(),
        c.passedStrategies(),
        c.evaluations().stream().map(SelectionDevController::evaluation).toList());
  }

  private static EvaluationView evaluation(StrategyEvaluation e) {
    return new EvaluationView(
        e.strategyRef(),
        e.status().name(),
        e.score(),
        e.explanation(),
        e.conditions().stream().map(SelectionDevController::condition).toList(),
        e.plan().map(SelectionDevController::plan).orElse(null));
  }

  private static ConditionView condition(ConditionResult c) {
    return new ConditionView(
        c.id(),
        c.description(),
        c.required(),
        c.outcome().name(),
        c.value().orElse(null),
        c.threshold());
  }

  private static PlanView plan(TradePlan p) {
    return new PlanView(
        p.entry().type().name(),
        p.entry().trigger(),
        p.entry().maxPrice(),
        p.entry().validSessions(),
        p.stop(),
        p.stopReason(),
        p.riskPerShare(),
        p.targets().stream()
            .map(t -> new TargetView(t.rMultiple(), t.fraction(), p.targetPrice(t)))
            .toList(),
        p.maxHoldSessions());
  }

  /** The whole response. Optional values are plain nulls so the JSON is simple. */
  record View(
      LocalDate asOf,
      String source,
      List<String> approvedStrategies,
      int evaluated,
      int passing,
      Map<String, Integer> skipped,
      List<CandidateView> shortlist,
      List<CandidateView> almostQualified) {}

  record CandidateView(
      long instrumentId,
      String symbol,
      String name,
      BigDecimal bestScore,
      List<String> passedStrategies,
      List<EvaluationView> evaluations) {}

  record EvaluationView(
      String strategy,
      String status,
      BigDecimal score,
      String explanation,
      List<ConditionView> conditions,
      PlanView plan) {}

  record ConditionView(
      String id,
      String description,
      boolean required,
      String outcome,
      BigDecimal value,
      String threshold) {}

  record PlanView(
      String entryType,
      BigDecimal trigger,
      BigDecimal maxPrice,
      int validSessions,
      BigDecimal stop,
      String stopReason,
      BigDecimal riskPerShare,
      List<TargetView> targets,
      int maxHoldSessions) {}

  record TargetView(BigDecimal rMultiple, BigDecimal fraction, BigDecimal price) {}
}
