-- ADR 0024 amendment (R0-02): an opening gap that may be a split or bonus blocks automatic exits
-- for the position until a person reconciles it. Prices are not adjusted for corporate actions yet
-- (B-020), so booking a stop on such a gap would record a loss that never happened.

CREATE TABLE paper_review (
  id              BIGSERIAL PRIMARY KEY,
  account_id      BIGINT NOT NULL REFERENCES paper_account (id),
  instrument_id   BIGINT NOT NULL REFERENCES instrument (id),
  symbol          TEXT NOT NULL,
  kind            TEXT NOT NULL CHECK (kind IN ('POSSIBLE_CORPORATE_ACTION')),
  session_date    DATE NOT NULL,                -- the session whose open gapped
  previous_close  NUMERIC(18,4) NOT NULL,
  open_price      NUMERIC(18,4) NOT NULL,
  gap             NUMERIC(10,6) NOT NULL,       -- open / previous_close - 1
  nearest_ratio   TEXT,                         -- e.g. 'bonus 1:1', or null
  detail          TEXT NOT NULL,
  status          TEXT NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
  resolution      TEXT CHECK (resolution IN ('NO_CORPORATE_ACTION', 'ADJUSTED')),
  factor          NUMERIC(18,8) CHECK (factor > 0),   -- shares multiplier when ADJUSTED
  resolved_at     TIMESTAMPTZ,
  resolved_by     TEXT,
  resolution_note TEXT,
  -- Set by the application from its Clock; no database defaults.
  created_at      TIMESTAMPTZ NOT NULL,
  CHECK ((status = 'OPEN') = (resolution IS NULL)),
  CHECK (factor IS NULL OR resolution = 'ADJUSTED')
);

-- At most one open review per position.
CREATE UNIQUE INDEX uq_paper_review_open
  ON paper_review (account_id, instrument_id) WHERE status = 'OPEN';
