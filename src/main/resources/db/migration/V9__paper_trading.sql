-- ADR 0024: paper trading with human approval (ADR 0005). Code proposes orders; a person approves
-- or rejects each one before the next open; approved orders fill at that open on paper.

CREATE TABLE paper_account (
  id                 BIGSERIAL PRIMARY KEY,
  name               TEXT NOT NULL UNIQUE,
  screen             TEXT NOT NULL,
  initial_capital    NUMERIC(18,2) NOT NULL CHECK (initial_capital > 0),
  cash               NUMERIC(18,2) NOT NULL,
  max_positions      INT NOT NULL CHECK (max_positions > 0),
  risk_per_trade     NUMERIC(8,6) NOT NULL CHECK (risk_per_trade > 0),
  max_position       NUMERIC(8,6) NOT NULL CHECK (max_position > 0),
  atr_multiple       NUMERIC(8,4) NOT NULL CHECK (atr_multiple > 0),
  max_hold_sessions  INT NOT NULL CHECK (max_hold_sessions > 0),
  regime_filter      BOOLEAN NOT NULL,
  slippage_bps       INT NOT NULL CHECK (slippage_bps >= 0),
  -- ADR 0005: the criteria for ever moving to live trading, written before the run starts.
  go_live_criteria   TEXT NOT NULL,
  started_on         DATE NOT NULL,
  active             BOOLEAN NOT NULL,
  -- Set by the application from its Clock; no database defaults.
  created_at         TIMESTAMPTZ NOT NULL
);

CREATE TABLE paper_order (
  id              BIGSERIAL PRIMARY KEY,
  account_id      BIGINT NOT NULL REFERENCES paper_account (id),
  instrument_id   BIGINT NOT NULL REFERENCES instrument (id),
  symbol          TEXT NOT NULL,
  side            TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
  quantity        BIGINT NOT NULL CHECK (quantity > 0),
  reason          TEXT NOT NULL,                -- ENTRY, STOP, TIME
  signal_date     DATE NOT NULL,                -- session whose close produced the proposal
  fill_date       DATE NOT NULL,                -- session whose open it fills at, if approved
  stop_price      NUMERIC(18,4),                -- for entries
  score           NUMERIC(18,6),
  detail          TEXT,
  status          TEXT NOT NULL
                  CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'EXPIRED', 'FILLED', 'UNFILLED')),
  decided_at      TIMESTAMPTZ,
  decided_by      TEXT,
  fill_price      NUMERIC(18,4),
  fill_costs      NUMERIC(18,2),
  status_note     TEXT,
  created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_paper_order_account_status ON paper_order (account_id, status, fill_date);

CREATE TABLE paper_position (
  account_id      BIGINT NOT NULL REFERENCES paper_account (id),
  instrument_id   BIGINT NOT NULL REFERENCES instrument (id),
  symbol          TEXT NOT NULL,
  quantity        BIGINT NOT NULL CHECK (quantity > 0),
  entry_price     NUMERIC(18,4) NOT NULL,
  entry_date      DATE NOT NULL,
  entry_costs     NUMERIC(18,2) NOT NULL,
  stop_price      NUMERIC(18,4) NOT NULL,
  sessions_held   INT NOT NULL,
  PRIMARY KEY (account_id, instrument_id)
);

-- Closed round trips: the trading journal.
CREATE TABLE paper_trade (
  id              BIGSERIAL PRIMARY KEY,
  account_id      BIGINT NOT NULL REFERENCES paper_account (id),
  instrument_id   BIGINT NOT NULL REFERENCES instrument (id),
  symbol          TEXT NOT NULL,
  quantity        BIGINT NOT NULL,
  entry_date      DATE NOT NULL,
  entry_price     NUMERIC(18,4) NOT NULL,
  exit_date       DATE NOT NULL,
  exit_price      NUMERIC(18,4) NOT NULL,
  exit_reason     TEXT NOT NULL,
  sessions_held   INT NOT NULL,
  costs           NUMERIC(18,2) NOT NULL,
  net_pnl         NUMERIC(18,2) NOT NULL
);

-- One row per account per processed session: the equity curve.
CREATE TABLE paper_equity (
  account_id      BIGINT NOT NULL REFERENCES paper_account (id),
  session_date    DATE NOT NULL,
  cash            NUMERIC(18,2) NOT NULL,
  equity          NUMERIC(18,2) NOT NULL,
  positions       INT NOT NULL,
  PRIMARY KEY (account_id, session_date)
);
