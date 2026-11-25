package com.auratrade.llmgateway;

/** What a provider returned for one call. */
record InvocationResult(String text, TokenUsage usage) {}
