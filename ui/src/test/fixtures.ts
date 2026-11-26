import type { Candle, JobRun, SeriesCount, SpendReport } from "@/api/schemas";

/** Synthetic test data. Symbols and companies are fictional. */
export const NOW = new Date("2026-11-26T11:30:00Z"); // 17:00 IST

export function run(p: Partial<JobRun> & Pick<JobRun, "id" | "jobName">): JobRun {
  return {
    trigger: "SCHEDULE",
    windowFrom: "2026-11-26",
    windowTo: "2026-11-26",
    status: "SUCCEEDED",
    itemsOk: 10,
    itemsFailed: 0,
    detail: null,
    startedAt: "2026-11-26T10:00:00Z",
    finishedAt: "2026-11-26T10:00:10Z",
    ...p,
  };
}

export const RUNS: JobRun[] = [
  run({
    id: 1,
    jobName: "instrument-master",
    trigger: "STARTUP",
    itemsOk: 10_048,
    itemsFailed: 1,
    startedAt: "2026-11-26T11:10:51Z",
    finishedAt: "2026-11-26T11:11:01Z",
  }),
  run({
    id: 2,
    jobName: "eod-candles",
    trigger: "STARTUP",
    status: "PARTIAL",
    itemsOk: 0,
    itemsFailed: 50,
    detail: "Upstox token not set",
    startedAt: "2026-11-26T11:11:02Z",
    finishedAt: "2026-11-26T11:11:06Z",
  }),
];

export const SPEND: SpendReport = {
  monthSpendUsd: 0.42,
  budgetUsd: 50,
  usedPercent: 0.84,
  month: [{ agent: "ADHOC_CHAT", tier: "LOCAL", runs: 3, tokens: 1200, costUsd: 0 }],
  today: [],
};

export const SERIES: SeriesCount[] = [
  { series: "EQ", active: 2418, inactive: 12 },
  { series: "BE", active: 682, inactive: 3 },
  { series: "SM", active: 412, inactive: 0 },
];

export const SHARE = { instrumentId: 7, tradingSymbol: "ACME", name: "Acme Cookers Limited", series: "EQ" };

export function candles(n: number): Candle[] {
  return Array.from({ length: n }, (_, i) => {
    const day = String(i + 2).padStart(2, "0");
    const c = 100 + i;
    return {
      ts: `2026-11-${day}T00:00:00+05:30`,
      open: c - 1,
      high: c + 2,
      low: c - 2,
      close: c,
      volume: 1000 + i,
    };
  }).map((c) => ({ ...c, ts: new Date(c.ts).toISOString() }));
}
