package com.auratrade.paperbroker;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CorporateActionGuardTest {

  private static java.util.Optional<CorporateActionGuard.Evidence> gap(String prev, String open) {
    return CorporateActionGuard.check(new BigDecimal(prev), new BigDecimal(open));
  }

  @Test
  void anOrdinaryDropIsNotFlagged() {
    assertThat(gap("100", "93")).isEmpty(); // -7%, no ratio within 1 point
    assertThat(gap("100", "105")).isEmpty();
  }

  @Test
  void aTenPercentDropIsFlaggedAsAPossibleOneForTenBonus() {
    // -10% is within 1 point of a 1:10 bonus (-9.09%): a person checks (fails closed).
    assertThat(gap("100", "90").orElseThrow().nearestRatio()).isEqualTo("bonus 1:10");
  }

  @Test
  void aGapOfFifteenPercentOrWorseIsFlagged() {
    assertThat(gap("100", "85.01")).isEmpty(); // -14.99%
    var e = gap("100", "85").orElseThrow(); // -15%
    assertThat(e.gap()).isEqualByComparingTo("-0.15");
    assertThat(e.nearestRatio()).isNull();
    assertThat(gap("100", "70")).isPresent(); // -30%, no ratio within 1 point
  }

  @Test
  void aSmallBonusIsFlaggedByItsRatioThoughAboveTheThreshold() {
    // 1:10 bonus: 10/11 of the price, -9.09%.
    var e = gap("110", "100").orElseThrow();
    assertThat(e.nearestRatio()).isEqualTo("bonus 1:10");
  }

  @Test
  void aHalvingNamesBothTheBonusAndTheSplit() {
    var e = gap("200", "100.50").orElseThrow(); // -49.75%, within 1 point of -50%
    assertThat(e.nearestRatio()).isEqualTo("bonus 1:1 or split 1:2");
    assertThat(e.describe()).contains("-49.75%").contains("bonus 1:1 or split 1:2");
  }

  @Test
  void missingOrNonPositivePricesAreNotFlagged() {
    assertThat(CorporateActionGuard.check(null, BigDecimal.ONE)).isEmpty();
    assertThat(gap("0", "10")).isEmpty();
  }
}
