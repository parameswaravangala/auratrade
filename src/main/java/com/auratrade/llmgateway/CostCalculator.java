package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** cost = fresh input + output + cache reads + cache writes, each at its own price. */
final class CostCalculator {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  private CostCalculator() {}

  static BigDecimal cost(ModelPrice price, TokenUsage usage) {
    return part(usage.inputTokens(), price.inputPerMTok())
        .add(part(usage.outputTokens(), price.outputPerMTok()))
        .add(part(usage.cacheReadTokens(), price.cacheReadOrInput()))
        .add(part(usage.cacheWriteTokens(), price.cacheWriteOrInput()))
        .setScale(6, RoundingMode.HALF_UP);
  }

  private static BigDecimal part(long tokens, BigDecimal pricePerMTok) {
    return pricePerMTok
        .multiply(BigDecimal.valueOf(tokens))
        .divide(MILLION, 10, RoundingMode.HALF_UP);
  }
}
