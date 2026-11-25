package com.auratrade.llmgateway;

import java.math.BigDecimal;

/**
 * Prices in USD per million tokens. Cache prices may be null; the input price is then used, which
 * over-estimates cost and is the safe direction for a budget.
 */
public record ModelPrice(
    BigDecimal inputPerMTok,
    BigDecimal outputPerMTok,
    BigDecimal cacheReadPerMTok,
    BigDecimal cacheWritePerMTok) {

  public ModelPrice {
    if (inputPerMTok == null || outputPerMTok == null) {
      throw new IllegalArgumentException("input and output prices are required");
    }
  }

  BigDecimal cacheReadOrInput() {
    return cacheReadPerMTok != null ? cacheReadPerMTok : inputPerMTok;
  }

  BigDecimal cacheWriteOrInput() {
    return cacheWritePerMTok != null ? cacheWritePerMTok : inputPerMTok;
  }
}
