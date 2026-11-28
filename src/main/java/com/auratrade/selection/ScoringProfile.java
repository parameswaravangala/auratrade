package com.auratrade.selection;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A named weighting of strategies into one combined score, 0 to 100 (plan P6, spec section 6 and
 * 14.4). Weights are per strategy id and are not tied to any stored version yet.
 *
 * <p>Only strategies whose gate has passed count (ADR 0042): a strategy outside the approved set
 * contributes nothing, whatever its weight. A strategy that is approved but did not pass on the
 * session also contributes nothing; its weight stays in the total, so a weak day is not hidden by
 * renormalising.
 *
 * <ul>
 *   <li>{@link Policy#STRICT}: the configured weights are used as they are. The combined score is
 *       the sum of weight x score, divided by 100.
 *   <li>{@link Policy#AUTO_NORMALIZED}: the weights of the approved strategies are rescaled to
 *       total 100, so a profile with one approved strategy is judged on that strategy alone.
 * </ul>
 */
public record ScoringProfile(
    String id, String name, Policy policy, Map<String, BigDecimal> weights) {

  public enum Policy {
    STRICT,
    AUTO_NORMALIZED
  }

  private static final MathContext MC = MathContext.DECIMAL64;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  /** The spec's starting weights, without the regime component (not built yet). */
  public static final ScoringProfile BALANCED_SWING =
      new ScoringProfile(
          "balanced-swing",
          "Balanced Swing",
          Policy.AUTO_NORMALIZED,
          Map.of(
              "momentum-breakout", BigDecimal.valueOf(25),
              "pullback-20ema", BigDecimal.valueOf(20),
              "relative-strength", BigDecimal.valueOf(20),
              "high-52w-momentum", BigDecimal.valueOf(15)));

  public ScoringProfile {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(policy, "policy");
    if (id.isBlank()) {
      throw new IllegalArgumentException("profile id is blank");
    }
    if (weights.isEmpty()) {
      throw new IllegalArgumentException("a profile needs at least one weight");
    }
    BigDecimal total = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
      if (e.getValue() == null || e.getValue().signum() <= 0) {
        throw new IllegalArgumentException("weight for " + e.getKey() + " must be positive");
      }
      total = total.add(e.getValue());
    }
    if (total.compareTo(HUNDRED) > 0) {
      throw new IllegalArgumentException("weights total " + total + ", above 100");
    }
    weights = Collections.unmodifiableSortedMap(new TreeMap<>(weights));
  }

  /**
   * The combined score for one share.
   *
   * @param bestPassedByStrategy the best PASSED score of each strategy on the share; absent when
   *     the strategy did not pass
   * @param approved the strategies whose gate has passed
   * @return 0 to 100, scaled to two decimals
   */
  public BigDecimal combined(Map<String, BigDecimal> bestPassedByStrategy, Set<String> approved) {
    BigDecimal weightTotal = BigDecimal.ZERO;
    BigDecimal sum = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
      String strategy = e.getKey();
      if (!approved.contains(strategy)) {
        continue;
      }
      if (policy == Policy.AUTO_NORMALIZED) {
        weightTotal = weightTotal.add(e.getValue());
      }
      BigDecimal score = bestPassedByStrategy.get(strategy);
      if (score != null) {
        sum = sum.add(e.getValue().multiply(score, MC));
      }
    }
    if (policy == Policy.STRICT) {
      return sum.divide(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
    }
    if (weightTotal.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return sum.divide(weightTotal, MC).setScale(2, RoundingMode.HALF_UP);
  }
}
