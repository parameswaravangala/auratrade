package com.auratrade.llmgateway;

import java.util.Objects;

/** The model behind a tier and what it costs. */
public record TierConfig(String model, ModelPrice price) {

  public TierConfig {
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(price, "price");
  }
}
