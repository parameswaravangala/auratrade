package com.auratrade.screener;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.marketdata.Candle;
import com.auratrade.marketdata.CandleSeriesReader.Series;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Hand-shaped synthetic series; no exchange data. */
class ScreensTest {

  static final LocalDate START = LocalDate.parse("2025-01-01");

  /** Daily candles from a list of closes; high = close + 1, low = close - 1, open = close. */
  static Series series(List<BigDecimal> closes, List<Long> volumes) {
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < closes.size(); i++) {
      BigDecimal c = closes.get(i);
      candles.add(
          Candle.daily(
              START.plusDays(i),
              c,
              c.add(BigDecimal.ONE),
              c.subtract(BigDecimal.ONE),
              c,
              volumes.get(i)));
    }
    return new Series(1, "TESTCO", "TEST COMPANY", candles);
  }

  static List<BigDecimal> constant(int n, String value) {
    return new ArrayList<>(java.util.Collections.nCopies(n, new BigDecimal(value)));
  }

  static List<Long> volumes(int n, long value) {
    return new ArrayList<>(java.util.Collections.nCopies(n, value));
  }

  static Screen.Context context(Series s, Series bench) {
    return new Screen.Context(s.lastDate(), Optional.ofNullable(bench));
  }

  @Test
  void breakoutNeedsANew52WeekHighOnHeavyVolume() {
    List<BigDecimal> closes = constant(252, "100"); // highs 101
    closes.add(new BigDecimal("105"));
    List<Long> vols = volumes(252, 1000);
    vols.add(3000L);

    var hit =
        new Screens.Breakout().evaluate(series(closes, vols), context(series(closes, vols), null));

    assertThat(hit).isPresent();
    assertThat(hit.get().score()).isEqualByComparingTo("3"); // 3000 / 1000
    assertThat(hit.get().metrics().get("prior52wHigh")).isEqualByComparingTo("101");

    vols.set(252, 1500L); // only 1.5x
    assertThat(
            new Screens.Breakout()
                .evaluate(series(closes, vols), context(series(closes, vols), null)))
        .isEmpty();
  }

  @Test
  void pullbackMatchesAnUptrendPausingAtIts50DayAverage() {
    // 150 days rising 100 -> 249, then 51 days alternating 250 / 249 (flat, RSI near 50).
    List<BigDecimal> closes = new ArrayList<>();
    for (int i = 0; i < 150; i++) {
      closes.add(BigDecimal.valueOf(100 + i));
    }
    for (int i = 0; i < 51; i++) {
      closes.add(BigDecimal.valueOf(i % 2 == 0 ? 250 : 249));
    }
    Series s = series(closes, volumes(closes.size(), 1000));

    var hit = new Screens.Pullback().evaluate(s, context(s, null));

    assertThat(hit).isPresent();
    assertThat(hit.get().metrics().get("rsi14"))
        .isBetween(new BigDecimal("40"), new BigDecimal("55"));
  }

  @Test
  void pullbackRejectsADowntrend() {
    List<BigDecimal> closes = new ArrayList<>();
    for (int i = 0; i < 201; i++) {
      closes.add(BigDecimal.valueOf(400 - i));
    }
    Series s = series(closes, volumes(closes.size(), 1000));

    assertThat(new Screens.Pullback().evaluate(s, context(s, null))).isEmpty();
  }

  @Test
  void relativeStrengthComparesSixMonthReturnsWithTheBenchmark() {
    // Share: 100 for 126 days then 150 on the last day (+50%). Benchmark: 100 -> 110 (+10%).
    List<BigDecimal> share = constant(126, "100");
    share.add(new BigDecimal("150"));
    List<BigDecimal> bench = constant(126, "100");
    bench.add(new BigDecimal("110"));
    Series s = series(share, volumes(127, 1000));
    Series b = series(bench, volumes(127, 0));

    var hit = new Screens.RelativeStrength().evaluate(s, context(s, b));

    assertThat(hit).isPresent();
    // (1.5 / 1.1) - 1 = 0.363636...
    assertThat(hit.get().score()).isEqualByComparingTo("0.3636");
    assertThat(hit.get().metrics().get("benchmark6mPct")).isEqualByComparingTo("10");

    List<BigDecimal> laggard = constant(126, "100");
    laggard.add(new BigDecimal("105"));
    Series l = series(laggard, volumes(127, 1000));
    assertThat(new Screens.RelativeStrength().evaluate(l, context(l, b))).isEmpty();
  }

  @Test
  void guardsSkipStaleShortIlliquidAndSplitSizedSeries() {
    var service =
        new ScreenerService(
            null, null, Screens.all(), List.of("EQ"), new BigDecimal("10000000"), "NIFTY");
    // 130 days at 1,000 with 20,000 shares a day = 2 crore traded per day: liquid.
    Series liquid = series(constant(130, "1000"), volumes(130, 20_000));
    LocalDate asOf = liquid.lastDate();

    assertThat(service.guard(liquid, asOf, 127)).isNull();
    assertThat(service.guard(liquid, asOf.plusDays(1), 127)).isEqualTo("STALE");
    assertThat(service.guard(liquid, asOf, 253)).isEqualTo("SHORT_HISTORY");

    Series thin = series(constant(130, "1000"), volumes(130, 100));
    assertThat(service.guard(thin, asOf, 127)).isEqualTo("ILLIQUID");

    List<BigDecimal> split = constant(129, "1000");
    split.add(new BigDecimal("500")); // a 1:2 split, not adjusted
    assertThat(service.guard(series(split, volumes(130, 20_000)), asOf, 127))
        .isEqualTo("POSSIBLE_CORPORATE_ACTION");
  }
}
