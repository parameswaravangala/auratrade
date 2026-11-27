# ADR 0042: Pre-registered pass gate for strategy versions

- Status: accepted
- Date: 2026-11-28

## Context
Plan section 3 fixes six pass rules before any result is seen, so a strategy cannot be tuned until
it passes. Without code, the rules are prose and a run could be judged loosely. The founder's
rule is that no combined score is built from a strategy that has not passed.

## Decision
`GateEvaluator` (package-private, backtest) evaluates one strategy version from the full-period
results of every parameter setting. It is pure.

- Fixed setting: among settings with at least 30 trades that closed before 1 January 2023, the
  highest expectancy (ties to the lower id). Out of sample, from 2023, that setting is judged once.
- Walk-forward: each year, the setting is re-chosen with trades that closed before 1 January of that
  year. The trades it made in that year are the walk-forward trades. Selection never sees a trade
  closing after its cutoff.
- The six checks (plan section 3), each reported with its value:
  1. at least 100 out-of-sample trades;
  2. out-of-sample expectancy after costs above zero;
  3. walk-forward expectancy after costs above zero;
  4. out-of-sample profit factor above 1.2 (passes with no losing trades);
  5. more than half of the settings beat Nifty buy and hold out of sample, on CAGR after costs;
  6. expectancy stays above zero without the 5 best out-of-sample trades;
  7. out-of-sample maximum drawdown at the spec defaults no deeper than -25%.

  Check 7 is the seventh line of the plan's six rules, because the drawdown and the trade count
  are separate rules in the plan's list. The gate passes only when every check passes.

## Consequences
- Each strategy version gets a report with the value of every check, so a failure says what
  failed and by how much.
- Approximation: a setting's trades in a year come from one full-period portfolio run. Capital
  carried between years is not re-run for each walk-forward choice. Stated in the code.
- The Nifty CAGR is an input the caller computes from the benchmark. The gate does not read
  market data.
- No strategy is implemented yet (plan slices 5 to 8), so the gate has not been run on real data
  and no strategy has passed. Running it needs the candle data, a strategy and the default
  setting identified.
- Multiple testing is limited by the grid size and by the robustness checks (5 and 6), not
  removed. A single best setting never passes on its own.
