package com.auratrade.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameNormalizerTest {

  @Test
  void normalisesCasePunctuationAmpersandAndSuffixes() {
    assertThat(NameNormalizer.key("The Acme & Sons Co. Ltd.")).isEqualTo("ACME AND SONS CO");
    assertThat(NameNormalizer.key("ACME COOKERS LIMITED")).isEqualTo("ACME COOKERS");
    assertThat(NameNormalizer.key("ORBIT DEF AND SPCE TECH L"))
        .isEqualTo("ORBIT DEF AND SPCE TECH");
  }

  @Test
  void keepsASingleWordEvenIfItLooksLikeASuffix() {
    assertThat(NameNormalizer.key("L")).isEqualTo("L");
  }

  @Test
  void emptyForPunctuationOnly() {
    assertThat(NameNormalizer.tokens("--- ...")).isEmpty();
  }
}
