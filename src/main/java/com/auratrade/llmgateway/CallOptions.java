package com.auratrade.llmgateway;

/**
 * Per-call generation settings chosen by the caller, who knows what kind of output it needs.
 * Providers that do not support a setting ignore it.
 *
 * @param temperature sampling temperature, or null for the provider default
 * @param jsonOutput ask the provider to return valid JSON only
 * @param seed fixed random seed for repeatable output, or null
 */
public record CallOptions(Double temperature, boolean jsonOutput, Integer seed) {

  public static final CallOptions DEFAULT = new CallOptions(null, false, null);

  /** For extraction and classification: deterministic as far as the provider allows, JSON only. */
  public static CallOptions structuredJson() {
    return new CallOptions(0.0, true, 42);
  }
}
