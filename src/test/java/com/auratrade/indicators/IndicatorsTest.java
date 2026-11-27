package com.auratrade.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every expected value is worked by hand in the comment next to it. */
class IndicatorsTest {

  private static List<BigDecimal> of(String... values) {
    return Arrays.stream(values).map(BigDecimal::new).toList();
  }

  @Test
  void smaAveragesTheLastN() {
    // (3 + 4 + 5) / 3 = 4
    assertThat(Indicators.sma(of("1", "2", "3", "4", "5"), 3))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("4"));
    assertThat(Indicators.sma(of("1", "2"), 3)).isEmpty();
  }

  @Test
  void emaSeedsWithSmaThenSmooths() {
    // n = 3, k = 2/4 = 0.5. Seed SMA(1,2,3) = 2. Next 4: 2 + 0.5*(4-2) = 3. Next 5: 3 + 0.5*2 = 4.
    assertThat(Indicators.ema(of("1", "2", "3", "4", "5"), 3))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("4"));
  }

  @Test
  void rsiUsesWilderSmoothing() {
    // n = 2. Changes: +2, -1, +2. First avg gain = (2+0)/2 = 1, avg loss = (0+1)/2 = 0.5.
    // Next change +2: gain = (1*1 + 2)/2 = 1.5, loss = (0.5*1 + 0)/2 = 0.25. RS = 6.
    // RSI = 100 - 100/7 = 85.714285...
    var rsi = Indicators.rsi(of("10", "12", "11", "13"), 2).orElseThrow();
    assertThat(rsi.setScale(4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("85.7143");
  }

  @Test
  void rsiIs100WithoutLossesAndNeedsNPlusOneValues() {
    assertThat(Indicators.rsi(of("1", "2", "3"), 2))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("100"));
    assertThat(Indicators.rsi(of("1", "2"), 2)).isEmpty();
  }

  @Test
  void atrUsesTrueRangeAndWilderSmoothing() {
    // Bars (h,l,c): (10,8,9) (11,9,10) (12,10,11) (15,12,14)
    // TR1 = max(2, |11-9|, |9-9|) = 2; TR2 = max(2, 2, 0) = 2; TR3 = max(3, |15-11|, |12-11|) = 4
    // n = 2: first ATR = (2+2)/2 = 2; next = (2*1 + 4)/2 = 3.
    var atr =
        Indicators.atr(
                of("10", "11", "12", "15"), of("8", "9", "10", "12"), of("9", "10", "11", "14"), 2)
            .orElseThrow();
    assertThat(atr).isEqualByComparingTo("3");
  }

  @Test
  void highestAndLowestCanSkipTheLatestValues() {
    var v = of("5", "9", "7", "3", "12");
    assertThat(Indicators.highest(v, 4, 1))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("9"));
    assertThat(Indicators.highest(v, 2, 0))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("12"));
    assertThat(Indicators.lowest(v, 3, 1))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("3"));
    assertThat(Indicators.highest(v, 5, 1)).isEmpty();
  }

  @Test
  void changeAndLargestMove() {
    var v = of("100", "110", "99", "198");
    // change over 3 periods: 198/100 - 1 = 0.98
    assertThat(Indicators.change(v, 3))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("0.98"));
    // moves: +10%, -10%, +100%: largest is 1
    assertThat(Indicators.largestMove(v, 3))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("1"));
  }

  @Test
  void ratioToAverageComparesTheLastValueWithThePreviousN() {
    // previous 3 values 100, 200, 300 average 200; last 500 / 200 = 2.5. The 10 is outside n.
    assertThat(Indicators.ratioToAverage(of("10", "100", "200", "300", "500"), 3))
        .hasValueSatisfying(x -> assertThat(x).isEqualByComparingTo("2.5"));
    assertThat(Indicators.ratioToAverage(of("100", "200", "300"), 3)).isEmpty();
    assertThat(Indicators.ratioToAverage(of("0", "0", "5"), 2)).isEmpty();
  }

  @Test
  void swingHighsNeedStrengthBarsOnBothSides() {
    // index:   0  1  2  3  4  5  6  7  8
    // values:  1  3  2  1  5  4  3  6  2
    // strength 2: i=4 is a swing high (5 > 2 and 1 before; 5 >= 4 and 3 after).
    // i=1 lacks two bars before; i=7 lacks two bars after (not yet confirmed).
    var swings = Indicators.swingHighs(of("1", "3", "2", "1", "5", "4", "3", "6", "2"), 2);
    assertThat(swings).extracting(Indicators.Swing::index).containsExactly(4);
    assertThat(swings.get(0).value()).isEqualByComparingTo("5");
  }

  @Test
  void ofTwoEqualPeaksOnlyTheFirstIsASwing() {
    // i=2 and i=3 both 5. i=2: above 1, 2 before; >= 5, 1 after: swing. i=3: not above i=2.
    var swings = Indicators.swingHighs(of("1", "2", "5", "5", "1", "0"), 2);
    assertThat(swings).extracting(Indicators.Swing::index).containsExactly(2);
  }

  @Test
  void swingLowsAreTheMirrorImage() {
    // lows: 9 7 8 9 4 6 7 3 5. strength 2: i=4 (4 < 8,9 before; <= 6,7 after).
    var lows = of("9", "7", "8", "9", "4", "6", "7", "3", "5");
    assertThat(Indicators.swingLows(lows, 2))
        .extracting(Indicators.Swing::index)
        .containsExactly(4);
    assertThat(Indicators.lastSwingLow(lows, 2))
        .hasValueSatisfying(s -> assertThat(s.value()).isEqualByComparingTo("4"));
    assertThat(Indicators.lastSwingLow(of("1", "2"), 2)).isEmpty();
    assertThat(Indicators.swingLows(lows, 0)).isEmpty();
  }

  @Test
  void higherHighsAndLowsComparesTheLastTwoSwingsOfEach() {
    // strength 1. highs swing at i=1 (5) and i=3 (6): higher high.
    // lows swing at i=2 (2) and i=4 (3): higher low.
    var highs = of("4", "5", "4", "6", "5", "7");
    var lows = of("3", "4", "2", "5", "3", "6");
    assertThat(Indicators.higherHighsAndLows(highs, lows, 1)).contains(true);
    // lower second high: highs swing at i=1 (6) and i=3 (5).
    var lowerHighs = of("4", "6", "4", "5", "4", "7");
    assertThat(Indicators.higherHighsAndLows(lowerHighs, lows, 1)).contains(false);
    // only one swing high: unknown.
    assertThat(Indicators.higherHighsAndLows(of("1", "2", "3", "4"), lows.subList(0, 4), 1))
        .isEmpty();
  }
}
