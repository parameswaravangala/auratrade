package com.auratrade.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads a JSONL golden set: one JSON object per line, blank lines and # comments ignored. */
final class GoldenCaseLoader {

  private GoldenCaseLoader() {}

  static List<GoldenCase> load(Path file) throws IOException {
    List<GoldenCase> cases = new ArrayList<>();
    int lineNo = 0;
    for (String line : Files.readAllLines(file)) {
      lineNo++;
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      try {
        cases.add(fromLine(trimmed));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(file + " line " + lineNo + ": " + e.getMessage(), e);
      }
    }
    return cases;
  }

  @SuppressWarnings("unchecked")
  static GoldenCase fromLine(String line) {
    Object parsed = MiniJson.parse(line);
    if (!(parsed instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("expected a JSON object");
    }
    Map<String, Object> m = (Map<String, Object>) parsed;
    return new GoldenCase(
        text(m, "id"),
        text(m, "text"),
        bool(m, "relevant"),
        text(m, "sentiment"),
        upperSet(m.get("symbols")),
        upperSet(m.get("forbiddenSymbols")),
        stringList(m.get("tags")));
  }

  private static String text(Map<String, Object> m, String key) {
    Object v = m.get(key);
    if (!(v instanceof String str) || str.isBlank()) {
      throw new IllegalArgumentException("missing or empty string field '" + key + "'");
    }
    return str;
  }

  private static boolean bool(Map<String, Object> m, String key) {
    if (!(m.get(key) instanceof Boolean b)) {
      throw new IllegalArgumentException("missing boolean field '" + key + "'");
    }
    return b;
  }

  private static List<String> stringList(Object v) {
    List<String> out = new ArrayList<>();
    if (v instanceof List<?> list) {
      for (Object o : list) {
        out.add(String.valueOf(o));
      }
    }
    return out;
  }

  private static Set<String> upperSet(Object v) {
    Set<String> out = new LinkedHashSet<>();
    for (String s : stringList(v)) {
      out.add(s.trim().toUpperCase());
    }
    return out;
  }
}
