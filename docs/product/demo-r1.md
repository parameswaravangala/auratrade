# Demo 1: "Can I trust what I see each morning?" (end of R1)

- Owner: Product Owner (content) and Delivery Coordinator (schedule and dry runs). Audience and approver: founder.
- Approved by the founder 2026-11-27.
- When: the last day of sprint R1 (Friday 2026-12-11, see [roadmap](roadmap-90-day.md)), starting 08:30 IST, about 60 minutes. If that day is not a trading session in `src/main/resources/calendar/nse-calendar.csv`, the demo moves to the next session day.
- Why 08:30 on a session day: the real pre-open decision on paper account 1 happens inside the demo, and the founder makes it. It is not scripted.
- Outcome: a go or no-go for R2, and a yes or no on opening paper account 2 with the swept settings. The founder opens account 2 in person, and only after R0-02 has merged.

## Script

| # | Segment (min) | What is shown | Stories | Owner |
|---|---|---|---|---|
| 1 | Phase 0 recap (10) | Architecture one-pager, ADRs 0030 to 0037, roadmap, founder decisions F1 to F11 as recorded | Phase 0 package | Delivery Coordinator |
| 2 | Morning check (10) | Sign-in (if R1-04 has landed). The Today strip: orders waiting with the countdown to their deadline, last night's EOD run, job problems. Freshness counted in trading sessions | B-040 (#43), R1-09, R1-04 | UI Cockpit, Backend Engine |
| 3 | The decision (10) | The founder approves or rejects account 1's real proposals in the Approval queue before 09:15 IST. An audit row records the decider, taken from the sign-in | R1-05 | UI Cockpit, Backend Engine |
| 4 | When things break (10) | A job failure set off on purpose on a demo branch or by a test switch. The alert email arrives, the run shows as failed or abandoned, and recovery needs no SQL | R1-11, #31 | Backend Engine |
| 5 | Safety nets (10) | The corporate-action guard flagging a held position, backup and restore test results, CI gates (Trivy, Semgrep, SBOM). The version upgrades, ArchUnit rules and tenant scaffold appear as CI and test evidence | R0-02, R1-10, R1-08, R1-01 to R1-03, R1-06, R1-07 | Backend Engine, Platform & Security |
| 6 | Strategy truth (10) | The R1-12 rerun under paper exit rules, and the risk sweep against the -25% limit (settings chosen in-sample, out of sample reported once). The proposed settings for account 2 | R1-12, B-051 step 2, F5 option 2 | Quant Research |

Segments 2, 3 and 6 are the core. If R1 runs short, 4 and 5 become a status slide.

## Rules

- **Real data, read only.** Nobody acts for the founder. The only paper action is the founder's own decision in segment 3.
- **Nothing is staged.** Work not merged by the morning of 2026-12-10 is shown as "not done", with its PR. If R1-04 and R1-05 slip, segment 3 runs on the dev profile without the audit row, and the demo says so.
- **Failures are set off only on a demo branch or by a test switch,** never by editing the shared database.
- **No spend.** Alert email goes through the founder's Gmail. Models run on local Ollama.

## Preparation

| Date | Step | Owner |
|---|---|---|
| 2026-12-09 | Dry run 1 of the full script. Business User Desk plays the founder and logs friction | Delivery Coordinator, Business User Desk |
| 2026-12-10 | Freeze: merged work only. The demo checklist (start order, health checks, fallback per segment) goes into `docs/runbooks/demo.md` | Delivery Coordinator, Platform & Security |
| 2026-12-10 evening | EOD and paper jobs run normally. Check EOD coverage is at least 90% | Backend Engine |
| 2026-12-11 07:45 IST | Stack up (Postgres, backend, UI, Ollama), other sessions paused | Platform & Security |
| 2026-12-11 08:30 IST | Demo | All segment owners |

Each segment owner sends the Delivery Coordinator a one-line status every Friday until the demo: ready, at risk (why) or dropped.

## Risks

1. **Machine load (highest).** PR #43 reported 100% CPU and about 0.5 GB of free RAM with every session running, and tests timing out. During dry runs and the demo, only the sessions that are presenting run. The Delivery Coordinator caps how many builds run at once.
2. **R1 scope.** Thirteen stories in one sprint. The core segments come first, and the rest can drop to a status slide.
3. **A live trading decision inside a demo.** This is by design, and the paper decisions stay the founder's alone.
