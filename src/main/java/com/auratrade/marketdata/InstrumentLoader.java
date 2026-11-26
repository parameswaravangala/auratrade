package com.auratrade.marketdata;

import com.auratrade.platform.time.MarketZones;
import java.io.IOException;
import java.io.Reader;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the instrument master: download, parse, sanity-check, then upsert everything in one
 * transaction. Any failure stores nothing (fail closed). Instruments missing from the file keep
 * their older last_seen_on and count as inactive; nothing is deleted.
 */
public class InstrumentLoader {

  private static final Logger log = LoggerFactory.getLogger(InstrumentLoader.class);

  /**
   * @param exchange the only exchange the file may contain, for example NSE
   * @param minRows fewer rows than this means a truncated or wrong file
   * @param maxShrink largest allowed drop in row count since the previous load (0.10 = 10%)
   */
  public record Rules(String exchange, int minRows, double maxShrink) {}

  /**
   * @param tokenMoves symbols whose Kite token now belongs to a different symbol than before. Not
   *     an error (a rename may do this), but worth a look.
   */
  public record Result(
      LocalDate seenOn, int rows, int added, int updated, int notInFile, List<String> tokenMoves) {}

  private final InstrumentSource source;
  private final InstrumentRepository repository;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final Rules rules;

  InstrumentLoader(
      InstrumentSource source,
      InstrumentRepository repository,
      TransactionTemplate tx,
      Clock clock,
      Rules rules) {
    this.source = source;
    this.repository = repository;
    this.tx = tx;
    this.clock = clock;
    this.rules = rules;
  }

  public Result load() {
    List<Instrument> instruments;
    try (Reader reader = source.open()) {
      instruments = InstrumentCsvParser.parse(reader);
    } catch (IOException e) {
      throw new InstrumentFileException("could not read the instrument file: " + e.getMessage(), e);
    }
    LocalDate today = LocalDate.now(clock.withZone(MarketZones.IST));
    Result result = tx.execute(status -> store(instruments, today));
    log.info("Instrument master loaded: {}", result);
    if (!result.tokenMoves().isEmpty()) {
      log.warn("Kite tokens moved between symbols: {}", result.tokenMoves());
    }
    return result;
  }

  private Result store(List<Instrument> instruments, LocalDate today) {
    checkFile(instruments);

    Map<String, Long> existing = repository.tokensBySymbol(rules.exchange());
    repository
        .lastLoadBefore(rules.exchange(), today)
        .ifPresent(previous -> checkShrink(instruments.size(), previous));

    Map<Long, String> previousOwner = new HashMap<>();
    existing.forEach((symbol, token) -> {
      if (token != null) {
        previousOwner.put(token, symbol);
      }
    });

    int added = 0;
    List<String> tokenMoves = new ArrayList<>();
    Set<String> inFile = new HashSet<>();
    for (Instrument i : instruments) {
      inFile.add(i.tradingSymbol());
      if (!existing.containsKey(i.tradingSymbol())) {
        added++;
      }
      String owner = previousOwner.get(i.kiteInstrumentToken());
      if (owner != null && !owner.equals(i.tradingSymbol())) {
        tokenMoves.add(owner + " -> " + i.tradingSymbol());
      }
    }
    int notInFile = (int) existing.keySet().stream().filter(s -> !inFile.contains(s)).count();

    repository.upsertAll(instruments, today);
    return new Result(
        today,
        instruments.size(),
        added,
        instruments.size() - added,
        notInFile,
        List.copyOf(tokenMoves));
  }

  private void checkFile(List<Instrument> instruments) {
    if (instruments.size() < rules.minRows()) {
      throw new InstrumentFileException(
          "only " + instruments.size() + " rows, expected at least " + rules.minRows());
    }
    Set<Long> tokens = new HashSet<>();
    Set<String> symbols = new HashSet<>();
    for (Instrument i : instruments) {
      if (!Objects.equals(i.exchange(), rules.exchange())) {
        throw new InstrumentFileException(
            i.tradingSymbol() + " is on " + i.exchange() + ", expected " + rules.exchange());
      }
      if (!symbols.add(i.tradingSymbol())) {
        throw new InstrumentFileException("symbol " + i.tradingSymbol() + " appears twice");
      }
      if (!tokens.add(i.kiteInstrumentToken())) {
        throw new InstrumentFileException(
            "Kite token " + i.kiteInstrumentToken() + " appears twice in one file");
      }
    }
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
