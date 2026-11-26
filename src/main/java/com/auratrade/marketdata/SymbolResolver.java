package com.auratrade.marketdata;

import com.auratrade.marketdata.Resolution.Ambiguous;
import com.auratrade.marketdata.Resolution.MatchKind;
import com.auratrade.marketdata.Resolution.Resolved;
import com.auratrade.marketdata.Resolution.Unresolved;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a company name, as written in news or extracted by an LLM, to an NSE share. Pure and
 * deterministic: no I/O, no model. The input is untrusted, and the result can only be a share from
 * the list it was built with, so an invented ticker cannot come out (backlog B-001).
 *
 * <p>Order: alias, exact symbol, exact normalised name, then fuzzy. The fuzzy step requires every
 * word of the listed name to match a word of the input, in order: exactly, as a prefix ("DEF" for
 * DEFENCE) or as an abbreviation with letters dropped ("SPCE" for SPACE). It accepts only a clear
 * winner; otherwise the result is ambiguous or unresolved. It never guesses.
 */
public final class SymbolResolver {

  static final int MAX_INPUT_LENGTH = 120;
  static final double MIN_SCORE = 0.75;
  static final double MIN_MARGIN = 0.10;
  private static final int MAX_CANDIDATES = 5;

  private record Entry(ShareRef share, List<String> tokens) {}

  private final List<Entry> entries;
  private final Map<String, ShareRef> bySymbol = new HashMap<>();
  private final Map<String, List<ShareRef>> byName = new HashMap<>();
  private final Map<String, ShareRef> byAlias = new HashMap<>();

  /**
   * @param shares the shares that may be returned
   * @param aliases normalised alias key to instrument id; aliases for ids not in {@code shares}
   *     (for example inactive instruments) are ignored
   */
  public SymbolResolver(List<ShareRef> shares, Map<String, Long> aliases) {
    List<Entry> list = new ArrayList<>();
    Map<Long, ShareRef> byId = new HashMap<>();
    for (ShareRef s : shares) {
      List<String> tokens = s.name() == null ? List.of() : NameNormalizer.tokens(s.name());
      list.add(new Entry(s, tokens));
      byId.put(s.instrumentId(), s);
      bySymbol.put(s.tradingSymbol(), s);
      if (!tokens.isEmpty()) {
        byName.computeIfAbsent(String.join(" ", tokens), k -> new ArrayList<>()).add(s);
      }
    }
    aliases.forEach((key, id) -> {
      ShareRef s = byId.get(id);
      if (s != null) {
        byAlias.put(key, s);
      }
    });
    this.entries = List.copyOf(list);
  }

  public int size() {
    return entries.size();
  }

  public Resolution resolve(String input) {
    if (input == null || input.isBlank()) {
      return new Unresolved("empty name");
    }
    if (input.length() > MAX_INPUT_LENGTH) {
      return new Unresolved("name longer than " + MAX_INPUT_LENGTH + " characters");
    }
    List<String> tokens = NameNormalizer.tokens(input);
    if (tokens.isEmpty()) {
      return new Unresolved("no letters or digits in name");
    }
    String key = String.join(" ", tokens);

    ShareRef alias = byAlias.get(key);
    if (alias != null) {
      return new Resolved(alias, MatchKind.ALIAS, 1.0);
    }
    ShareRef symbol = bySymbol.get(input.trim().toUpperCase(Locale.ROOT));
    if (symbol != null) {
      return new Resolved(symbol, MatchKind.SYMBOL, 1.0);
    }
    List<ShareRef> named = byName.getOrDefault(key, List.of());
    if (named.size() == 1) {
      return new Resolved(named.get(0), MatchKind.NAME, 1.0);
    }
    if (named.size() > 1) {
      return new Ambiguous(named);
    }
    return fuzzy(tokens);
  }

  private record Scored(ShareRef share, double score) {}

  private Resolution fuzzy(List<String> input) {
    List<Scored> hits = new ArrayList<>();
    for (Entry e : entries) {
      double score = score(e.tokens(), input);
      if (score >= MIN_SCORE) {
        hits.add(new Scored(e.share(), score));
      }
    }
    if (hits.isEmpty()) {
      return new Unresolved("no listed share matches");
    }
    hits.sort(Comparator.comparingDouble(Scored::score).reversed());
    Scored best = hits.get(0);
    if (hits.size() == 1 || best.score() - hits.get(1).score() >= MIN_MARGIN) {
      return new Resolved(best.share(), MatchKind.FUZZY, round(best.score()));
    }
    return new Ambiguous(
        hits.stream()
            .filter(h -> best.score() - h.score() < MIN_MARGIN)
            .limit(MAX_CANDIDATES)
            .map(Scored::share)
            .toList());
  }

  /**
   * 0 unless every name token matches an input token in order. Each match scores 1.0 (exact), 0.9
   * (prefix) or 0.8 (abbreviation); the sum is divided by the longer token count, so extra words on
   * either side lower the score.
   */
  static double score(List<String> name, List<String> input) {
    if (name.isEmpty()) {
      return 0;
    }
    double sum = 0;
    int next = 0;
    for (String n : name) {
      double best = 0;
      int at = -1;
      for (int i = next; i < input.size(); i++) {
        double s = tokenMatch(n, input.get(i));
        if (s > 0) {
          best = s;
          at = i;
          break;
        }
      }
      if (at < 0) {
        return 0;
      }
      sum += best;
      next = at + 1;
    }
    return sum / Math.max(name.size(), input.size());
  }

  static double tokenMatch(String nameToken, String inputToken) {
    if (nameToken.equals(inputToken)) {
      return 1.0;
    }
    if (nameToken.length() >= 2 && inputToken.startsWith(nameToken)) {
      return 0.9;
    }
    if (nameToken.length() >= 3
        && nameToken.charAt(0) == inputToken.charAt(0)
        && isSubsequence(nameToken, inputToken)) {
      return 0.8;
    }
    return 0;
  }

  private static boolean isSubsequence(String small, String big) {
    int j = 0;
    for (int i = 0; i < big.length() && j < small.length(); i++) {
      if (big.charAt(i) == small.charAt(j)) {
        j++;
      }
    }
    return j == small.length();
  }

  private static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }
}
