package com.auratrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterSchemaTest {

  private static final ParameterSchema SCHEMA =
      ParameterSchema.builder()
          .integer("breakoutLookback", "Sessions of prior resistance", 10, 60, 20)
          .decimal("minVolumeRatio", "Breakout volume over 20-day average", "1.0", "5.0", "1.5")
          .decimal("rsiMin", "Lowest RSI(14)", "0", "100", "55")
          .decimal("rsiMax", "Highest RSI(14)", "0", "100", "70")
          .lessOrEqual("rsiMin", "rsiMax")
          .build();

  @Test
  void defaultsFillEveryParameter() {
    Parameters p = SCHEMA.defaults();
    assertThat(p.integer("breakoutLookback")).isEqualTo(20);
    assertThat(p.decimal("minVolumeRatio")).isEqualByComparingTo("1.5");
    assertThat(p.asMap())
        .containsOnlyKeys("breakoutLookback", "minVolumeRatio", "rsiMax", "rsiMin");
  }

  @Test
  void givenValuesOverrideDefaults() {
    Parameters p = SCHEMA.validate(Map.of("minVolumeRatio", new BigDecimal("2.0")));
    assertThat(p.decimal("minVolumeRatio")).isEqualByComparingTo("2");
    assertThat(p.decimal("rsiMin")).isEqualByComparingTo("55");
  }

  @Test
  void everyProblemIsReportedAtOnce() {
    assertThatThrownBy(() ->
            SCHEMA.validate(
                Map.of(
                    "breakoutLookback", new BigDecimal("20.5"),
                    "minVolumeRatio", new BigDecimal("9"),
                    "rsiMin", new BigDecimal("75"),
                    "colour", BigDecimal.ONE)))
        .isInstanceOfSatisfying(
            InvalidParametersException.class,
            e ->
                assertThat(e.errors())
                    .containsExactlyInAnyOrder(
                        "unknown parameter colour",
                        "breakoutLookback = 20.5 must be a whole number",
                        "minVolumeRatio = 9 outside 1.0 to 5.0",
                        "rsiMin must not exceed rsiMax"));
  }

  @Test
  void wholeNumbersWithTrailingZerosAreIntegral() {
    Parameters p = SCHEMA.validate(Map.of("breakoutLookback", new BigDecimal("30.00")));
    assertThat(p.integer("breakoutLookback")).isEqualTo(30);
  }

  @Test
  void undeclaredNamesCannotBeRead() {
    assertThatThrownBy(() -> SCHEMA.defaults().decimal("nope"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parametersCompareNumerically() {
    assertThat(SCHEMA.validate(Map.of("minVolumeRatio", new BigDecimal("1.50"))))
        .isEqualTo(SCHEMA.defaults())
        .hasSameHashCodeAs(SCHEMA.defaults());
  }

  @Test
  void schemaRejectsBadDeclarations() {
    assertThatThrownBy(() -> ParameterSchema.builder().decimal("x", "d", "5", "1", "3").build())
        .hasMessageContaining("min <= default <= max");
    assertThatThrownBy(() -> ParameterSchema.builder().integer("Bad_Name", "d", 1, 2, 1).build())
        .hasMessageContaining("lowerCamelCase");
    assertThatThrownBy(() ->
            ParameterSchema.builder().integer("a", "d", 1, 2, 1).integer("a", "d", 1, 2, 1).build())
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() ->
            ParameterSchema.builder().integer("a", "d", 1, 2, 1).lessOrEqual("a", "b").build())
        .hasMessageContaining("undeclared");
    // defaults that break a constraint fail when the schema is built
    assertThatThrownBy(() ->
            ParameterSchema.builder()
                .integer("lo", "d", 0, 10, 8)
                .integer("hi", "d", 0, 10, 2)
                .lessOrEqual("lo", "hi")
                .build())
        .isInstanceOf(InvalidParametersException.class);
  }
}
