package com.auratrade.llmgateway;

import java.math.BigDecimal;

/** What callers get back from the gateway. */
public record LlmResult(
    String text, Tier tier, String model, TokenUsage usage, BigDecimal costUsd, String runId) {}
