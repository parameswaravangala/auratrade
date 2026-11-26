package com.auratrade.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Baseline comparison and report output. */
final class EvalReporter {

  private EvalReporter() {}

  /** Quality metrics that dropped more than the tolerance below the baseline. */
  static List<String> regressions(
      Map<String, Double> current, Map<String, Double> baseline, double tolerance) {
    List<String> out = new ArrayList<>();
    for (String metric : Scorer.QUALITY) {
      Double base = baseline.get(metric);
      Double now = current.get(metric);
      if (base != null && now != null && now < base - tolerance) {
        out.add(String.format("%s fell from %.3f to %.3f", metric, base, now));
      }
    }
    return out;
  }

  static String table(Map<String, Double> current, Map<String, Double> baseline) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-20s %10s %10s %10s%n", "metric", "current", "baseline", "delta"));
    for (Map.Entry<String, Double> e : current.entrySet()) {
      Double base = baseline.get(e.getKey());
      sb.append(
          String.format(
              "%-20s %10.3f %10s %10s%n",
              e.getKey(),
              e.getValue(),
              base == null ? "-" : String.format("%.3f", base),
              base == null ? "-" : String.format("%+.3f", e.getValue() - base)));
    }
    return sb.toString();
  }

  static Map<String, Object> reportJson(
      String model,
      String promptVersion,
      String recordedAt,
      Map<String, Double> metrics,
      List<CaseOutcome> outcomes) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("recordedAt", recordedAt);
    report.put("model", model);
    report.put("promptVersion", promptVersion);
    report.put("metrics", metrics);
    List<Object> cases = new ArrayList<>();
    for (CaseOutcome o : outcomes) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("id", o.golden().id());
      c.put("parsed", o.parsed().isPresent());
      c.put("relevantCorrect", o.relevantCorrect());
      c.put("sentimentCorrect", o.sentimentCorrect());
      c.put("expectedSymbols", List.copyOf(o.golden().symbols()));
      c.put("actualSymbols", o.parsed().map(p -> List.copyOf(p.symbols())).orElse(List.of()));
      c.put("latencyMs", o.latencyMs());
      c.put("error", o.error());
      c.put("rawText", o.rawText());
      cases.add(c);
    }
    report.put("cases", cases);
    return report;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Double> readBaseline(Path file) throws IOException {
    Map<String, Double> out = new LinkedHashMap<>();
    if (!Files.exists(file)) {
      return out;
    }
    Object parsed = MiniJson.parse(Files.readString(file));
    if (parsed instanceof Map<?, ?> root && root.get("metrics") instanceof Map<?, ?> metrics) {
      for (Map.Entry<?, ?> e : ((Map<Object, Object>) metrics).entrySet()) {
        if (e.getValue() instanceof Number n) {
          out.put(String.valueOf(e.getKey()), n.doubleValue());
        }
      }
    }
    return out;
  }

  static void write(Path file, Object json) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, MiniJson.toJson(json) + System.lineSeparator());
  }
}
