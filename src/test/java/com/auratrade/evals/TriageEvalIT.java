package com.auratrade.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.auratrade.PostgresIntegrationTest;
import com.auratrade.llmgateway.AgentType;
import com.auratrade.llmgateway.CallOptions;
import com.auratrade.llmgateway.LlmGateway;
import com.auratrade.llmgateway.LlmResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the triage golden set through the real gateway and model. Excluded from normal builds; run
 * with the eval profile (see evals/README.md).
 *
 * <p>Environment switches: EVAL_PROMPT (prompt file, default prompt-v2), EVAL_JSON_MODE=false (use
 * provider defaults instead of temperature 0, JSON mode and a fixed seed),
 * EVAL_UPDATE_BASELINE=true (save this run as the baseline), EVAL_STRICT=true (fail when a quality
 * metric drops more than the tolerance below the baseline).
 */
@Tag("eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TriageEvalIT extends PostgresIntegrationTest {

  private static final double TOLERANCE = 0.05;

  private static final Path GOLDEN = Path.of("evals/triage/golden.jsonl");
  private static final Path PROMPT =
      Path.of(System.getenv().getOrDefault("EVAL_PROMPT", "evals/triage/prompt-v2.txt"));
  private static final Path BASELINE = Path.of("evals/triage/baseline.json");

  @Autowired LlmGateway gateway;

  @Test
  void triageQualityAgainstGoldenSet() throws Exception {
    List<GoldenCase> cases = GoldenCaseLoader.load(GOLDEN);
    String system = Files.readString(PROMPT);
    String promptVersion = PROMPT.getFileName().toString().replaceFirst("\\.txt$", "");
    boolean jsonMode = !"false".equalsIgnoreCase(System.getenv("EVAL_JSON_MODE"));
    CallOptions options = jsonMode ? CallOptions.structuredJson() : CallOptions.DEFAULT;

    List<CaseOutcome> outcomes = new ArrayList<>();
    String model = "unknown";
    for (GoldenCase golden : cases) {
      long start = System.nanoTime();
      try {
        LlmResult result =
            gateway.call(
                AgentType.NEWS_TRIAGE, system, "<news>\n" + golden.text() + "\n</news>", options);
        model = result.model();
        outcomes.add(
            CaseOutcome.answered(
                golden,
                result.text(),
                (System.nanoTime() - start) / 1_000_000,
                result.usage().total(),
                result.costUsd()));
      } catch (RuntimeException e) {
        outcomes.add(
            CaseOutcome.failed(golden, e.toString(), (System.nanoTime() - start) / 1_000_000));
      }
    }

    Map<String, Double> metrics = Scorer.score(outcomes);
    Map<String, Double> baseline = EvalReporter.readBaseline(BASELINE);
    String now = Instant.now().toString();

    System.out.println();
    System.out.println(
        "Triage eval, model "
            + model
            + ", "
            + promptVersion
            + (jsonMode ? ", temperature 0 + JSON mode" : ", provider defaults"));
    System.out.print(EvalReporter.table(metrics, baseline));

    List<CaseOutcome> holdout =
        outcomes.stream().filter(o -> o.golden().tags().contains("holdout")).toList();
    if (!holdout.isEmpty()) {
      System.out.println();
      System.out.println(
          "Held-out cases only (written after the prompt was tuned): " + holdout.size());
      System.out.print(EvalReporter.table(Scorer.score(holdout), Map.of()));
    }

    Object report = EvalReporter.reportJson(model, promptVersion, now, metrics, outcomes);
    EvalReporter.write(Path.of("target/evals/triage-" + now.replace(':', '-') + ".json"), report);

    if (Boolean.parseBoolean(System.getenv("EVAL_UPDATE_BASELINE"))) {
      Map<String, Object> saved = new java.util.LinkedHashMap<>();
      saved.put("recordedAt", now);
      saved.put("model", model);
      saved.put("jsonMode", jsonMode);
      saved.put("promptVersion", promptVersion);
      saved.put("metrics", metrics);
      EvalReporter.write(BASELINE, saved);
      System.out.println("Baseline saved to " + BASELINE);
    }

    List<String> regressions = EvalReporter.regressions(metrics, baseline, TOLERANCE);
    regressions.forEach(r -> System.out.println("REGRESSION: " + r));

    assertThat(outcomes).hasSize(cases.size());
    if (Boolean.parseBoolean(System.getenv("EVAL_STRICT"))) {
      assertThat(regressions).as("quality regressions against baseline").isEmpty();
    }
  }
}
