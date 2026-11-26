package com.auratrade.marketdata;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds an in-memory {@link SymbolResolver} built from the database, about 3,600 shares. Built on
 * first use; call {@link #refresh()} after an instrument load. Adding an alias refreshes it.
 */
public class SymbolResolverService {

  private static final Logger log = LoggerFactory.getLogger(SymbolResolverService.class);
  private static final String EXCHANGE = "NSE";

  private final ResolverRepository repository;
  private final Clock clock;
  private final AtomicReference<SymbolResolver> current = new AtomicReference<>();

  SymbolResolverService(ResolverRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public Resolution resolve(String name) {
    SymbolResolver resolver = current.get();
    if (resolver == null) {
      resolver = refresh();
    }
    return resolver.resolve(name);
  }

  /** Rebuilds the resolver from the latest instrument load and aliases. Returns the new one. */
  public SymbolResolver refresh() {
    SymbolResolver resolver =
        new SymbolResolver(repository.activeShares(EXCHANGE), repository.aliases());
    current.set(resolver);
    log.info("Symbol resolver built with {} active shares", resolver.size());
    return resolver;
  }

  /**
   * Maps a name to an active share by hand. Fails if the symbol is not an active company share.
   *
   * @throws IllegalArgumentException if the alias is empty or the symbol is not an active share
   */
  public void addAlias(String alias, String tradingSymbol, String note) {
    String key = NameNormalizer.key(alias == null ? "" : alias);
    if (key.isEmpty() || alias.length() > SymbolResolver.MAX_INPUT_LENGTH) {
      throw new IllegalArgumentException("alias must have letters or digits and be short");
    }
    long id =
        repository
            .activeShareId(EXCHANGE, tradingSymbol)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    tradingSymbol + " is not an active NSE company share"));
    repository.saveAlias(key, alias.trim(), id, note, clock.instant());
    refresh();
  }
}
