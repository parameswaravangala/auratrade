package com.auratrade.api;

import com.auratrade.marketdata.BackfillService;
import com.auratrade.marketdata.CalendarNotCoveredException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only backfill control. Creating a request only queues it; the backfill job works through it
 * in batches (every 15 minutes, or POST /dev/jobs/backfill/run).
 */
@RestController
@Profile("dev")
@RequestMapping("/dev/marketdata/backfills")
class BackfillDevController {

  private final BackfillService backfills;

  BackfillDevController(BackfillService backfills) {
    this.backfills = backfills;
  }

  /** For example ?from=2024-01-01&to=2026-11-25&series=EQ. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  BackfillService.Status create(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "EQ") List<String> series) {
    return backfills.create(from, to, series);
  }

  @GetMapping
  List<BackfillService.Status> recent(@RequestParam(defaultValue = "10") int limit) {
    return backfills.recent(Math.min(limit, 100));
  }

  @PostMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@PathVariable long id) {
    backfills.cancel(id);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(CalendarNotCoveredException.class)
  ProblemDetail calendarNotCovered(CalendarNotCoveredException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
  }
}
