package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import com.auratrade.screener.Screen;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Synthetic series; checks the replay rules, not any real screen. */
class ScreenReplayTest {

  static final LocalDate D0 = LocalDate.parse("2025-01-01");

  /** open = close = given price, high/low = price +/- 1. */
  static Series series(String symbol, double... prices) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < prices.length; i++) {
      BigDecimal p = BigDecimal.valueOf(prices[i]);
      candles.add(
          Candle.daily(
              D0.plusDays(i), p, p.add(BigDecimal.ONE), p.subtract(BigDecimal.ONE), p, 1000));
    }
    return new Series(1, symbol, symbol, candles);
  }

  /** Matches on one date and records the last date it was ever shown. */
  static final class Recording implements Screen {
    final LocalDate matchOn;
    final List<LocalDate> seenLastDates = new ArrayList<>();
    final int lookback;

    Recording(LocalDate matchOn, int lookback) {
      this.matchOn = matchOn;
      this.lookback = lookback;
    }

    @Override
    public String name() {
      return "recording";
    }

    @Override
    public String description() {
      return "test";
    }

    @Override
    public int lookback() {
      return lookback;
    }

    @Override
    public Optional<Hit> evaluate(Series s, Context context) {
      seenLastDates.add(s.lastDate());
      assertThat(s.candles()).hasSize(lookback);
      return s.lastDate().equals(matchOn)
          ? Optional.of(new Hit(s.symbol(), s.name(), BigDecimal.ONE, java.util.Map.of(), "test"))
          : Optional.empty();
    }
  }

  private static final ScreenReplay.Guard ALLOW = (w, d, n) -> null;

  @Test
  void theScreenNeverSeesCandlesAfterTheSampleDate() {
    var share = series("ACME", 10, 11, 12, 13, 14, 15, 16, 17);
    var screen = new Recording(D0.plusDays(3), 2);
    Set<LocalDate> dates = Set.of(D0.plusDays(1), D0.plusDays(3), D0.plusDays(5));

    ScreenReplay.replay(
        share, series("NIFTY", 1, 1, 1, 1, 1, 1, 1, 1), List.of(screen), dates, List.of(2), ALLOW);

    assertThat(screen.seenLastDates)
        .containsExactly(D0.plusDays(1), D0.plusDays(3), D0.plusDays(5));
  }

  @Test
  void entersAtTheNextOpenAndExitsAtTheCloseHSessionsLater() {
    // Signal on day 3 (close 13). Entry: open of day 4 = 14. h = 2: exit close of day 5 = 15.
    // Return 15/14 - 1. Benchmark: open day 4 = 100, close day 5 = 101 -> +1%.
    var share = series("ACME", 10, 11, 12, 13, 14, 15, 16);
    var bench = series("NIFTY", 100, 100, 100, 100, 100, 101, 101);
    var screen = new Recording(D0.plusDays(3), 2);

    var out =
        ScreenReplay.replay(
            share, bench, List.of(screen), Set.of(D0.plusDays(3)), List.of(2), ALLOW);

    assertThat(out.samples())
        .singleElement()
        .satisfies(s -> {
          assertThat(s.ret())
              .isEqualByComparingTo(
                  new BigDecimal("15")
                      .divide(new BigDecimal("14"), com.auratrade.screener.indicators.Indicators.MC)
                      .subtract(BigDecimal.ONE));
          assertThat(s.excess()).isEqualByComparingTo(s.ret().subtract(new BigDecimal("0.01")));
        });
  }

  @Test
  void horizonsPastTheEndOfDataAreSkipped() {
    var share = series("ACME", 10, 11, 12, 13);
    var screen = new Recording(D0.plusDays(2), 2);

    var out =
        ScreenReplay.replay(
            share,
            series("NIFTY", 1, 1, 1, 1),
            List.of(screen),
            Set.of(D0.plusDays(2)),
            List.of(1, 5),
            ALLOW);

    assertThat(out.samples()).extracting(ScreenReplay.Sample::horizon).containsExactly(1);
  }

  @Test
  void holdingPeriodsWithASplitSizedMoveAreDroppedAndCounted() {
    var share = series("ACME", 100, 100, 100, 100, 50, 50); // halves on day 4: unadjusted 1:2 split
    var screen = new Recording(D0.plusDays(2), 2);

    var out =
        ScreenReplay.replay(
            share,
            series("NIFTY", 1, 1, 1, 1, 1, 1),
            List.of(screen),
            Set.of(D0.plusDays(2)),
            List.of(3),
            ALLOW);

    assertThat(out.samples()).isEmpty();
    assertThat(out.droppedCorporateAction()).containsEntry("recording", 1);
  }

  @Test
  void theGuardCanVetoASampleDate() {
    var share = series("ACME", 10, 11, 12, 13, 14);
    var screen = new Recording(D0.plusDays(2), 2);

    var out =
        ScreenReplay.replay(
            share,
            series("NIFTY", 1, 1, 1, 1, 1),
            List.of(screen),
            Set.of(D0.plusDays(2)),
            List.of(1),
            (w, d, n) -> "ILLIQUID");

    assertThat(out.samples()).isEmpty();
    assertThat(screen.seenLastDates).isEmpty();
  }
}
