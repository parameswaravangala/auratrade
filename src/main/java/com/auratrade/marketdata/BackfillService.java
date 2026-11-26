package com.auratrade.marketdata;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads candle history in resumable requests (ADR 0018). A request is split into one item per
 * share; {@link #runBatch} works through pending items, so a crash, restart or outage resumes where
 * it stopped. Candles go through the same validation as daily ingestion.
 *
 * <p>Fail closed: a request is refused unless the trading calendar covers every year in the range.
 * A share whose candles fail validation is marked FAILED (data problem; no retry). A transient
 * error is retried up to {@code maxAttempts}. An auth or rate-limit failure stops the batch at once
 * and leaves items pending.
 */
public class BackfillService {

  private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

  /** Request summary for operators. */
  public record Status(
      long id,
      String source,
      Interval interval,
      LocalDate from,
      LocalDate to,
      String series,
      String status,
      long pending,
      long done,
      long failed,
      long candlesStored,
      List<String> firstFailures) {}

  /** What one batch did. */
  public record BatchResult(long requestId, int done, int failed, int retrying, long pending) {}

  private final BackfillRepository repository;
  private final CandleIngestionService ingestion;
  private final TradingCalendar calendar;
  private final Clock clock;
  private final int maxAttempts;

  BackfillService(
      BackfillRepository repository,
      CandleIngestionService ingestion,
      TradingCalendar calendar,
      Clock clock,
      int maxAttempts) {
    this.repository = repository;
    this.ingestion = ingestion;
    this.calendar = calendar;
    this.clock = clock;
    this.maxAttempts = maxAttempts;
  }

  /**
   * Creates a DAY backfill for every active share in {@code series} from the selected provider.
   *
   * @throws CalendarNotCoveredException if any year in the range is not covered
   * @throws IllegalArgumentException for a bad range, an open request, or an empty universe
   */
  public Status create(LocalDate from, LocalDate to, List<String> series) {
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to " + to + " is before from " + from);
    }
    LocalDate today = LocalDate.now(clock.withZone(com.auratrade.platform.time.MarketZones.IST));
    if (!to.isBefore(today)) {
      throw new IllegalArgumentException(
          "a backfill must end before today (" + today + "); the daily job loads today");
    }
    for (int y = from.getYear(); y <= to.getYear(); y++) {
      if (!calendar.covers(y)) {
        throw new CalendarNotCoveredException(y);
      }
    }
    String source = ingestion.providerName();
    repository
        .openRequest(source, Interval.DAY)
        .ifPresent(r -> {
          throw new IllegalArgumentException(
              "backfill #" + r.id() + " is still running; cancel it or wait");
        });
    List<CandleRepository.Target> universe = ingestion.universe(series, 0);
    if (universe.isEmpty()) {
      throw new IllegalArgumentException("no active shares in " + series + "; load instruments");
    }
    long id =
        repository.createRequest(
            source, Interval.DAY, from, to, String.join(",", series), universe, clock.instant());
    log.info("Backfill #{} created: {} shares, {} to {}", id, universe.size(), from, to);
    return status(id).orElseThrow();
  }

  /** Works through up to {@code maxItems} pending items of the oldest open request. */
  public Optional<BatchResult> runBatch(int maxItems) {
    Optional<BackfillRepository.Request> open =
        repository.nextOpenRequest(ingestion.providerName());
    if (open.isEmpty()) {
      return Optional.empty();
    }
    BackfillRepository.Request req = open.get();
    int done = 0;
    int failed = 0;
    int retrying = 0;
    for (BackfillRepository.Item item : repository.pendingItems(req.id(), maxItems)) {
      var target =
          new CandleRepository.Target(
              item.instrumentId(), item.tradingSymbol(), item.providerKey());
      int attempts = item.attempts() + 1;
      try {
        var result = ingestion.ingest(target, req.interval(), req.from(), req.to());
        if (result.hasErrors()) {
          String codes =
              result.issues().stream()
                  .filter(i -> i.severity() == Severity.ERROR)
                  .map(QualityIssue::code)
                  .distinct()
                  .collect(Collectors.joining(","));
          repository.markItem(
              req.id(), item.instrumentId(), "FAILED", attempts, 0, "validation: " + codes, now());
          failed++;
        } else {
          repository.markItem(
              req.id(), item.instrumentId(), "DONE", attempts, result.stored(), null, now());
          done++;
        }
      } catch (ProviderException.Auth | ProviderException.RateLimited e) {
        throw e; // every item would fail; leave them pending for the next run
      } catch (RuntimeException e) {
        String status = attempts >= maxAttempts ? "FAILED" : "PENDING";
        repository.markItem(
            req.id(), item.instrumentId(), status, attempts, 0, e.toString(), now());
        if (status.equals("FAILED")) {
          failed++;
        } else {
          retrying++;
        }
      }
    }
    BackfillRepository.Progress p = repository.progress(req.id());
    if (p.pending() == 0) {
      repository.finishRequest(req.id(), p.failed() == 0 ? "DONE" : "DONE_WITH_FAILURES", now());
      log.info("Backfill #{} finished: {} done, {} failed", req.id(), p.done(), p.failed());
    }
    return Optional.of(new BatchResult(req.id(), done, failed, retrying, p.pending()));
  }

  public boolean hasOpenRequest() {
    return repository.nextOpenRequest(ingestion.providerName()).isPresent();
  }

  public Optional<BackfillRepository.Request> openRequest() {
    return repository.nextOpenRequest(ingestion.providerName());
  }

  public void cancel(long id) {
    status(id)
        .filter(s -> s.status().equals("PENDING"))
        .orElseThrow(() -> new IllegalArgumentException("backfill #" + id + " is not open"));
    repository.finishRequest(id, "CANCELLED", now());
  }

  public List<Status> recent(int limit) {
    return repository.recentRequests(limit).stream().map(this::toStatus).toList();
  }

  public Optional<Status> status(long id) {
    return repository.recentRequests(1000).stream()
        .filter(r -> r.id() == id)
        .findFirst()
        .map(this::toStatus);
  }

  private Status toStatus(BackfillRepository.Request r) {
    BackfillRepository.Progress p = repository.progress(r.id());
    return new Status(
        r.id(),
        r.source(),
        r.interval(),
        r.from(),
        r.to(),
        r.series(),
        r.status(),
        p.pending(),
        p.done(),
        p.failed(),
        p.candlesStored(),
        repository.failedSymbols(r.id(), 10));
  }

  private java.time.Instant now() {
    return clock.instant();
  }
}
