-- Hand-curated names for instruments (ADR 0014): short forms and brand names used in news that
-- the resolver cannot derive from the listed name. Added by a human, never by a model.
CREATE TABLE instrument_alias (
  -- Normalised by the application (upper case, punctuation and legal suffixes removed).
  alias_key      TEXT PRIMARY KEY,
  alias          TEXT NOT NULL,
  instrument_id  BIGINT NOT NULL REFERENCES instrument (id),
  note           TEXT,
  -- Set by the application from its Clock; no database default.
  created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_instrument_alias_instrument ON instrument_alias (instrument_id);
