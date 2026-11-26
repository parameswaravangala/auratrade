package com.auratrade.evals;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns raw model text into a TriageResult. Tolerates code fences and chatter around the JSON
 * object; anything that does not validate returns empty and counts against parse_rate.
 */
final class TriageParser {

  private static final Set<String> SENTIMENTS = Set.of("positive", "negative", "neutral");

  private TriageParser() {}

  static Optional<TriageResult> parse(String raw) {
    String json = firstJsonObject(raw);
    if (json == null) {
      return Optional.empty();
    }
    try {
      Object parsed = MiniJson.parse(json);
      if (!(parsed instanceof Map<?, ?> map)) {
        return Optional.empty();
      }
      if (!(map.get("relevant") instanceof Boolean relevant)) {
        return Optional.empty();
      }
      if (!(map.get("sentiment") instanceof String sentimentRaw)) {
        return Optional.empty();
      }
      String sentiment = sentimentRaw.trim().toLowerCase();
      if (!SENTIMENTS.contains(sentiment)) {
        return Optional.empty();
      }
      Set<String> symbols = new LinkedHashSet<>();
      Object rawSymbols = map.get("symbols");
      if (rawSymbols != null && !(rawSymbols instanceof java.util.List<?>)) {
        return Optional.empty();
      }
      if (rawSymbols instanceof java.util.List<?> list) {
        for (Object o : list) {
          if (!(o instanceof String str)) {
            return Optional.empty();
          }
          symbols.add(str.trim().toUpperCase());
        }
      }
      return Optional.of(new TriageResult(relevant, sentiment, symbols));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Returns the first balanced {...} in the text, ignoring braces inside strings. */
  static String firstJsonObject(String text) {
    if (text == null) {
      return null;
    }
    int start = text.indexOf('{');
    if (start < 0) {
      return null;
    }
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int k = start; k < text.length(); k++) {
      char c = text.charAt(k);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return text.substring(start, k + 1);
        }
      }
    }
    return null;
  }
}
