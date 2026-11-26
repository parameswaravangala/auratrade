package com.auratrade.api;

import com.auratrade.marketdata.InstrumentFileException;
import com.auratrade.marketdata.InstrumentLoader;
import com.auratrade.marketdata.InstrumentRepository;
import com.auratrade.marketdata.InstrumentRepository.SeriesCount;
import com.auratrade.marketdata.Resolution;
import com.auratrade.marketdata.SymbolResolverService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoints for the instrument master and the symbol resolver. Loading is manual for now;
 * a daily schedule comes with the staleness-alert slice.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev/marketdata")
class MarketDataDevController {

  private final InstrumentLoader loader;
  private final InstrumentRepository instruments;
  private final SymbolResolverService resolver;

  MarketDataDevController(
      InstrumentLoader loader, InstrumentRepository instruments, SymbolResolverService resolver) {
    this.loader = loader;
    this.instruments = instruments;
    this.resolver = resolver;
  }

  /** Downloads the instrument file from the configured feed, upserts it, rebuilds the resolver. */
  @PostMapping("/instruments/load")
  InstrumentLoader.Result load() {
    InstrumentLoader.Result result = loader.load();
    resolver.refresh();
    return result;
  }

  /** Active and inactive instrument counts per series. */
  @GetMapping("/instruments/series")
  List<SeriesCount> series() {
    return instruments.seriesCounts("NSE");
  }

  /** Resolves a company name to an NSE share, for example ?name=Acme Cookers Ltd. */
  @GetMapping("/resolve")
  Resolution resolve(@RequestParam String name) {
    return resolver.resolve(name);
  }

  record AliasRequest(String alias, String symbol, String note) {}

  /** Adds or repoints a hand-curated alias. The symbol must be an active company share. */
  @PostMapping("/aliases")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addAlias(@RequestBody AliasRequest request) {
    resolver.addAlias(request.alias(), request.symbol(), request.note());
  }

  @ExceptionHandler(InstrumentFileException.class)
  ProblemDetail badFile(InstrumentFileException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
