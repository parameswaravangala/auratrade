/** LLM budget rules from ADR 0006, shown on the budget ring. The backend enforces them; this only labels. */
export const THRESHOLDS = [
  { at: 60, label: "drop a tier" },
  { at: 75, label: "to floor" },
  { at: 85, label: "non-critical stop" },
  { at: 100, label: "all stop" },
] as const;

export type BudgetStage = "normal" | "downgraded" | "at-floor" | "non-critical-stopped" | "stopped";

export function budgetStage(usedPercent: number): BudgetStage {
  if (usedPercent >= 100) return "stopped";
  if (usedPercent >= 85) return "non-critical-stopped";
  if (usedPercent >= 75) return "at-floor";
  if (usedPercent >= 60) return "downgraded";
  return "normal";
}

/** Share of the ring to fill, 0 to 1. Keeps a small visible sliver for any spend above zero. */
export function ringFill(usedPercent: number): number {
  if (usedPercent <= 0) return 0;
  return Math.min(1, Math.max(0.015, usedPercent / 100));
}
