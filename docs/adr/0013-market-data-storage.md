# ADR 0013: Market data storage (instrument and candle tables)

- Status: accepted
- Date: 2026-11-26

## Context
Sprint 2 needs somewhere to keep the instrument master and OHLCV candles. Phase 1 has end-of-day
data only (intraday needs the paid Kite plan, ADR 0012): about 2,000 shares times 250 trading days
is roughly half a million rows a year.

The Kite NSE instruments file (`https://api.kite.trade/instruments/NSE`, checked 2026-11-26:
downloads without authentication, 12 columns, about 10,500 rows) mixes shares with other
instruments under the EQ type. The series shows in the symbol suffix: none for regular shares, and
for example -BE, -SM, -ST, -SG (state government securities), -GS, -TB (treasury bills), -N0 and
-N1 (bonds). About 1,700 rows have no name. Tick sizes vary (0.01, 0.05, 0.1, 0.5).

## Decision
- `candle` is one plain table keyed by (instrument_id, bar_interval, ts). No partitioning or
  TimescaleDB until intraday data makes the volume matter.
- Prices are NUMERIC(18,4), mapped to BigDecimal. Timestamps are TIMESTAMPTZ (UTC). DAY bars sit
  at midnight IST, enforced by a CHECK.
- The table repeats the validator's hard rules as CHECK constraints (positive prices, high and low
  bracket open and close, non-negative volume, known interval), so bad rows cannot land even if a
  caller skips validation. Each candle records its source and ingestion time (set from the Clock).
- `instrument` holds every row of the file, with a derived `series` column; consumers such as the
  name-to-symbol resolver filter to the series they need. The natural key is (exchange,
  trading_symbol); a surrogate id is what candles reference, so a symbol change does not rewrite
  candles. The Kite token is an indexed attribute, not a key, because whether Kite reuses tokens
  is unverified.
- The master is upserted daily with first_seen_on and last_seen_on. Rows are never deleted;
  absence from the latest file means inactive.

## Consequences
Simple to query and to test. Moving to partitions later means a new table and a copy, acceptable
at our volume. Field-level history of instruments (for example a name change) is not kept; if it
becomes necessary, add a history table. Symbol changes after corporate actions will appear as a
new instrument and an inactive old one until we link them (corporate actions, backlog B-020).
