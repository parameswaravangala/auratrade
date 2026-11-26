/** Display formatting only. The UI never computes money; Java does (CLAUDE.md). */
const int = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });
const inr = new Intl.NumberFormat("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2 });
const compact = new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 });

export const formatInt = (n: number): string => int.format(n);
export const formatPrice = (n: number): string => `₹${inr.format(n)}`;
const usdSmall = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 4,
});
/** Dollars; sub-cent amounts keep four decimals so small local spend does not read as zero. */
export const formatUsd = (n: number): string => (n > 0 && n < 0.01 ? usdSmall.format(n) : usd.format(n));
export const formatCompact = (n: number): string => compact.format(n);
export const formatPct = (n: number, digits = 1): string => `${n.toFixed(digits)}%`;
export const formatSigned = (n: number, digits = 2): string =>
  `${n >= 0 ? "+" : "−"}${Math.abs(n).toFixed(digits)}%`;

/** "850 ms", "9.8 s", "2 min 05 s". */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
  const m = Math.floor(ms / 60_000);
  const s = Math.round((ms % 60_000) / 1000);
  return `${m} min ${String(s).padStart(2, "0")} s`;
}
