package com.auratrade.llmgateway;

/**
 * Tokens for one agent run, split by how they are billed.
 *
 * @param inputTokens fresh (non-cached) input tokens. Adapters must subtract cache reads and writes
 *     if the provider includes them in its prompt-token count, or cost will be double-counted.
 * @param outputTokens generated tokens
 * @param cacheReadTokens input tokens served from the prompt cache
 * @param cacheWriteTokens input tokens written to the prompt cache
 */
public record TokenUsage(
    long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

  public TokenUsage {
    if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0) {
      throw new IllegalArgumentException("token counts must not be negative");
    }
  }

  public long total() {
    return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
  }
}
