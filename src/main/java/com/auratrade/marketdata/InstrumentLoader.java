package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the instrument master from any {@link InstrumentFeed}. Provider-neutral: it knows ISINs,
 * series and provider keys, never a provider's file format (ADR 0015).
 *
 * <p>Fail closed: the whole file is checked first (exchange, unique ISINs and provider keys, row
 * count, no large shrink since the previous load). A fault in a single row, such as a bad ISIN
 * check digit, rejects only that row and is reported, unless too many rows are rejected. Then all
 * accepted rows are upserted in one transaction. Instruments missing from the file keep their older
 * last_seen_on and count as inactive. Identity is the ISIN, so a symbol change updates the same row
 * and is reported.
 */
public class InstrumentLoader {

  private static final Logger log = LoggerFactory.getLogger(InstrumentLoader.class);

  /**
   * @param exchange the only exchange the feed may contain, for example NSE
   * @param minRows fewer accepted rows than this means a truncated or wrong file
   * @param maxShrink largest allowed drop in row count since the previous load (0.10 = 10%)
   * @param maxRejected more rejected rows than this means the file itself is wrong, so the whole
   *     load fails
   */
  public record Rules(String exchange, int minRows, double maxShrink, int maxRejected) {}

  /**
   * @param rows rows accepted and stored
   * @param renamed instruments whose symbol changed since the last load, as "OLD -> NEW (ISIN)"
   * @param rejected rows skipped for a row-level fault, as "SYMBOL (provider key): reason"
   */
  public record Result(
      String provider,
      LocalDate seenOn,
      int rows,
      int added,
      int updated,
      int notInFile,
      List<String> renamed,
      List<String> rejected) {}

  private final InstrumentFeed feed;
  private final InstrumentRepository repository;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final Rules rules;

  InstrumentLoader(
      InstrumentFeed feed,
      InstrumentRepository repository,
      TransactionTemplate tx,
      Clock clock,
      Rules rules) {
    this.feed = feed;
    this.repository = repository;
    this.tx = tx;
    this.clock = clock;
    this.rules = rules;
  }

  public Result load() {
    List<Instrument> instruments;
    try {
      instruments = feed.fetch();
    } catch (IOException e) {
      throw new InstrumentFileException(
          "could not read the " + feed.provider() + " instrument file: " + e.getMessage(), e);
    }
    List<String> rejected = new ArrayList<>();
    List<Instrument> accepted = checkFile(instruments, rejected);
    LocalDate today = LocalDate.now(clock.withZone(MarketZones.IST));
    Result result = tx.execute(status -> store(accepted, rejected, today));
    log.info(
        "Instrument master loaded from {}: {} rows, {} added, {} not in file, {} renamed,"
            + " {} rejected",
        result.provider(),
        result.rows(),
        result.added(),
        result.notInFile(),
        result.renamed().size(),
        result.rejected().size());
    if (!result.renamed().isEmpty()) {
      log.warn("Instruments renamed since the last load: {}", result.renamed());
    }
    if (!result.rejected().isEmpty()) {
      log.warn("Instrument rows rejected: {}", result.rejected());
    }
    return result;
  }

  /** A fault in one row: the row is skipped and reported, the rest of the file still loads. */
  private static String rowFault(Instrument i) {
    if (!i.isIndex() && !Isin.isValid(i.isin())) {
      return "invalid ISIN '" + i.isin() + "'";
    }
    if (i.tickSize() == null || i.tickSize().signum() < 0 || i.lotSize() < 0) {
      return "negative or missing tick size or lot size";
    }
    return null;
  }

  /** Identity used to match file rows to stored rows. */
  static String identity(Instrument i) {
    return i.isIndex() ? "INDEX|" + i.tradingSymbol() : "ISIN|" + i.isin();
  }

  private Result store(List<Instrument> instruments, List<String> rejected, LocalDate today) {
    repository
        .lastLoadBefore(rules.exchange(), today)
        .ifPresent(previous -> checkShrink(instruments.size(), previous));

    Map<String, String> existing = repository.symbolsByIdentity(rules.exchange());
    int added = 0;
    List<String> renamed = new ArrayList<>();
    Set<String> inFile = new HashSet<>();
    for (Instrument i : instruments) {
      String id = identity(i);
      inFile.add(id);
      String oldSymbol = existing.get(id);
      if (oldSymbol == null) {
        added++;
      } else if (!oldSymbol.equals(i.tradingSymbol())) {
        renamed.add(oldSymbol + " -> " + i.tradingSymbol() + " (" + i.isin() + ")");
      }
    }
    int notInFile = (int) existing.keySet().stream().filter(id -> !inFile.contains(id)).count();

    repository.upsertAll(instruments, feed.provider(), today);
    return new Result(
        feed.provider(),
        today,
        instruments.size(),
        added,
        instruments.size() - added,
        notInFile,
        List.copyOf(renamed),
        List.copyOf(rejected));
  }

  /**
   * Checks the whole file. File-level faults (wrong exchange, duplicates, too many rejected or too
   * few accepted rows) fail the load; row-level faults reject only that row. Returns accepted rows.
   */
  private List<Instrument> checkFile(List<Instrument> instruments, List<String> rejected) {
    List<Instrument> accepted = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    Set<String> providerKeys = new HashSet<>();
    Set<String> seriesSymbols = new HashSet<>();
    for (Instrument i : instruments) {
      String where = i.tradingSymbol() + " (" + i.providerKey() + ")";
      if (!Objects.equals(i.exchange(), rules.exchange())) {
        throw new InstrumentFileException(
            where + " is on " + i.exchange() + ", expected " + rules.exchange());
      }
      String fault = rowFault(i);
      if (fault != null) {
        rejected.add(where + ": " + fault);
        continue;
      }
      if (!identities.add(identity(i))) {
        throw new InstrumentFileException(where + ": " + identity(i) + " appears twice");
      }
      if (!providerKeys.add(i.providerKey())) {
        throw new InstrumentFileException("provider key " + i.providerKey() + " appears twice");
      }
      if (!seriesSymbols.add(i.series() + "|" + i.tradingSymbol())) {
        throw new InstrumentFileException(
            "symbol " + i.tradingSymbol() + " appears twice in series " + i.series());
      }
      accepted.add(i);
    }
    if (rejected.size() > rules.maxRejected()) {
      throw new InstrumentFileException(
          rejected.size()
              + " rows rejected, more than the "
              + rules.maxRejected()
              + " allowed; first: "
              + rejected.get(0));
    }
    if (accepted.size() < rules.minRows()) {
      throw new InstrumentFileException(
          "only " + accepted.size() + " valid rows, expected at least " + rules.minRows());
    }
    return accepted;
  }

  private void checkShrink(int rows, LocalDate previous) {
    int before = repository.countSeenOn(rules.exchange(), previous);
    if (rows < before * (1 - rules.maxShrink())) {
      throw new InstrumentFileException(
          "file has "
              + rows
              + " rows but the load on "
              + previous
              + " had "
              + before
              + "; refusing a drop of more than "
              + Math.round(rules.maxShrink() * 100)
              + "%");
    }
  }
}
