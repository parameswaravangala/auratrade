package com.auratrade.llmgateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistence for llm_usage: one row per agent run. */
@Repository
class UsageRepository implements UsageRecorder {

  private final JdbcClient jdbc;

  UsageRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(UsageEntry e) {
    save(e.ts(), e.agent(), e.tier(), e.model(), e.usage(), e.costUsd(), e.runId());
  }

  void save(
      Instant ts,
      AgentType agent,
      Tier tier,
      String model,
      TokenUsage usage,
      BigDecimal costUsd,
      String runId) {
    jdbc.sql(
            """
            insert into llm_usage
              (ts, agent, tier, model, input_tokens, output_tokens, cache_read, cache_write, cost_usd, run_id)
            values
              (:ts, :agent, :tier, :model, :input, :output, :cacheRead, :cacheWrite, :cost, :runId)
            """)
        .param("ts", OffsetDateTime.ofInstant(ts, ZoneOffset.UTC))
        .param("agent", agent.name())
        .param("tier", tier.name())
        .param("model", model)
        .param("input", Math.toIntExact(usage.inputTokens()))
        .param("output", Math.toIntExact(usage.outputTokens()))
        .param("cacheRead", Math.toIntExact(usage.cacheReadTokens()))
        .param("cacheWrite", Math.toIntExact(usage.cacheWriteTokens()))
        .param("cost", costUsd)
        .param("runId", runId)
        .update();
  }

  BigDecimal spendSince(OffsetDateTime from) {
    return jdbc.sql("select coalesce(sum(cost_usd), 0) from llm_usage where ts >= :from")
        .param("from", from)
        .query(BigDecimal.class)
        .single();
  }

  long tokensSince(AgentType agent, OffsetDateTime from) {
    return jdbc.sql(
            """
            select coalesce(sum(input_tokens + output_tokens
                                + coalesce(cache_read, 0) + coalesce(cache_write, 0)), 0)
            from llm_usage
            where agent = :agent and ts >= :from
            """)
        .param("agent", agent.name())
        .param("from", from)
        .query(Long.class)
        .single();
  }
}
