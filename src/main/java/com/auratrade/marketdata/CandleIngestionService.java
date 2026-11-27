package com.auratrade.marketdata;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches candles from the configured provider, validates them, and stores them under that
 * provider's name as the source (ADR 0016). Fail closed: if validation finds any ERROR, nothing
 * from the request is stored. WARNINGs are stored with the candles' report so a human can look.
 *
 * <p>Validation needs the trading calendar, so a range in a year the calendar does not cover throws
 * {@link CalendarNotCoveredException} before anything is stored.
 */
public class CandleIngestionService {

  private static final Logger log = LoggerFactory.getLogger(CandleIngestionService.class);
  private static final String EXCHANGE = "NSE";

  /**
   * @param stored candles written; 0 when the report has errors
   * @param newestDate trading date of the newest candle fetched, or null if none came back
   */
  public record Result(
      String symbol,
      String source,
      Interval interval,
      LocalDate from,
      LocalDate to,
      int fetched,
      int stored,
      LocalDate newestDate,
      List<QualityIssue> issues) {

    public boolean hasErrors() {
      return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }
  }

  private final MarketDataProvider provider;
  private final CandleRepository candles;
  private final CandleValidator validator;
  private final Clock clock;

  CandleIngestionService(
      MarketDataProvider provider,
      CandleRepository candles,
      CandleValidator validator,
      Clock clock) {
    this.provider = provider;
    this.candles = candles;
    this.validator = validator;
    this.clock = clock;
  }

  public String providerName() {
    return provider.name();
  }

  public Result ingest(String tradingSymbol, Interval interval, LocalDate from, LocalDate to) {
    CandleRepository.Target target =
        candles
            .shareTarget(EXCHANGE, tradingSymbol, provider.name())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    tradingSymbol
                        + " is not an active NSE company share with a "
                        + provider.name()
                        + " key"));
    return ingest(target, interval, from, to);
  }

  /**
   * Loads daily candles for an index such as NIFTY (benchmarks for relative strength). Same
   * validation and storage as shares.
   */
  public Result ingestIndex(String indexSymbol, LocalDate from, LocalDate to) {
    CandleRepository.Target target =
        candles
            .indexTarget(EXCHANGE, indexSymbol, provider.name())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    indexSymbol + " is not an NSE index with a " + provider.name() + " key"));
    return ingest(target, Interval.DAY, from, to);
  }

  /** Active company shares with a key at the selected provider, in the given series. */
  public List<CandleRepository.Target> universe(List<String> series, int limit) {
    return candles.shareTargets(EXCHANGE, provider.name(), series, limit);
  }

  /** True when the provider can give many instruments' latest session bar in one call. */
  public boolean supportsSessionBars() {
    return provider instanceof SessionBarProvider;
  }

  /** An index such as NIFTY with its key at the selected provider. */
  public CandleRepository.Target indexTarget(String indexSymbol) {
    return candles
        .indexTarget(EXCHANGE, indexSymbol, provider.name())
        .orElseThrow(() ->
            new IllegalArgumentException(
                indexSymbol + " is not an NSE index with a " + provider.name() + " key"));
  }

  /**
   * Stores the bar for {@code session} from quote snapshots (ADR 0025), a few calls for the whole
   * universe. Each bar is validated and stored like any other; a bar dated another session (the
   * instrument did not trade, or the next session has opened) is not stored, and its result has
   * {@code newestDate} other than {@code session} or null.
   *
   * @return one result per target, in the order given
   * @throws IllegalStateException if the provider has no snapshot capability
   */
  public java.util.Map<CandleRepository.Target, Result> ingestSessionBars(
      List<CandleRepository.Target> targets, LocalDate session) {
    if (!(provider instanceof SessionBarProvider snapshots)) {
      throw new IllegalStateException(provider.name() + " has no session-bar snapshots");
    }
    java.util.Map<CandleRepository.Target, Result> out = new java.util.LinkedHashMap<>();
    int size = snapshots.maxKeysPerCall();
    for (int i = 0; i < targets.size(); i += size) {
      List<CandleRepository.Target> chunk = targets.subList(i, Math.min(i + size, targets.size()));
      var bars =
          snapshots.latestSessionBars(
              chunk.stream().map(CandleRepository.Target::providerKey).toList());
      for (CandleRepository.Target t : chunk) {
        Candle bar = bars.get(t.providerKey());
        if (bar == null || !bar.tradingDate().equals(session)) {
          out.put(
              t,
              new Result(
                  t.tradingSymbol(),
                  provider.name(),
                  Interval.DAY,
                  session,
                  session,
                  0,
                  0,
                  bar == null ? null : bar.tradingDate(),
                  List.of()));
          continue;
        }
        out.put(t, store(t, Interval.DAY, session, session, List.of(bar)));
      }
    }
    return out;
  }

  public Result ingest(
      CandleRepository.Target target, Interval interval, LocalDate from, LocalDate to) {
    return store(
        target, interval, from, to, provider.candles(target.providerKey(), interval, from, to));
  }

  private Result store(
      CandleRepository.Target target,
      Interval interval,
      LocalDate from,
      LocalDate to,
      List<Candle> raw) {
    String tradingSymbol = target.tradingSymbol();
    CandleValidator.Cleaned cleaned = validator.dropPlaceholderBars(interval, raw);
    List<Candle> fetched = cleaned.kept();
    QualityReport validated = validator.validate(interval, fetched);
    List<QualityIssue> all = new java.util.ArrayList<>(cleaned.dropped());
    all.addAll(validated.issues());
    QualityReport report = new QualityReport(all);
    int stored = 0;
    if (report.hasErrors()) {
      log.warn(
          "Not storing {} {} {}..{} from {}: {}",
          tradingSymbol,
          interval,
          from,
          to,
          provider.name(),
          report.issues());
    } else {
      candles.upsert(target.instrumentId(), interval, provider.name(), fetched, clock.instant());
      stored = fetched.size();
    }
    return new Result(
        target.tradingSymbol(),
        provider.name(),
        interval,
        from,
        to,
        fetched.size(),
        stored,
        fetched.isEmpty() ? null : fetched.get(fetched.size() - 1).tradingDate(),
        report.issues());
  }

  public List<Candle> stored(
      String tradingSymbol, Interval interval, LocalDate from, LocalDate to) {
    CandleRepository.Target target =
        candles
            .shareTarget(EXCHANGE, tradingSymbol, provider.name())
            .orElseThrow(
                () -> new IllegalArgumentException(tradingSymbol + " is not an active NSE share"));
    return candles.find(target.instrumentId(), interval, provider.name(), from, to);
  }
}
