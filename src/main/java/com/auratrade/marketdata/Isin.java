package com.auratrade.marketdata;

import java.util.regex.Pattern;

/**
 * ISIN format and check digit (ISO 6166): two letters, nine letters or digits, one check digit.
 * Letters count as two-digit numbers (A=10 ... Z=35) and the Luhn algorithm runs over the result.
 */
final class Isin {

  private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

  private Isin() {}

  static boolean isValid(String isin) {
    return isin != null
        && FORMAT.matcher(isin).matches()
        && checkDigit(isin.substring(0, 11)) == isin.charAt(11) - '0';
  }

  /** Check digit for the first eleven characters of an ISIN. */
  static int checkDigit(String first11) {
    StringBuilder digits = new StringBuilder();
    for (char c : first11.toCharArray()) {
      digits.append(Character.isDigit(c) ? String.valueOf(c - '0') : String.valueOf(c - 'A' + 10));
    }
    int sum = 0;
    for (int i = digits.length() - 1, pos = 0; i >= 0; i--, pos++) {
      int d = digits.charAt(i) - '0';
      if (pos % 2 == 0) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
    }
    return (10 - sum % 10) % 10;
  }
}
