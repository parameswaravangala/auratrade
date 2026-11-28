package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketRegimeTest {

  private static List<BigDecimal> line(int n, double start, double step) {
    List<BigDecimal> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(BigDecimal.valueOf(start + step * i));
    }
    return out;
  }

  @Test
  void aRisingIndexIsBullish() {
    assertThat(MarketRegime.classify(line(260, 100, 0.5)))
        .hasValueSatisfying(s -> {
          assertThat(s.regime()).isEqualTo(MarketRegime.Regime.BULLISH);
          assertThat(s.score()).isEqualByComparingTo("1");
        });
  }

  @Test
  void aFallingIndexIsBearish() {
    assertThat(MarketRegime.classify(line(260, 300, -0.5)))
        .hasValueSatisfying(s -> {
          assertThat(s.regime()).isEqualTo(MarketRegime.Regime.BEARISH);
          assertThat(s.score()).isEqualByComparingTo("0");
        });
  }

  @Test
  void aFlatIndexIsSidewaysNotBearish() {
    assertThat(MarketRegime.classify(line(260, 100, 0)))
        .hasValueSatisfying(s -> {
          assertThat(s.regime()).isEqualTo(MarketRegime.Regime.SIDEWAYS);
          assertThat(s.score()).isEqualByComparingTo("0.5");
        });
  }

  @Test
  void tooLittleHistoryIsUnknownNotAGuess() {
    assertThat(MarketRegime.classify(line(MarketRegime.MIN_HISTORY - 1, 100, 0.5))).isEmpty();
  }

  @Test
  void theEvidenceNamesTheIndexLevels() {
    assertThat(MarketRegime.classify(line(260, 100, 0.5)).orElseThrow().evidence())
        .contains("NIFTY close", "EMA50", "EMA200", "rising");
  }

  @Test
  void aContextWithoutAnIndexHasNoRegime() {
    assertThat(
            MarketRegime.snapshot(
                new StrategyContext(java.time.LocalDate.of(2026, 1, 1), Optional.empty())))
        .isEmpty();
  }
}
