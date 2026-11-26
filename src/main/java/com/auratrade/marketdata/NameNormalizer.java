package com.auratrade.marketdata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a company name into comparable tokens: upper case, "&" to AND, punctuation to spaces, and
 * trailing legal suffixes removed. A trailing single "L" is dropped too, because the exchange data
 * truncates many names to end in "... L" (for LIMITED).
 */
final class NameNormalizer {

  private static final Set<String> TRAILING_SUFFIXES = Set.of("LTD", "LIMITED", "L", "PLC");

  private NameNormalizer() {}

  static List<String> tokens(String name) {
    String cleaned =
        name.toUpperCase(Locale.ROOT).replace("&", " AND ").replaceAll("[^A-Z0-9 ]", " ").trim();
    if (cleaned.isEmpty()) {
      return List.of();
    }
    List<String> tokens = new ArrayList<>(Arrays.asList(cleaned.split("\\s+")));
    if (!tokens.isEmpty() && tokens.get(0).equals("THE")) {
      tokens.remove(0);
    }
    while (tokens.size() > 1 && TRAILING_SUFFIXES.contains(tokens.get(tokens.size() - 1))) {
      tokens.remove(tokens.size() - 1);
    }
    return List.copyOf(tokens);
  }

  /** Normalised key for exact lookups (names and aliases). */
  static String key(String name) {
    return String.join(" ", tokens(name));
  }
}
