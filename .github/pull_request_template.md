## What and why
<!-- One or two sentences. Link the issue. -->

## Self-review checklist
- [ ] Tests added or updated; CI is green
- [ ] No secrets, keys or tokens in the diff
- [ ] LLM calls go through the gateway; budget, tier and usage logging respected
- [ ] Any new prompt is versioned and covered by an eval
- [ ] Money uses BigDecimal or paise; time uses the injected Clock; UTC stored, IST displayed
- [ ] Migrations are forward-only and backward compatible for one release
- [ ] Docs, ADR or runbook updated if behaviour changed
- [ ] Safety paths (risk, kill switch, budget) still fail closed
