package com.auratrade.llmgateway;

/**
 * Capability tiers. Agents ask for a tier, never a model. Declaration order is strongest first, so
 * a higher ordinal means a cheaper and weaker tier.
 */
public enum Tier {
  HEAVY,
  LIGHT,
  LOCAL;

  /** One step cheaper, but never below the floor. Returns this tier if already at or below it. */
  public Tier oneDown(Tier floor) {
    if (ordinal() >= floor.ordinal()) {
      return this;
    }
    return values()[ordinal() + 1];
  }
}
