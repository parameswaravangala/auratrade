import type {
  Candle,
  JobRun,
  PortfolioReport,
  ScreenInfo,
  ScreenResult,
  SeriesCount,
  SpendReport,
  SweepReport,
  SweepRow,
  SweepWindow,
} from "@/api/schemas";

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

// ---- Playbook lab (synthetic; fictional shares) ----

export const SCREENS: ScreenInfo[] = [
  {
    name: "relative-strength-leaders",
    description: "Six-month return ahead of the benchmark.",
    lookback: 127,
  },
  { name: "pullback-to-50dma", description: "Uptrend pulled back to the 50-day average.", lookback: 201 },
];

export const SHORTLIST: ScreenResult = {
  screen: "relative-strength-leaders",
  description: "Six-month return ahead of the benchmark.",
  asOf: "2026-11-25",
  source: "UPSTOX",
  evaluated: 1200,
  matched: 40,
  skipped: { ILLIQUID: 600, STALE: 3 },
  shortlist: [
    {
      symbol: "ACME",
      name: "Acme Cookers Limited",
      score: 1.5,
      metrics: { close: 210.5, return6mPct: 160.25, outperformancePct: 150.1 },
      reason: "Up 160.25% in six months against 10.15% for the benchmark",
    },
    {
      symbol: "NOVA",
      name: "Nova Fabrics Limited",
      score: 0.9,
      metrics: { close: 88.2, return6mPct: 100.0, outperformancePct: 89.85 },
      reason: "Up 100.00% in six months against 10.15% for the benchmark",
    },
  ],
};

const win = (cagr: number, bench: number): SweepWindow => ({
  cagrPct: cagr,
  maxDrawdownPct: -20,
  sharpe: 1,
  trades: 50,
  benchmarkCagrPct: bench,
  benchmarkMaxDrawdownPct: -15,
});

export const PORTFOLIO: PortfolioReport = {
  request: {
    screen: "relative-strength-leaders",
    from: "2019-01-01",
    to: "2026-11-25",
    initialCapital: 1_000_000,
    maxPositions: 10,
    riskPerTradePct: 1,
    maxPositionPct: 20,
    atrMultiple: 2,
    maxHoldSessions: 60,
    slippageBps: 5,
    costs: true,
    regimeFilter: false,
  },
  source: "UPSTOX",
  benchmark: "NIFTY",
  signalDays: 1900,
  candidateShares: 300,
  seconds: 61,
  summary: {
    startEquity: 1_000_000,
    endEquity: 2_500_000,
    totalReturnPct: 150,
    cagrPct: 12.3,
    maxDrawdownPct: -31.4,
    annualVolatilityPct: 22,
    sharpe: 0.85,
    benchmarkTotalReturnPct: 90,
    benchmarkCagrPct: 8.7,
    benchmarkMaxDrawdownPct: -38.4,
    trades: 420,
    winRatePct: 41,
    avgWinPct: 12,
    avgLossPct: -5,
    profitFactor: 1.6,
    avgSessionsHeld: 30,
    avgPositions: 8,
    totalCosts: 85_000,
    exitsByReason: { STOP: 300, TIME: 110, END: 10 },
    returnPctByYear: { "2025": -4.2, "2024": 30.1 },
    benchmarkReturnPctByYear: { "2025": 2.0, "2024": 8.8 },
  },
  worstTrades: [
    {
      symbol: "NOVA",
      entryDate: "2024-03-01",
      entryPrice: 100,
      quantity: 500,
      exitDate: "2024-03-20",
      exitPrice: 80,
      exitReason: "STOP_GAP",
      sessionsHeld: 13,
      costs: 120,
      netPnl: -10_120,
    },
  ],
  bestTrades: [
    {
      symbol: "ACME",
      entryDate: "2024-01-02",
      entryPrice: 100,
      quantity: 500,
      exitDate: "2024-04-01",
      exitPrice: 190,
      exitReason: "TIME",
      sessionsHeld: 60,
      costs: 150,
      netPnl: 44_850,
    },
  ],
  equityCurveWeekly: [
    { date: "2019-01-04", equity: 1_000_000, cash: 1_000_000, positions: 0, benchmark: 1_000_000 },
    { date: "2026-11-20", equity: 2_500_000, cash: 300_000, positions: 9, benchmark: null },
  ],
  caveats: ["Survivorship bias: delisted shares are missing."],
};

const row = (atr: number, hold: number, regime: boolean, outCagr: number): SweepRow => ({
  atrMultiple: atr,
  maxHold: hold,
  regimeFilter: regime,
  inSample: win(15, 13.5),
  outOfSample: win(outCagr, 9.4),
});

export const SWEEP: SweepReport = {
  screen: "relative-strength-leaders",
  from: "2019-01-01",
  split: "2023-01-01",
  to: "2026-11-25",
  fixedSettings: "INR 10 lakh, 10 positions",
  chosen: row(3, 120, true, 20.2),
  combinations: 3,
  combinationsBeatingBenchmarkOutOfSample: 2,
  seconds: 200,
  rowsByInSampleSharpe: [row(3, 120, true, 20.2), row(2, 60, false, 4.1), row(2, 120, true, 11)],
  caveats: [],
};
