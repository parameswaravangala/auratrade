package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleValidatorTest {

  private final TradingCalendar calendar = TestCalendars.fixture();
  private final CandleValidator validator = new CandleValidator(calendar, QualityRules.defaults());

  private static Candle day(String date, String o, String h, String l, String c, long volume) {
    return Candle.daily(
        LocalDate.parse(date),
        new BigDecimal(o),
        new BigDecimal(h),
        new BigDecimal(l),
        new BigDecimal(c),
        volume);
  }

  private QualityReport check(Candle... candles) {
    return validator.validate(Interval.DAY, List.of(candles));
  }

  @Test
  void aSyntheticSeriesIsClean() {
    List<Candle> series = SyntheticCandles.daily(calendar, LocalDate.parse("2026-01-05"), 60, 7L);

    QualityReport report = validator.validate(Interval.DAY, series);

    assertThat(report.isClean()).as(report.issues().toString()).isTrue();
  }

  @Test
  void theSameSeedGivesTheSameSeries() {
    var a = SyntheticCandles.daily(calendar, LocalDate.parse("2026-01-05"), 10, 42L);
    var b = SyntheticCandles.daily(calendar, LocalDate.parse("2026-01-05"), 10, 42L);

    assertThat(a).isEqualTo(b);
  }

  @Test
  void flagsInconsistentOhlc() {
    var report = check(day("2026-01-23", "100", "99", "95", "98", 10)); // high below open

    assertThat(report.hasErrors()).isTrue();
    assertThat(report.count("OHLC_INCONSISTENT")).isEqualTo(1);
  }

  @Test
  void flagsNonPositivePricesAndNegativeVolume() {
    var report = check(day("2026-01-23", "0", "10", "0", "5", -1));

    assertThat(report.count("NON_POSITIVE_PRICE")).isEqualTo(1);
    assertThat(report.count("NEGATIVE_VOLUME")).isEqualTo(1);
  }

  @Test
  void flagsDuplicatesAndOutOfOrderCandles() {
    var a = day("2026-01-23", "100", "101", "99", "100", 10);
    var b = day("2026-01-27", "100", "101", "99", "100", 10);

    assertThat(check(a, a).count("DUPLICATE_TIMESTAMP")).isEqualTo(1);
    assertThat(check(b, a).count("OUT_OF_ORDER")).isEqualTo(1);
  }

  @Test
  void flagsCandlesOnClosedDays() {
    var report = check(day("2026-01-26", "100", "101", "99", "100", 10)); // fixture holiday

    assertThat(report.count("NOT_TRADING_DAY")).isEqualTo(1);
    assertThat(report.hasErrors()).isTrue();
  }

  @Test
  void warnsAboutMissingTradingDaysButNotWeekends() {
    var fri = day("2026-01-23", "100", "101", "99", "100", 10);
    var tueNext =
        day("2026-01-27", "100", "101", "99", "100", 10); // Mon was a holiday: nothing missing
    var thu = day("2026-01-29", "100", "101", "99", "100", 10); // Wed 28 missing

    assertThat(check(fri, tueNext).isClean()).isTrue();
    var report = check(fri, tueNext, thu);
    assertThat(report.count("MISSING_TRADING_DAY")).isEqualTo(1);
    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void warnsAboutExtremeMoves() {
    var a = day("2026-01-27", "100", "101", "99", "100", 10);
    var b = day("2026-01-28", "100", "151", "99", "150", 10); // +50%

    var report = check(a, b);

    assertThat(report.count("EXTREME_MOVE")).isEqualTo(1);
    assertThat(report.hasErrors()).isFalse();
  }

  @Test
  void warnsWhenPricesAreFlatWithNoVolumeForDays() {
    List<Candle> flat = new ArrayList<>();
    LocalDate d = LocalDate.parse("2026-02-02"); // Monday
    for (int i = 0; i < 5; i++) {
      flat.add(Candle.daily(d, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 0));
      d = calendar.nextTradingDay(d);
    }

    var report = validator.validate(Interval.DAY, flat);

    assertThat(report.count("STALE_PRICE")).isEqualTo(1);
  }

  @Test
  void intradayCandlesMustSitInsideTheSession() {
    var inside =
        new Candle(
            java.time.LocalDateTime.parse("2026-01-23T10:00:00")
                .atZone(com.auratrade.platform.time.MarketZones.IST)
                .toInstant(),
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.TEN,
            1);
    var outside =
        new Candle(
            java.time.LocalDateTime.parse("2026-01-23T08:00:00")
                .atZone(com.auratrade.platform.time.MarketZones.IST)
                .toInstant(),
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.TEN,
            1);

    assertThat(validator.validate(Interval.MINUTE_1, List.of(inside)).isClean()).isTrue();
    var report = validator.validate(Interval.MINUTE_1, List.of(inside, outside));
    assertThat(report.count("OUTSIDE_SESSION")).isEqualTo(1);
  }

  @Test
  void validationFailsClosedForAnUncoveredYear() {
    var later = day("2027-01-04", "100", "101", "99", "100", 10);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> check(later))
        .isInstanceOf(CalendarNotCoveredException.class);
  }
}
