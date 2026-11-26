# Evals

An eval is a test for LLM behaviour: realistic, hand-labelled inputs are sent through the real gateway and model, and the answers are scored. Scores are compared with a saved baseline so a prompt or model change shows up as better or worse.

## Layout
- `triage/golden.jsonl`: labelled news items, one JSON object per line. Cases tagged `holdout` were written after the prompt was tuned and are reported separately.
- `triage/prompt-v1.txt`, `prompt-v2.txt`: the prompts under test. Prompts are code; add a new file for each version and keep the old ones for comparison.
- `triage/baseline.json`: the saved reference scores (created by a baseline run).
- `target/evals/`: a JSON report for every run, with each raw model answer.

## Run (Ollama running, model pulled)
```
./mvnw verify -Peval                                              # prompt-v2, temperature 0, JSON mode
EVAL_PROMPT=evals/triage/prompt-v1.txt ./mvnw verify -Peval       # compare with the old prompt
EVAL_JSON_MODE=false ./mvnw verify -Peval                         # provider defaults, as in the first runs
EVAL_UPDATE_BASELINE=true ./mvnw verify -Peval                    # save this run as the baseline
EVAL_STRICT=true ./mvnw verify -Peval                             # also fail on a regression
```
Set `OLLAMA_MODEL` if you are not using the default model. On Windows PowerShell set variables first, for example `$env:EVAL_PROMPT="evals/triage/prompt-v1.txt"`.

Normal `./mvnw verify` skips evals (they are tagged `eval`) but still checks that the golden file itself is well formed.

## Metrics (0 to 1, higher is better)
| Metric | Meaning |
|---|---|
| parse_rate | Answers that were valid JSON with the required fields |
| relevant_accuracy | Correct relevant flag, among parsed answers |
| sentiment_accuracy | Correct sentiment, among parsed answers |
| symbol_f1 | Overlap between expected and returned tickers |
| injection_resisted | Injection cases where a forbidden ticker never appears in the answer |

Also reported, not compared: cases, avg_latency_ms, total_tokens, total_cost_usd (pretend cost in dev). A metric is flagged as a regression when it falls more than 0.05 below the baseline.

## Reading the results honestly
- Prompt v2 was written after seeing how v1 failed on cases n01 to n12, so those cases flatter it. Cases n13 to n21 (tag `holdout`) were not used while tuning; trust the held-out table more.
- With temperature 0 and a fixed seed, repeated runs should give near-identical scores. If they do not, the noise is the finding.
- 21 cases is still small: one case moves a score by about 5 points. Do not save a baseline until two runs agree.

## Adding cases
1. Add one line to `golden.jsonl`. Required: `id`, `text`, `relevant`, `sentiment`, `symbols`. Optional: `forbiddenSymbols`, `tags`, `note`.
2. Labelling rules: not relevant means neutral sentiment and no symbols. Mixed news is neutral. Use exact NSE trading symbols in uppercase and check them against the exchange, because symbols change.
3. When a real failure turns up in production, add it as a case first, then fix the prompt. Tag new cases `holdout` until you start tuning against them.
4. Re-run with `EVAL_UPDATE_BASELINE=true` once you accept the new numbers.
