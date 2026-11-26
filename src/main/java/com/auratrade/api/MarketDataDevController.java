package com.auratrade.api;

import com.auratrade.marketdata.InstrumentFileException;
import com.auratrade.marketdata.InstrumentLoader;
import com.auratrade.marketdata.InstrumentRepository;
import com.auratrade.marketdata.InstrumentRepository.SeriesCount;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoints for the instrument master. Loading is manual for now; a daily schedule comes
 * with the staleness-alert slice.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev/marketdata")
class MarketDataDevController {

  private final InstrumentLoader loader;
  private final InstrumentRepository instruments;

  MarketDataDevController(InstrumentLoader loader, InstrumentRepository instruments) {
    this.loader = loader;
    this.instruments = instruments;
  }

  /** Downloads the Kite NSE instruments file and upserts it. */
  @PostMapping("/instruments/load")
  InstrumentLoader.Result load() {
    return loader.load();
  }

  /** Active and inactive instrument counts per series. */
  @GetMapping("/instruments/series")
  List<SeriesCount> series() {
    return instruments.seriesCounts("NSE");
  }

  @ExceptionHandler(InstrumentFileException.class)
  ProblemDetail badFile(InstrumentFileException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
  }
}
