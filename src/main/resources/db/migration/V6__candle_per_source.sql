-- ADR 0016: candles are stored per source (provider), so histories from different providers sit
-- side by side and never overwrite each other. Consumers read one source at a time.
ALTER TABLE candle DROP CONSTRAINT candle_pkey;
ALTER TABLE candle ADD CONSTRAINT candle_pkey PRIMARY KEY (instrument_id, bar_interval, source, ts);
ALTER TABLE candle ADD CONSTRAINT ck_candle_source CHECK (source ~ '^[A-Z][A-Z0-9_]{1,31}$');
