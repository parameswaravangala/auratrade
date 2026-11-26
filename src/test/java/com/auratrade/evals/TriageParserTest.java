package com.auratrade.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TriageParserTest {

  @Test
  void parsesAPlainAnswer() {
    var result =
        TriageParser.parse(
            "{\"relevant\": true, \"sentiment\": \"positive\", \"symbols\": [\"tcs\"]}");

    assertThat(result).contains(new TriageResult(true, "positive", Set.of("TCS")));
  }

  @Test
  void toleratesCodeFencesAndChatter() {
    var result =
        TriageParser.parse(
            "Sure! Here you go:\n```json\n{\"relevant\": false, \"sentiment\": \"Neutral\", \"symbols\": []}\n```\nHope that helps.");

    assertThat(result).contains(new TriageResult(false, "neutral", Set.of()));
  }

  @Test
  void bracesInsideStringsDoNotConfuseExtraction() {
    String text =
        "{\"relevant\": true, \"sentiment\": \"neutral\", \"symbols\": [\"A}B\"]} trailing";

    assertThat(TriageParser.firstJsonObject(text)).endsWith("]}");
  }

  @Test
  void rejectsInvalidAnswers() {
    assertThat(TriageParser.parse("no json here")).isEmpty();
    assertThat(
            TriageParser.parse(
                "{\"relevant\": \"yes\", \"sentiment\": \"positive\", \"symbols\": []}"))
        .isEmpty();
    assertThat(
            TriageParser.parse("{\"relevant\": true, \"sentiment\": \"bullish\", \"symbols\": []}"))
        .isEmpty();
    assertThat(
            TriageParser.parse(
                "{\"relevant\": true, \"sentiment\": \"positive\", \"symbols\": \"TCS\"}"))
        .isEmpty();
    assertThat(TriageParser.parse("{\"relevant\": true, \"sentiment\": \"positive\"")).isEmpty();
    assertThat(TriageParser.parse(null)).isEmpty();
  }
}
