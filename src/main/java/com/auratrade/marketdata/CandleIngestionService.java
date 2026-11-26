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
   */
  public record Result(
      String symbol,
      String source,
      Interval interval,
      LocalDate from,
      LocalDate to,
      int fetched,
      int stored,
      List<QualityIssue> issues) {}

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
    List<Candle> fetched = provider.candles(target.providerKey(), interval, from, to);
    QualityReport report = validator.validate(interval, fetched);
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
