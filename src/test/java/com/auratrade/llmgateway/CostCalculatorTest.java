package com.auratrade.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

  private final ModelPrice sonnet =
      new ModelPrice(
          new BigDecimal("2.00"),
          new BigDecimal("10.00"),
          new BigDecimal("0.10"),
          new BigDecimal("2.50"));

  @Test
  void pricesEachTokenTypeSeparately() {
    // 6K cache write at 2.50 = 0.015, 24K cache read at 0.10 = 0.0024
    var usage = new TokenUsage(0, 0, 24_000, 6_000);

    assertThat(CostCalculator.cost(sonnet, usage)).isEqualByComparingTo("0.017400");
  }

  @Test
  void addsFreshInputAndOutput() {
    // 100K fresh input at 2.00 = 0.20, 10K output at 10.00 = 0.10
    var usage = new TokenUsage(100_000, 10_000, 0, 0);

    assertThat(CostCalculator.cost(sonnet, usage)).isEqualByComparingTo("0.300000");
  }

  @Test
  void usesInputPriceWhenCachePricesAreUnknown() {
    var haiku = new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50"), null, null);
    var usage = new TokenUsage(0, 0, 1_000_000, 1_000_000);

    assertThat(CostCalculator.cost(haiku, usage)).isEqualByComparingTo("0.200000");
  }

  @Test
  void localModelCostsNothing() {
    var local = new ModelPrice(BigDecimal.ZERO, BigDecimal.ZERO, null, null);

    assertThat(CostCalculator.cost(local, new TokenUsage(5_000, 2_000, 0, 0)))
        .isEqualByComparingTo("0");
  }
}
