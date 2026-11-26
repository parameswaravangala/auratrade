/**
 * Display helpers for the Playbook lab. Every number here was computed in Java; these only label,
 * sort and arrange it for the screen.
 */
import type { PortfolioSummary, SweepReport, SweepRow, SweepWindow } from "@/api/schemas";
import { formatPrice } from "./format";

/** Plain-language names for the backend's screen ids. Unknown ids fall back to the id itself. */
const SCREEN_TITLES: Record<string, string> = {
  "52w-high-breakout": "52-week high breakout",
  "pullback-to-50dma": "Pullback to the 50-day average",
  "relative-strength-leaders": "Relative-strength leaders",
};
export const screenTitle = (name: string): string => SCREEN_TITLES[name] ?? name;

/** Why a position was closed (PortfolioSimulator). */
export const EXIT_LABEL: Record<string, string> = {
  STOP: "Stop hit",
  STOP_GAP: "Gapped through stop",
  TIME: "Holding limit",
  CORPORATE_ACTION: "Corporate-action gap",
  END: "Open at end of test",
};

/** Why a share was not evaluated by a screen (ScreenerService). */
export const SKIP_LABEL: Record<string, string> = {
  ILLIQUID: "illiquid",
  SHORT_HISTORY: "short history",
  STALE: "stale data",
  POSSIBLE_CORPORATE_ACTION: "possible corporate action",
};

export const label = (map: Record<string, string>, key: string): string => map[key] ?? key;

/** How a metric from Screen.Hit.metrics is shown. */
export type MetricKind = "price" | "pct" | "times" | "number";

/** Every metric key the three screens emit (com.auratrade.screener.Screens), labelled. */
const METRIC_LABEL: Record<string, { text: string; kind: MetricKind }> = {
  close: { text: "Close", kind: "price" },
  prior52wHigh: { text: "Prior 52-week high", kind: "price" },
  pctAboveHigh: { text: "Above that high", kind: "pct" },
  volumeRatio: { text: "Volume vs 50-day avg", kind: "times" },
  sma50: { text: "50-day avg", kind: "price" },
  sma200: { text: "200-day avg", kind: "price" },
  pctFromSma50: { text: "From 50-day avg", kind: "pct" },
  rsi14: { text: "RSI(14)", kind: "number" },
  trendPct: { text: "50-day vs 200-day avg", kind: "pct" },
  return6mPct: { text: "6-month", kind: "pct" },
  benchmark6mPct: { text: "Nifty 6-month", kind: "pct" },
  outperformancePct: { text: "Ahead of Nifty", kind: "pct" },
};
export function metricLabel(key: string): { text: string; kind: MetricKind } {
  return METRIC_LABEL[key] ?? { text: key, kind: key.endsWith("Pct") ? "pct" : "number" };
}

/** Display text for one metric value. */
export function metricText(kind: MetricKind, v: number): string {
  switch (kind) {
    case "price":
      return formatPrice(v);
    case "pct":
      return `${v.toFixed(2)}%`;
    case "times":
      return `${v.toFixed(1)}×`;
    case "number":
      return v.toFixed(1);
  }
}

export type YearRow = { year: string; strategy: number | null; benchmark: number | null };

/** Calendar-year returns of the strategy next to the benchmark's, oldest first. */
export function yearRows(s: PortfolioSummary): YearRow[] {
  const years = new Set([...Object.keys(s.returnPctByYear), ...Object.keys(s.benchmarkReturnPctByYear)]);
  return [...years].sort().map((year) => ({
    year,
    strategy: s.returnPctByYear[year] ?? null,
    benchmark: s.benchmarkReturnPctByYear[year] ?? null,
  }));
}

/** Exit reasons, most frequent first. */
export function exitRows(s: PortfolioSummary): { reason: string; count: number }[] {
  return Object.entries(s.exitsByReason)
    .map(([reason, count]) => ({ reason, count }))
    .sort((a, b) => b.count - a.count);
}

/** Did a window beat its benchmark on CAGR? null when either side is missing. */
export function beats(w: SweepWindow): boolean | null {
  if (w.cagrPct === null || w.benchmarkCagrPct === null) return null;
  return w.cagrPct > w.benchmarkCagrPct;
}

const rowKey = (r: Pick<SweepRow, "atrMultiple" | "maxHold" | "regimeFilter">) =>
  `${String(r.atrMultiple)}|${String(r.maxHold)}|${String(r.regimeFilter)}`;
export const isChosen = (report: SweepReport, r: SweepRow) => rowKey(report.chosen) === rowKey(r);

export type SweepGrid = {
  stops: number[];
  holds: number[];
  /** cell(stop, hold, regime) for the grid; undefined when the backend sent no such combination. */
  cell: (stop: number, hold: number, regime: boolean) => SweepRow | undefined;
};

/** Arranges the sweep's rows as a grid: stop multiple × holding limit, regime filter off and on. */
export function sweepGrid(report: SweepReport): SweepGrid {
  const rows = report.rowsByInSampleSharpe;
  const byKey = new Map(rows.map((r) => [rowKey(r), r]));
  const sortedUnique = (xs: number[]) => [...new Set(xs)].sort((a, b) => a - b);
  return {
    stops: sortedUnique(rows.map((r) => r.atrMultiple)),
    holds: sortedUnique(rows.map((r) => r.maxHold)),
    cell: (stop, hold, regime) =>
      byKey.get(rowKey({ atrMultiple: stop, maxHold: hold, regimeFilter: regime })),
  };
}

/** The settings the Playbook lab offers; they match the sweep's grid (ADR 0023). */
export const STOP_MULTIPLES = [1.5, 2, 3] as const;
export const HOLD_LIMITS = [20, 60, 120] as const;
