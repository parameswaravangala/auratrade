# ADR 0016: Candles stored per provider; one selected provider per environment

- Status: accepted
- Date: 2026-11-26

## Context
The platform will support more than one candle provider (Upstox now; Kite later, with one
interactive login per day). Providers can differ in split and bonus adjustment, close and volume
definitions, history depth and intervals. Mixing two providers in one price series creates false
jumps and makes backtests irreproducible. ADR 0013 keyed candles by instrument, interval and time,
so a second provider would have overwritten the first.

## Decision
- The candle key includes the source: (instrument_id, bar_interval, source, ts) (V6). Each
  provider's history is kept separately and never overwrites another's.
- Consumers read candles through `MarketDataProvider` and `CandleIngestionService`, from one
  selected provider: `auratrade.marketdata.candles.provider`. Development defaults to `upstox`;
  production has no default (`CANDLE_PROVIDER`) so startup fails until it is chosen. An unknown name
  stops startup.
- Ingestion is fail closed: provider candles are validated by `CandleValidator`; any ERROR stores
  nothing from that request. Providers parse prices straight into BigDecimal and return candles
  oldest first; a response in an unknown shape fails rather than being stored.
- Each provider has a client-side rate limiter below its published limits (Upstox: 80% of 50/s,
  500/min, 2000/30 min).
- Switching provider is a planned cutover: backfill the new provider's history, compare it with the
  current one over the overlap (matching closes within a tolerance, gaps, split-shaped jumps), then
  change the setting. A fallback provider's candles stay in its own series and are never blended
  into the primary series.

## Consequences
Provider differences stay visible and auditable, and switching cannot silently corrupt history.
Storage roughly doubles for instruments held from two providers, which is small at end-of-day
volumes. Backtests and signals should record the source they used. Validation needs a covered
trading calendar, so backfilling years before 2026 needs historical calendars or a decided policy
(backlog B-026).
