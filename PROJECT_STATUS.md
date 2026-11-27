# Project status

Where AuraTrade stands now and what comes next. Keep it short. Update it when a PR changes the picture.
What exists in the code, module by module, is in `CLAUDE.md` under "Current state". Decisions live in `docs/adr`, open work in `docs/backlog.md`, and the sprint plan in `docs/product/roadmap-90-day.md`.

Last updated: 2026-11-28 (from `main` at 556c803).

## Phase and sprint
- Phase 1: research, playbooks and paper trading only. No live orders, no paid data and no LLM spend.
- Phase 0 reset is done: ADRs 0030 to 0037 and the 90-day roadmap (PR #35).
- Sprint R1 "Trustworthy decisions" runs 2026-11-30 to 2026-12-11. The end-of-R1 demo is on Fri 2026-12-11 at 08:30 IST (`docs/product/demo-r1.md`).

## Running today
- Market data: instrument master (07:00 IST), EOD candles (19:30 IST), resumable backfill, NSE calendar 2018 to 2026.
- Research: three screens, the screen backtest, portfolio simulation, and the 18-setting sweep with out-of-sample results.
- Paper account 1 `relative-strength-1` has been open since 2026-11-26, with the paper job at 20:15 IST. Since V10, a BUY can be approved for fewer shares or with a limit price. Since V11, a possible split or bonus gap holds automatic exits until a person reviews them.
- UI: shell, Command center (with the Today strip), Instrument dossier and Playbook lab.
- Local-only request guard in every profile. There is no authentication yet (B-031).

## Recently merged
| PR | Change |
|---|---|
| #47 | R0-02: the corporate-action guard holds automatic exits on a possible split or bonus gap |
| #45 | End-of-R1 demo plan |
| #43 | Today strip on the Command center (B-040) |
| #42, #38 | MIA golden sets and B-003 injection cases i26 to i31 |
| #39 | R0-01: localhost bind, Host and Origin guard, pinned images and Actions |
| #44, #46 | UI dev proxy on 127.0.0.1; `data/` git-ignored |
| #34, #37, #40, #41 | Business-user review, compliance verification, paper account validity, R1-12 spec and go-live proposal |

## Next (R0 and R1)
- R0-03: load the 2027 calendar once NSE publishes it. The deadline is 2026-12-31, and the calendar throws for 2027 until then.
- R0-04: terms review of data sources (research).
- R1 core for the demo: R1-04 sign-in, R1-05 audit of decisions, R1-09 freshness API, R1-12 next-open reference rerun. The rest of R1 (R1-01 to R1-03, R1-06 to R1-08, R1-10, R1-11, R1-13) is in the roadmap.
- Also open: the first paper proposals and decisions, B-028 batch quotes, walk-forward test, corporate actions B-020, delisted instruments.

## Risks and blockers
- 2027 calendar: there is no coverage, and the startup warning starts on 2026-12-01.
- Machine load: with every session running, tests time out (PR #43). Only one `verify`, Testcontainers or eval run at a time.
- Prices are not adjusted for corporate actions yet (B-020). The V11 guard only holds exits; it does not correct prices.
- Prompt injection case n21 still fails, so model output never triggers an action.
- Not verified from the repo: whether account 1 has proposals or decisions yet. Check `GET /api/v1/paper` on a running stack.

## Handover
Each finished work package adds one line here (date, PR, what changed, what is next) and updates the sections above.
- 2026-11-28: PROJECT_STATUS.md created. Next: keep it current as R1 PRs land.
