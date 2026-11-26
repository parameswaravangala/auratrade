-- ADR 0018: candle history is loaded by resumable backfill requests. Each request is split into
-- one item per instrument; the backfill job works through pending items in batches, so a crash,
-- restart or provider outage resumes where it stopped instead of starting again.

CREATE TABLE backfill_request (
  id            BIGSERIAL PRIMARY KEY,
  source        TEXT NOT NULL,
  bar_interval  TEXT NOT NULL,
  date_from     DATE NOT NULL,
  date_to       DATE NOT NULL,
  series        TEXT NOT NULL,                 -- comma-separated, for example EQ or EQ,BE
  status        TEXT NOT NULL CHECK (status IN ('PENDING', 'DONE', 'DONE_WITH_FAILURES', 'CANCELLED')),
  -- Set by the application from its Clock; no database defaults.
  created_at    TIMESTAMPTZ NOT NULL,
  finished_at   TIMESTAMPTZ,
  CONSTRAINT ck_backfill_range CHECK (date_to >= date_from)
);

CREATE TABLE backfill_item (
  request_id     BIGINT NOT NULL REFERENCES backfill_request (id),
  instrument_id  BIGINT NOT NULL REFERENCES instrument (id),
  trading_symbol TEXT NOT NULL,
  provider_key   TEXT NOT NULL,
  status         TEXT NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
  attempts       INT NOT NULL DEFAULT 0,
  candles_stored INT NOT NULL DEFAULT 0,
  last_error     TEXT,
  updated_at     TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (request_id, instrument_id)
);

CREATE INDEX idx_backfill_item_pending ON backfill_item (request_id, status);
