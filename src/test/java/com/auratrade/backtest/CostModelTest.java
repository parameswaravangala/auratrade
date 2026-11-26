package com.auratrade.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostModelTest {

  private final CostModel zerodha = CostModel.zerodhaDelivery();

  @Test
  void buyOfOneLakhWorkedByHand() {
    // STT 100 + txn 3.07 + SEBI 0.10 + GST 18% of 3.17 (0.5706) + stamp 15 = 118.7406 -> 118.74
    assertThat(zerodha.buyCost(new BigDecimal("100000"))).isEqualByComparingTo("118.74");
  }

  @Test
  void sellOfOneLakhWorkedByHand() {
    // STT 100 + txn 3.07 + SEBI 0.10 + GST 0.5706 + DP 15.34 = 119.0806 -> 119.08 (no stamp duty)
    assertThat(zerodha.sellCost(new BigDecimal("100000"))).isEqualByComparingTo("119.08");
  }

  @Test
  void freeModelCostsNothing() {
    assertThat(CostModel.free().buyCost(new BigDecimal("100000"))).isEqualByComparingTo("0");
    assertThat(CostModel.free().sellCost(new BigDecimal("100000"))).isEqualByComparingTo("0");
  }
}
