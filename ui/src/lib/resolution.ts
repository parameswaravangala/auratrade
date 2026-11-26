import type { MatchKind, Resolution } from "@/api/schemas";

/** The resolver tries these in order and stops at the first that matches (ADR 0014). */
export const STAGES: readonly MatchKind[] = ["ALIAS", "SYMBOL", "NAME", "FUZZY"];

export type StageState = "miss" | "hit" | "skip" | "unknown";
export type TraceStep = { kind: MatchKind; state: StageState };

/**
 * Reconstructs which stages ran from the outcome: stages before the matching one missed, later ones
 * were not needed. For ambiguous or unresolved names the backend does not say which stage gave up,
 * so the trace is unknown rather than guessed.
 */
export function trace(r: Resolution): TraceStep[] {
  if (r.type !== "RESOLVED") return STAGES.map((kind) => ({ kind, state: "unknown" }));
  const hit = STAGES.indexOf(r.kind);
  return STAGES.map((kind, i) => ({ kind, state: i < hit ? "miss" : i === hit ? "hit" : "skip" }));
}

export const STAGE_HINT: Record<MatchKind, string> = {
  ALIAS: "hand-curated alias",
  SYMBOL: "exact trading symbol",
  NAME: "normalised company name",
  FUZZY: "fuzzy name, threshold 0.75, margin 0.10",
};
