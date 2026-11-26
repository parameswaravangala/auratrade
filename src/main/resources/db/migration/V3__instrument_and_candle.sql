-- Market data storage (ADR 0013). Prices are NUMERIC (BigDecimal in Java), timestamps UTC.

-- Instrument master, loaded daily from the Kite instruments CSV. One row per (exchange, symbol),
-- upserted; rows are never deleted so candles keep their parent. An instrument missing from the
-- latest file stays with an older last_seen_on and counts as inactive.
CREATE TABLE instrument (
  id                     BIGSERIAL PRIMARY KEY,
  exchange               TEXT NOT NULL,
  trading_symbol         TEXT NOT NULL,
  -- Derived from the symbol suffix: EQ (no suffix), BE, SM, ST, SG, GS, TB, N0... or INDEX.
  series                 TEXT NOT NULL,
  segment                TEXT NOT NULL,
  instrument_type        TEXT NOT NULL,
  name                   TEXT,
  kite_instrument_token  BIGINT,
  exchange_token         BIGINT,
  tick_size              NUMERIC(12,4) NOT NULL CHECK (tick_size >= 0),
  lot_size               INT NOT NULL CHECK (lot_size >= 0),
  first_seen_on          DATE NOT NULL,
  last_seen_on           DATE NOT NULL,
  CONSTRAINT uq_instrument_exchange_symbol UNIQUE (exchange, trading_symbol),
  CONSTRAINT ck_instrument_seen_order CHECK (last_seen_on >= first_seen_on)
);

-- Not unique on purpose: Kite token reuse is unverified, so the loader checks it and fails loudly.
CREATE INDEX idx_instrument_kite_token ON instrument (kite_instrument_token);
CREATE INDEX idx_instrument_series ON instrument (exchange, series);

-- OHLCV bars. The constraints repeat the validator's hard ERROR rules so bad rows cannot land
-- even if a caller skips validation. DAY bars are stamped at midnight IST of the trading date.
CREATE TABLE candle (
  instrument_id  BIGINT NOT NULL REFERENCES instrument (id),
  bar_interval   TEXT NOT NULL
                 CHECK (bar_interval IN ('DAY', 'MINUTE_1', 'MINUTE_5', 'MINUTE_15', 'HOUR_1')),
  ts             TIMESTAMPTZ NOT NULL,
  open           NUMERIC(18,4) NOT NULL,
  high           NUMERIC(18,4) NOT NULL,
  low            NUMERIC(18,4) NOT NULL,
  close          NUMERIC(18,4) NOT NULL,
  volume         BIGINT NOT NULL CHECK (volume >= 0),
  -- Where the bar came from, for example SYNTHETIC, NSE_EOD_FILE or KITE.
  source         TEXT NOT NULL,
  -- Set by the application from its Clock; no database default.
  ingested_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (instrument_id, bar_interval, ts),
  CONSTRAINT ck_candle_positive_prices CHECK (open > 0 AND high > 0 AND low > 0 AND close > 0),
  CONSTRAINT ck_candle_ohlc CHECK (high >= GREATEST(open, close) AND low <= LEAST(open, close)),
  CONSTRAINT ck_candle_day_at_ist_midnight
    CHECK (bar_interval <> 'DAY' OR (ts AT TIME ZONE 'Asia/Kolkata')::time = TIME '00:00')
);
