# ADR 0012: Market data sourcing for Phase 1

- Status: accepted
- Date: 2026-11-26

## Context
Zerodha's free Personal plan includes orders, GTT and portfolio but no market data; historical and streaming data need the paid Connect plan (about 500 rupees per month, per Zerodha's support page). We have decided not to spend on data or LLM APIs until needed. NSE Data & Analytics sells end-of-day and historical data under a tariff and an undertaking, and its published terms restrict redistribution without permission. This matters for Phase 3 (SaaS) and means exchange data must not leak into our repository.

## Decision
- Sprint 2 builds the data layer on synthetic candles (deterministic, generated in tests) and on end-of-day files that you download yourself for personal research. Those files live in a git-ignored local folder and are never committed or redistributed. Verify the current NSE terms before relying on any free source.
- The instrument master comes from the Kite instruments CSV, which is reportedly downloadable without authentication (verify), cached once a day. It is the source of truth for symbols and for resolving company names.
- The trading calendar is loaded from a file filled from the official NSE holiday list. A year without a COVERS row is unknown, and every query for it fails closed (CalendarNotCoveredException) instead of treating weekdays as trading days. Never fill it from memory or from an assistant.
- Candles are validated before use. ERROR rules block use (inconsistent OHLC, bad prices, duplicates, out of order, closed days, outside session). WARNING rules need a human look (missing days, extreme moves, stale prices).
- Prices are BigDecimal; timestamps are UTC instants; DAY candles are stamped at IST midnight of the trading date.
- The paid Connect plan is bought only when live ticks or intraday history are needed.

## Consequences
We can build and test almost the whole data layer for free. Real-data behaviour (splits, odd sessions) is untested until real files are loaded, hence the warnings and the corporate-action item in the backlog. Licensing for redistribution stays an open Phase 3 question.
