CREATE EXTENSION IF NOT EXISTS vector;

-- One row per agent run. Spring AI usage is cumulative across the tool-calling loop.
CREATE TABLE llm_usage (
  id            BIGSERIAL PRIMARY KEY,
  ts            TIMESTAMPTZ NOT NULL DEFAULT now(),
  agent         TEXT NOT NULL,
  tier          TEXT NOT NULL,
  model         TEXT NOT NULL,
  input_tokens  INT NOT NULL,
  output_tokens INT NOT NULL,
  cache_read    INT,
  cache_write   INT,
  cost_usd      NUMERIC(10,6) NOT NULL,
  run_id        TEXT
);

CREATE INDEX idx_llm_usage_ts ON llm_usage (ts);
CREATE INDEX idx_llm_usage_agent_ts ON llm_usage (agent, ts);
