package com.auratrade.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiniJsonTest {

  @Test
  void parsesObjectsArraysAndScalars() {
    Object v = MiniJson.parse("{\"a\": 1, \"b\": [true, null, \"x\"], \"c\": 2.5, \"d\": {}}");

    assertThat(v).isInstanceOf(Map.class);
    Map<?, ?> m = (Map<?, ?>) v;
    assertThat(m.get("a")).isEqualTo(1L);
    assertThat(m.get("b")).isEqualTo(java.util.Arrays.asList(true, null, "x"));
    assertThat(m.get("c")).isEqualTo(2.5);
    assertThat((Map<?, ?>) m.get("d")).isEmpty();
  }

  @Test
  void handlesEscapesAndUnicode() {
    assertThat(MiniJson.parse("\"line\\nbreak \\\"q\\\" \\u20b9\""))
        .isEqualTo("line\nbreak \"q\" \u20b9");
  }

  @Test
  void rejectsMalformedInput() {
    assertThatThrownBy(() -> MiniJson.parse("{\"a\": }"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MiniJson.parse("{\"a\": 1} extra"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MiniJson.parse("[1, 2")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void writesWhatItReads() {
    Map<String, Object> original =
        new java.util.LinkedHashMap<>(
            Map.of("name", "a \"quoted\" value", "list", List.of(1L, 2L), "ok", true));

    Object roundTrip = MiniJson.parse(MiniJson.toJson(original));

    assertThat(roundTrip).isEqualTo(original);
  }
}
