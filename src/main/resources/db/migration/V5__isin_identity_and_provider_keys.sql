-- ADR 0015: ISIN is the identity of a security; provider identifiers live in a mapping table.

-- The instrument master moves from the Kite file (no ISIN) to the Upstox file. Rows loaded from
-- Kite cannot be matched to an ISIN, so unreferenced ones are removed and reloaded from Upstox.
-- This is a one-time exception to "rows are never deleted" (ADR 0013): that rule protects rows
-- that candles or aliases point at, and those are kept.
DELETE FROM instrument i
WHERE NOT EXISTS (SELECT 1 FROM candle c WHERE c.instrument_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM instrument_alias a WHERE a.instrument_id = i.id);

ALTER TABLE instrument ADD COLUMN isin TEXT
  CHECK (isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$');

-- A symbol is not unique: a share and its warrants, or bond tranches, can share one. Identity is
-- the ISIN; indices have no ISIN and are identified by segment and symbol.
ALTER TABLE instrument DROP CONSTRAINT uq_instrument_exchange_symbol;
CREATE UNIQUE INDEX uq_instrument_exchange_isin ON instrument (exchange, isin) WHERE isin IS NOT NULL;
CREATE UNIQUE INDEX uq_instrument_exchange_segment_symbol_no_isin
  ON instrument (exchange, segment, trading_symbol) WHERE isin IS NULL;
CREATE INDEX idx_instrument_exchange_symbol ON instrument (exchange, trading_symbol);

-- Kite-specific identifier moves out of the instrument row.
DROP INDEX idx_instrument_kite_token;
ALTER TABLE instrument DROP COLUMN kite_instrument_token;

-- Each provider's own key for an instrument, for example ('UPSTOX', 'NSE_EQ|INE...').
CREATE TABLE instrument_provider_key (
  provider       TEXT NOT NULL,
  provider_key   TEXT NOT NULL,
  instrument_id  BIGINT NOT NULL REFERENCES instrument (id),
  first_seen_on  DATE NOT NULL,
  last_seen_on   DATE NOT NULL,
  PRIMARY KEY (provider, provider_key),
  CONSTRAINT ck_provider_key_seen_order CHECK (last_seen_on >= first_seen_on)
);

CREATE INDEX idx_provider_key_instrument ON instrument_provider_key (instrument_id, provider);
