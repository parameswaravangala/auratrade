package com.auratrade.paperbroker;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Spots an opening gap that may be a split or bonus rather than a price move (ADR 0024 amendment,
 * research note 2026-11-27 R1). Prices are not adjusted for corporate actions yet (B-020), so on an
 * ex-date the open drops by the ratio and a stop would book a loss that never happened.
 *
 * <p>Flags a gap of {@link #GAP_THRESHOLD} or worse, or one within {@link #RATIO_TOLERANCE} of a
 * common split or bonus ratio. A flag is not proof: a person checks the exchange's corporate
 * actions and reconciles. Pure arithmetic; the ratios are the price factors of the split and bonus
 * terms listed, not exchange data.
 */
final class CorporateActionGuard {

  /** -15%: the threshold in the research note (R1, R2). */
  static final BigDecimal GAP_THRESHOLD = new BigDecimal("-0.15");

  /** One percentage point either side of a ratio's price change. */
  static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.01");

  /** A split or bonus and the open-to-previous-close change it causes. */
  record Ratio(String terms, BigDecimal priceChange) {}

  /**
   * Price change on the ex-date: a bonus of a new for b held leaves b/(a+b) of the price; a split
   * from one share into n leaves 1/n.
   */
  static final List<Ratio> RATIOS =
      List.of(
          bonus(1, 10),
          bonus(1, 5),
          bonus(1, 4),
          bonus(1, 3),
          bonus(1, 2),
          bonus(1, 1),
          bonus(2, 1),
          bonus(3, 1),
          bonus(4, 1),
          split(2),
          split(4),
          split(5),
          split(10));

  private static Ratio bonus(int newShares, int held) {
    return new Ratio(
        "bonus " + newShares + ":" + held,
        change(BigDecimal.valueOf(held), BigDecimal.valueOf(newShares + held)));
  }

  private static Ratio split(int into) {
    return new Ratio("split 1:" + into, change(BigDecimal.ONE, BigDecimal.valueOf(into)));
  }

  private static BigDecimal change(BigDecimal keep, BigDecimal of) {
    return keep.divide(of, MathContext.DECIMAL64).subtract(BigDecimal.ONE);
  }

  /** Why a gap was flagged. */
  record Evidence(BigDecimal previousClose, BigDecimal open, BigDecimal gap, String nearestRatio) {

    String describe() {
      return "open "
          + open.toPlainString()
          + " against previous close "
          + previousClose.toPlainString()
          + ", a gap of "
          + gap.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
          + "%"
          + (nearestRatio == null ? "" : ", close to a " + nearestRatio);
    }
  }

  private CorporateActionGuard() {}

  /**
   * @return evidence if the open's gap from the previous close may be a corporate action
   */
  static Optional<Evidence> check(BigDecimal previousClose, BigDecimal open) {
    if (previousClose == null || open == null || previousClose.signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal gap = open.divide(previousClose, MathContext.DECIMAL64).subtract(BigDecimal.ONE);
    // A 1:1 bonus and a 1:2 split both halve the price: name every ratio that fits.
    List<String> fits =
        RATIOS.stream()
            .filter(r -> gap.subtract(r.priceChange()).abs().compareTo(RATIO_TOLERANCE) <= 0)
            .map(Ratio::terms)
            .toList();
    String nearest = fits.isEmpty() ? null : String.join(" or ", fits);
    if (nearest == null && gap.compareTo(GAP_THRESHOLD) > 0) {
      return Optional.empty();
    }
    return Optional.of(
        new Evidence(previousClose, open, gap.setScale(6, RoundingMode.HALF_UP), nearest));
  }
}
