package com.auratrade.llmgateway;

/**
 * The only seam to a model provider. One implementation per provider (Ollama now, Claude later).
 * Throws a RuntimeException on any provider failure so the gateway can fall back.
 */
@FunctionalInterface
interface ModelInvoker {

  InvocationResult invoke(String model, String system, String user);

  /** Providers that support generation options override this; others ignore the options. */
  default InvocationResult invoke(String model, String system, String user, CallOptions options) {
    return invoke(model, system, user);
  }
}
