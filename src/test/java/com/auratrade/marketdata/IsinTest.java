package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The check digit is worked by hand below. The stronger proof is that every ISIN in the real
 * provider file validates, which the instrument load checks (it fails closed on any bad ISIN).
 */
class IsinTest {

  @Test
  void checkDigitWorkedByHand() {
    // INE000A0100: I=18 N=23 E=14 A=10 gives digits 1 8 2 3 1 4 0 0 0 1 0 0 1 0 0.
    // Doubling every second digit from the right, starting with the last:
    // 0 0 2 0 0 1 0 0 0 4 2 3 4 8 2 -> sum 26 -> check digit (10 - 6) % 10 = 4.
    assertThat(Isin.checkDigit("INE000A0100")).isEqualTo(4);
    assertThat(Isin.isValid("INE000A01004")).isTrue();
  }

  @Test
  void anyOtherCheckDigitIsRejected() {
    for (int d = 0; d <= 9; d++) {
      assertThat(Isin.isValid("INE000A0100" + d)).isEqualTo(d == 4);
    }
  }

  @Test
  void rejectsBadFormats() {
    assertThat(Isin.isValid(null)).isFalse();
    assertThat(Isin.isValid("")).isFalse();
    assertThat(Isin.isValid("ine000a01004")).isFalse(); // lower case
    assertThat(Isin.isValid("INE000A0100")).isFalse(); // 11 characters
    assertThat(Isin.isValid("INE000A010040")).isFalse(); // 13 characters
    assertThat(Isin.isValid("1NE000A01004")).isFalse(); // country code must be letters
  }
}
