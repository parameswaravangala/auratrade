# ADR 0014: Resolving company names to NSE symbols in code

- Status: accepted
- Date: 2026-11-26

## Context
In the Sprint 1 evals a 3B model invented tickers (SPML, ASP, LARSEN&TUBRO, BAJAJFINANCE), and
prompt injection case n21 succeeded every run. Backlog B-001: the model should only extract
company names as written, and code should turn them into symbols.

The Kite NSE instruments file (loaded into `instrument`, ADR 0013) has about 3,600 company shares
once bonds, government securities, indices, ETF iNAV rows (series EQ, symbol ending INAV) and
rights entitlements are left out. About 87% of names are short and clean ("HAWKINS COOKERS"); about
10% are abbreviated and truncated ("PARAS DEF AND SPCE TECH L"). A few names belong to two symbols
(a share and its DVR class).

## Decision
- `SymbolResolver` is pure Java over an in-memory list of active company shares (series EQ, BE,
  SM, ST, BZ; not INAV; not -RE rights; present in the latest load). `SymbolResolverService`
  rebuilds it from the database after each instrument load or alias change.
- Order: curated alias, exact symbol, exact normalised name, then fuzzy. Normalising: upper case,
  "&" to AND, punctuation removed, leading THE and trailing LTD, LIMITED, PLC and a truncated "L"
  dropped.
- Fuzzy: every word of the listed name must match a word of the input, in order: exactly (1.0),
  as a prefix (0.9, "DEF" for DEFENCE) or as an abbreviation with the same first letter and the
  letters in order (0.8, "SPCE" for SPACE). Score is the sum over the longer word count. Accept the
  best only if it scores at least 0.75 and leads the next by at least 0.10.
- Never guess. The result is a sealed type: Resolved, Ambiguous (with candidates) or Unresolved.
  Only Resolved carries a symbol, and it always exists in `instrument`.
- `instrument_alias` (V4) holds hand-curated names (brands, acronyms used in news). Aliases are
  added by a human through the dev endpoint, never by a model, and must point at an active share.

Alternatives considered: Postgres `pg_trgm` similarity (logic split between SQL and Java, tested
only through ITs); exact and prefix only (leaves the abbreviated 10% unresolved); embeddings with
pgvector (needs a model call, not deterministic, overkill for 3,600 names).

## Consequences
Wrong tickers cannot come out of the resolver, and resolution is testable without a database.
Names news writes differently from the listing (brand names, a group name alone, missing words)
come back unresolved until someone adds an alias; that is the intended trade-off. Thresholds are
starting values: tune them from the triage golden set once prompt v3 extracts names (B-001).
