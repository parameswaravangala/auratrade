package com.auratrade.api;

import com.auratrade.paperbroker.PaperRepository;
import com.auratrade.paperbroker.PaperRepository.Account;
import com.auratrade.paperbroker.PaperTradingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paper trading API (ADR 0024). Only in the dev profile until authentication exists, because it
 * lets a caller approve orders. The UI is meant to use these endpoints.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/paper")
class PaperTradingController {

  private final PaperTradingService paper;
  private final PaperRepository repo;

  PaperTradingController(PaperTradingService paper, PaperRepository repo) {
    this.paper = paper;
    this.repo = repo;
  }

  /** Percentages are entered as percent: riskPct 1 means 1% of equity per trade. */
  record OpenAccount(
      String name,
      String screen,
      BigDecimal capital,
      Integer maxPositions,
      BigDecimal riskPct,
      BigDecimal maxPositionPct,
      BigDecimal atrMultiple,
      Integer maxHold,
      Boolean regime,
      Integer slippageBps,
      String goLiveCriteria,
      LocalDate startOn) {}

  /** Defaults are the relative-strength settings that held up out of sample (ADR 0023). */
  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  Account open(@RequestBody OpenAccount r) {
    return paper.open(
        new Account(
            0,
            r.name(),
            r.screen() == null ? "relative-strength-leaders" : r.screen(),
            r.capital() == null ? new BigDecimal("1000000") : r.capital(),
            r.capital() == null ? new BigDecimal("1000000") : r.capital(),
            r.maxPositions() == null ? 10 : r.maxPositions(),
            (r.riskPct() == null ? BigDecimal.ONE : r.riskPct()).movePointLeft(2),
            (r.maxPositionPct() == null ? BigDecimal.valueOf(20) : r.maxPositionPct())
                .movePointLeft(2),
            r.atrMultiple() == null ? BigDecimal.valueOf(3) : r.atrMultiple(),
            r.maxHold() == null ? 120 : r.maxHold(),
            r.regime() == null || r.regime(),
            r.slippageBps() == null ? 5 : r.slippageBps(),
            r.goLiveCriteria(),
            r.startOn() == null ? paper.latestCandleDate() : r.startOn(),
            true));
  }

  @GetMapping("/accounts")
  List<Account> accounts() {
    return repo.accounts();
  }

  record AccountView(
      Account account,
      List<PaperRepository.Position> positions,
      List<PaperRepository.Order> proposed,
      List<PaperRepository.EquityPoint> equity) {}

  @GetMapping("/accounts/{id}")
  AccountView account(@PathVariable long id) {
    return new AccountView(
        repo.account(id).orElseThrow(() -> new IllegalArgumentException("no account " + id)),
        repo.positions(id),
        repo.orders(id, "PROPOSED", 100),
        repo.equity(id));
  }

  @GetMapping("/accounts/{id}/orders")
  List<PaperRepository.Order> orders(
      @PathVariable long id,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    return repo.orders(id, status, Math.min(limit, 500));
  }

  @GetMapping("/accounts/{id}/trades")
  List<PaperRepository.Trade> trades(@PathVariable long id) {
    return repo.trades(id);
  }

  /**
   * @param quantity optional, BUY only: 1 up to the proposed quantity
   * @param limitPrice optional: most to pay on a BUY, least to accept on a SELL
   */
  record Decision(String by, String note, Long quantity, BigDecimal limitPrice) {}

  @PostMapping("/orders/{id}/approve")
  PaperRepository.Order approve(@PathVariable long id, @RequestBody Decision d) {
    return paper.decide(id, true, d.by(), d.note(), d.quantity(), d.limitPrice());
  }

  @PostMapping("/orders/{id}/reject")
  PaperRepository.Order reject(@PathVariable long id, @RequestBody Decision d) {
    return paper.decide(id, false, d.by(), d.note(), d.quantity(), d.limitPrice());
  }

  record BulkDecision(List<Long> orderIds, boolean approve, String by, String note) {}

  /** Same decision for several orders, each on its own; always 200 with one outcome per order. */
  @PostMapping("/orders/decide")
  List<PaperTradingService.Outcome> decideAll(@RequestBody BulkDecision d) {
    return paper.decideAll(d.orderIds(), d.approve(), d.by(), d.note());
  }

  /** Possible corporate actions on held positions, newest first; status OPEN or RESOLVED. */
  @GetMapping("/accounts/{id}/reviews")
  List<PaperRepository.Review> reviews(
      @PathVariable long id, @RequestParam(required = false) String status) {
    return repo.reviews(id, status);
  }

  /**
   * @param resolution NO_CORPORATE_ACTION or ADJUSTED
   * @param factor shares multiplier for ADJUSTED (2 for a 1:1 bonus or a 1:2 split), else null
   */
  record Resolve(String by, String note, String resolution, BigDecimal factor) {}

  @PostMapping("/reviews/{id}/resolve")
  PaperRepository.Review resolve(@PathVariable long id, @RequestBody Resolve r) {
    return paper.resolveReview(id, r.resolution(), r.factor(), r.by(), r.note());
  }

  /** Dev: process one session for one account now, for example to start on the latest data. */
  @PostMapping("/accounts/{id}/process")
  PaperTradingService.SessionResult process(
      @PathVariable long id, @RequestParam LocalDate session) {
    return paper.process(id, session);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail conflict(IllegalStateException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }
}
