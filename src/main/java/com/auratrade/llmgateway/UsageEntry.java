package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.time.Instant;

/** One agent run as it is stored in llm_usage. */
record UsageEntry(
    Instant ts,
    AgentType agent,
    Tier tier,
    String model,
    TokenUsage usage,
    BigDecimal costUsd,
    String runId) {}
