# Swing scanner and Strategy Studio: gap analysis and delivery plan

- Baseline spec: [`swing-scanner-spec.md`](swing-scanner-spec.md) (founder's document, updated 2026-11-28; sections 14 to 20 add the Strategy Studio, scoring profiles and the wider platform roadmap).
- Founder's rule: implement and backtest each strategy on its own against a gate fixed in advance before any combined ranking is built. A combined score uses only strategies that passed.
- Status: approved by the founder 2026-11-28 (decisions D1 to D8 and the gate in section 3). Not in the 90-day roadmap yet; R1 (2026-11-30 to 2026-12-11) covers sign-in, audit and freshness, so this work competes with it for time.

## 1. Gap analysis (spec section 20, items 1 and 2)

| Spec capability | Today | Gap |
|---|---|---|
| Daily OHLCV, 3 to 5 years | Upstox daily bars from 2018, EOD job, backfill | Not adjusted for corporate actions (B-020); the 40% guard skips splits instead of correcting them |
| Indicators | `Indicators`: SMA, EMA, RSI, ATR, highest, lowest, change (BigDecimal, unit-tested) | Swing highs and lows, higher-high/higher-low structure, volume-ratio helpers |
| Strategies | Three hardcoded `Screen`s in `Screens.all()`, match or no match with one ranking number | Versioned strategy definitions; pass, fail or unknown for each condition; a 0 to 100 score; trade plan output. The spec's rules differ from all three existing screens |
| Strategy lifecycle (14.2, 14.10) | None | Definition, immutable versions, status workflow, enable, pause, archive, rollback |
| Stored evaluations (17) | Shortlists recomputed on every request (B-042) | `screening_run` and `strategy_evaluation` stored separately from the combined score |
| Scoring profiles, what-if (14.4 to 14.6) | None | Profiles, weight policies, dry run, activation |
| Trade plan | ATR stop, entry at the next open, exit on stop or holding limit | Buy-stop trigger, structural stops, 2R/3R targets, partial exits, trailing (20 EMA or 2 ATR), invalidation, no-chase rule |
| Backtest | Screen replay, portfolio simulator with costs, 18-setting sweep, one in/out-of-sample split | Walk-forward; results by regime, sector and year; Sortino, expectancy, profit factor, turnover, gap losses; experiment records (15.9) |
| Correlation (14.8) | None | Signal overlap and return correlation between strategies |
| Regime | A single switch: Nifty above its 200-session average | Three states with evidence and confidence, breadth, sector participation |
| Universe | All active EQ shares (about 3,600) above INR 1 crore traded value | NIFTY 500 list and its membership history |
| Sectors | None | Share-to-sector mapping and sector index candles |
| Fundamentals, results dates, news | None; the announcement adapter (R2-03) waits on the terms review (R2-01) | All of strategy 5 and the fundamental veto in strategy 4 |
| Paper trading | Account 1 runs one screen; positions hold their stop | Positions keep the strategy version and exit policy that opened them (spec 20.9) |
| Auth, roles, audit | None yet. R1-04 (sign-in) and R1-05 (`audit_event`, ADR 0037) are in R1 | Configuration changes need a principal and an audit record, so the Studio's write endpoints wait on R1-05 |
| Portfolio, news engine, alerts, journal, assistant, risk cockpit (15) | Partly planned: R2 news inputs, B-027 alerts, B-041 grounded copilot, B-045 and B-052 risk | Roadmap only. No plan here beyond mapping them to existing backlog items |

Deterministic or LLM-assisted (20.4): everything in sections 3 to 14 and 17 is plain Java (CLAUDE.md: code computes). The LLM helps only with news extraction (15.3) and the research assistant (15.7), reads stored facts and never sets a score, weight, status or quantity.

## 2. Decisions needed

**D1. Order of building: evidence first.** Spec section 19 puts the Studio (phase 3) and scoring profiles (phase 4) before backtesting (phase 6). The founder's instruction reverses that for combined ranking. Proposal: build the domain model and stored, versioned evaluations early, because backtests need versions anyway. Then backtest each strategy. Build scoring profiles, weights and what-if only after at least two strategies pass. Until then a profile may be saved as `Experimental` but never activated.

**D2. Code-based strategies first, rule builder later.** Spec 14.7 allows both. A rule builder is a small expression language: parser, evaluator, missing-data semantics, schema validation and its own tests, which is several sprints of work. The five seeds are code-based strategies with a typed, validated parameter schema; their parameters are edited in the Studio. In Spring terms, each strategy is a bean implementing `Strategy`, and its parameters are a versioned, validated record, like `@ConfigurationProperties` stored as data. The rule builder is reconsidered once the five strategies have been through the gate.

**D3. Where configuration lives: the database, not YAML.** The Studio edits configuration at runtime, with approval and audit, so it goes in tables: `strategy_definition`, `strategy_version` (immutable JSONB parameters, status, created_by, change reason), `scoring_profile` and `scoring_profile_version`. Flyway seeds the first versions. The YAML in spec 14.3 becomes the JSON schema of the parameters.

**D4. What a strategy score means.** Proposal: a strategy's score is 0 unless every required condition passes. When all pass, a quality score from 0 to 100 (for example volume ratio and distance from the trigger) ranks the setup. A setup that partly passes appears on the "almost qualified" list (15.4) and adds nothing to a combined score. UNKNOWN on a required condition means the setup is not evaluated, which is never a pass. This matches the spec's example, where a failed strategy contributes 0, and avoids rewarding near misses.

**D5. Universe.** Options:
1. The official NIFTY 500 list from niftyindices.com. It covers the current list only, there is no free membership history, and the download terms are unverified.
2. A point-in-time proxy built from our own candles: the 500 most liquid EQ shares by median traded value over the previous 126 sessions, recomputed monthly. Automated, with no membership look-ahead, but it is not the official list.
3. The proxy for backtests, and the official list for the live shortlist once its terms are verified.

Recommendation: 3, starting with 2. Missing delisted shares still cause survivorship bias, and every result says so.

**D6. Strategy 5 and sector rules wait for data.** No legally usable results or fundamentals source exists today. Strategy 5 is marked BLOCKED, with no invented data. Strategy 3's sector rule and the sector cap are UNKNOWN until a sector source is verified (Upstox index keys and niftyindices files; terms unverified).

**D7. Existing screens and account 1 stay unchanged.** They become comparison baselines. The new strategies get new ids.

**D8. Priority comes from evidence.** The spec ranks momentum breakout first, but the 2026-11-26 sweep found that the 52-week breakout overfits and relative strength is the most robust. The spec's order sets the build order only; profile weights are set from gate results.

## 3. Pre-registered gate for each strategy version

Fixed before any result is seen. A failed version is reported, keeps the status `Rejected` and is never activated. Changing its rules creates a new version, which starts the gate again. This is the "Backtest passed" and "Out-of-sample validated" criterion in spec 14.9, stored as thresholds, not prose.

- Data: the proxy universe, 2019-01-01 to the last completed session, costs plus 5 bps slippage, entry no earlier than the session after the signal.
- Settings chosen on 2019 to 2022 only, from a grid declared in the version (at most 18 combinations). The out-of-sample run from 2023 is done once.
- Walk-forward: choose settings each year on the earlier years, test on the next year, and report each year.
- A version passes only if all of these hold:
  1. At least 100 out-of-sample trades.
  2. Positive expectancy per trade after costs, both out of sample and across the walk-forward.
  3. Profit factor above 1.2 out of sample.
  4. More than half of the grid settings beat Nifty buy and hold out of sample after costs.
  5. Expectancy stays positive without the 5 best trades.
  6. Maximum drawdown no deeper than -25% at the spec defaults (0.75% risk, 5 positions).
- Four strategies with up to 18 settings each will produce a good-looking result by chance. Rules 4 and 5 and the walk-forward guard against that; a single best setting is never enough.

## 4. Architecture and domain model (becomes ADR 0038 once D1 to D8 are agreed)

New module `com.auratrade.strategy`; `screener` and `backtest` depend on it, not the other way round.

- `Strategy` (code, one bean per definition): `id()`, `parameterSchema()`, `requiredData()`, `lookback(params)`, and `evaluate(series, context, params)`, which returns a `StrategyEvaluation`.
- `StrategyEvaluation`: one `ConditionResult` (PASS, FAIL or UNKNOWN, with the value and threshold) per condition, a score from D4, an explanation, and an optional `TradePlan`.
- `TradePlan`: entry trigger (buy-stop price, valid for N sessions, no-chase limit), initial stop and its reason, targets in R, partial-exit fraction, trailing rule (NONE, EMA20 or ATR2), and an invalidation rule. It is pure data, so the simulator and paper trading run the same plan.
- `StrategyRegistry`: code beans matched to `strategy_version` rows. A version whose code id has no bean fails at startup (fail closed).
- Stored: `screening_run` (universe snapshot, data timestamps, regime snapshot, application version), `strategy_evaluation` (per run, share and strategy version), and later `stock_score` (per run and profile version). Re-ranking under another profile reads stored evaluations and recalculates nothing (20.6).
- Scoring (later slice): `ScoringPolicy` is STRICT, AUTO_NORMALIZED or FIXED_CONTRIBUTION (14.4). The effective weights are stored with every score.
- Backtests evaluate in memory and store the run, settings, version ids and trades, not every daily evaluation (about 5 million rows per full run otherwise).
- Paper positions store `strategy_version_id` and their exit policy, so a later version or a disable never changes an open position.

## 5. Delivery sequence (one PR each)

| # | Slice | Depends on |
|---|---|---|
| 1 | This plan, the spec in the repo, backlog items | Founder decisions D1 to D8 |
| 2 | ADR 0038; `strategy` module: `Strategy`, `StrategyEvaluation`, `ConditionResult`, `TradePlan`, parameter schema validation; indicator helpers; unit tests. No database yet | 1 |
| 3 | Proxy universe, point-in-time, with tests | 1 |
| 4 | Plan executor in the simulator (triggers, structural stops, targets, partials, trailing, invalidation), new metrics, walk-forward runner, gate report | 2 |
| 5 to 8 | One strategy per PR: momentum breakout, 20 EMA pullback, 52-week high (fundamental veto UNKNOWN), relative strength (sector rule UNKNOWN). Each has tests, a backtest and a research note against the gate. These four are independent of each other | 3, 4 |
| 9 | Migrations: `strategy_definition`, `strategy_version` with status workflow, `screening_run`, `strategy_evaluation`; seeds the versions with their gate results; daily run job after EOD (also closes B-042) | 5 to 8; writes wait on R1-05 for the principal and audit |
| 10 | Three-state regime with evidence; passing strategies re-run with it | 9 |
| 11 | Correlation and overlap between passing strategies | 10 |
| 12 | Scoring profiles, weight policies, dry run and activation, audited | 11, R1-05 |
| 13 | UI: Strategy Studio, stock matrix, score breakdown, what-if, top 10 | 12; UI Cockpit agrees the JSON first |
| 14 | Paper trading by strategy version and exit policy | 9 |
| later | Strategy 5, sector rules, official NIFTY 500 list, rule builder, section 15 capabilities | Data source decisions, D2 review |

## 6. Risks

- Survivorship bias from missing delisted shares flatters every result; every research note says so.
- Unadjusted prices distort 52-week highs and long EMAs around splits.
- About eight years of daily data (2018 onwards) covers only a few market regimes.
- The Studio's scope (sections 14 to 18) is large. Building it before the strategies are validated would produce exactly the attractive, unvalidated combined score the founder wants to avoid.
- Machine load: one `verify` or long backtest at a time.
