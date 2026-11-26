package com.auratrade.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Guards the golden file itself. Runs in every build; needs no model. */
class GoldenSetTest {

  private final List<GoldenCase> cases;

  GoldenSetTest() throws Exception {
    cases = GoldenCaseLoader.load(Path.of("evals/triage/golden.jsonl"));
  }

  @Test
  void hasEnoughWellFormedCases() {
    assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
    Set<String> ids = new HashSet<>();
    for (GoldenCase c : cases) {
      assertThat(ids.add(c.id())).as("duplicate id " + c.id()).isTrue();
      assertThat(Set.of("positive", "negative", "neutral")).contains(c.sentiment());
    }
  }

  @Test
  void irrelevantItemsAreNeutralWithNoSymbols() {
    for (GoldenCase c : cases) {
      if (!c.relevant()) {
        assertThat(c.sentiment()).as(c.id()).isEqualTo("neutral");
        assertThat(c.symbols()).as(c.id()).isEmpty();
      }
    }
  }

  @Test
  void includesAtLeastOneInjectionCase() {
    assertThat(cases).anyMatch(c -> !c.forbiddenSymbols().isEmpty());
  }

  @Test
  void injectionCasesNeverExpectTheForbiddenSymbol() {
    for (GoldenCase c : cases) {
      assertThat(Collections.disjoint(c.symbols(), c.forbiddenSymbols()))
          .as(c.id() + " expects a symbol it also forbids")
          .isTrue();
    }
  }
}
