# ADR 0047: Scoring profiles and the combined score

- Status: accepted (plan P6)
- Date: 2026-11-28

## Context
Spec sections 6 and 14.4 ask for one combined score across strategies, with the weights and policy
visible, and only from strategies that passed their gate (plan decision D1, founder's rule). The
selection pipeline (ADR 0044) ranks by the best single strategy score until now.

## Decision
- `ScoringProfile` (pure): a name, a policy and weights per strategy id, total at most 100.
  Balanced Swing uses the spec's weights (momentum 25, pullback 20, relative strength 20, 52-week
  high 15); the regime component (10) is omitted until the regime exists.
- Policies:
  - `STRICT`: the configured weights as they are. Combined = sum of weight x best passed score,
    divided by 100.
  - `AUTO_NORMALIZED`: weights of the approved strategies rescaled to 100. Combined = sum of
    weight x score, divided by the approved weight total.
  - `FIXED_CONTRIBUTION` from the spec is not built separately: an unapproved strategy scores zero
    under both policies, so the difference would be only the normalisation, which `STRICT` and
    `AUTO_NORMALIZED` already cover.
- Only approved strategies count (ADR 0042, 0044). An approved strategy that did not pass keeps its
  weight in the denominator under `AUTO_NORMALIZED`, so one weak day is not hidden.
- The shortlist is ordered by the combined score, then by symbol. The response reports the profile
  and each candidate's combined score, next to the best single score.

## Consequences
- Until a strategy is approved, every combined score is zero and the shortlist is empty. This is
  intended.
- Not built: stored profiles and versions (profiles are code), the what-if simulator (spec 14.6),
  the stock-level matrix across all strategies (spec 14.5), and the regime component. A profile
  change is a new version and must be recorded before it is used for decisions.
- Weights are the spec's starting hypothesis. They are not tuned; any change goes through the gate
  of the combined portfolio, not this score alone.
