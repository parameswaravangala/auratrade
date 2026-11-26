/**
 * Response shapes of the backend's dev endpoints, checked at runtime with Zod (like Bean Validation
 * on everything that arrives). Mirrors the Java records named in each comment. When the backend
 * publishes `/api/v1` with OpenAPI, these are generated instead (ADR 0019).
 */
import { z } from "zod";

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "expected yyyy-MM-dd");
const instant = z.string().refine((s) => !Number.isNaN(Date.parse(s)), "expected an ISO instant");

/** com.auratrade.platform.jobs.JobRunRepository.JobRun */
export const JobRun = z.object({
  id: z.number(),
  jobName: z.string(),
  trigger: z.enum(["SCHEDULE", "STARTUP", "MANUAL"]),
  windowFrom: isoDate.nullable(),
  windowTo: isoDate.nullable(),
  status: z.enum(["RUNNING", "SUCCEEDED", "PARTIAL", "FAILED"]),
  itemsOk: z.number(),
  itemsFailed: z.number(),
  detail: z.string().nullable(),
  startedAt: instant,
  finishedAt: instant.nullable(),
});
export type JobRun = z.infer<typeof JobRun>;
export const JobRuns = z.array(JobRun);

/** POST /dev/jobs/{name}/run */
export const JobTriggered = z.object({ job: z.string(), result: z.string() });

/** com.auratrade.marketdata.InstrumentRepository.SeriesCount */
export const SeriesCount = z.object({ series: z.string(), active: z.number(), inactive: z.number() });
export type SeriesCount = z.infer<typeof SeriesCount>;
export const SeriesCounts = z.array(SeriesCount);

/** com.auratrade.llmgateway.SpendRow and SpendReport. Money arrives as JSON numbers; display only. */
const SpendRow = z.object({
  agent: z.string(),
  tier: z.string(),
  runs: z.number(),
  tokens: z.number(),
  costUsd: z.number(),
});
export const SpendReport = z.object({
  monthSpendUsd: z.number(),
  budgetUsd: z.number(),
  usedPercent: z.number(),
  month: z.array(SpendRow),
  today: z.array(SpendRow),
});
export type SpendReport = z.infer<typeof SpendReport>;

/** com.auratrade.marketdata.ShareRef */
export const ShareRef = z.object({
  instrumentId: z.number(),
  tradingSymbol: z.string(),
  name: z.string(),
  series: z.string(),
});
export type ShareRef = z.infer<typeof ShareRef>;

export const MatchKind = z.enum(["ALIAS", "SYMBOL", "NAME", "FUZZY"]);
export type MatchKind = z.infer<typeof MatchKind>;

/**
 * com.auratrade.marketdata.Resolution is a sealed interface serialised without a type field, so the
 * three cases are told apart by their fields. `/api/v1` should add a `type` tag (ADR 0019).
 */
export const Resolution = z.union([
  z
    .object({ share: ShareRef, kind: MatchKind, score: z.number() })
    .transform((r) => ({ type: "RESOLVED" as const, ...r })),
  z.object({ candidates: z.array(ShareRef) }).transform((r) => ({ type: "AMBIGUOUS" as const, ...r })),
  z.object({ reason: z.string() }).transform((r) => ({ type: "UNRESOLVED" as const, ...r })),
]);
export type Resolution = z.infer<typeof Resolution>;

/** com.auratrade.marketdata.Candle. Prices arrive as JSON numbers: display only, never computed. */
export const Candle = z.object({
  ts: instant,
  open: z.number(),
  high: z.number(),
  low: z.number(),
  close: z.number(),
  volume: z.number(),
});
export type Candle = z.infer<typeof Candle>;
export const Candles = z.array(Candle);

/** com.auratrade.marketdata.CandleIngestionService.Result */
export const IngestionResult = z.object({
  symbol: z.string(),
  source: z.string(),
  interval: z.string(),
  from: isoDate,
  to: isoDate,
  fetched: z.number(),
  stored: z.number(),
  newestDate: isoDate.nullable(),
  issues: z.array(z.object({ severity: z.string() }).loose()),
});
export type IngestionResult = z.infer<typeof IngestionResult>;

/** com.auratrade.llmgateway.LlmResult. `text` is untrusted model output: render as text only. */
export const LlmResult = z.object({
  text: z.string(),
  tier: z.string(),
  model: z.string(),
  usage: z.object({ inputTokens: z.number(), outputTokens: z.number() }).loose(),
  costUsd: z.number(),
  runId: z.string().nullable(),
});
export type LlmResult = z.infer<typeof LlmResult>;

/** Spring Boot actuator health (details hidden by configuration). */
export const Health = z.object({ status: z.string() });

/** RFC 9457 problem detail, which the backend returns for handled errors. */
export const ProblemDetail = z
  .object({ title: z.string().optional(), detail: z.string().optional(), status: z.number().optional() })
  .loose();

// ---- Screener and backtests (Playbook lab). BigDecimal arrives as JSON numbers; display only. ----

/** A BigDecimal the backend may leave null (for example a win rate with no trades). */
const dec = z.number().nullable();
/** Java Map<Integer, BigDecimal> arrives with string keys, for example {"2024": 12.3}. */
const byYear = z.record(z.string().regex(/^\d{4}$/), z.number());

/** com.auratrade.screener.ScreenerService.ScreenInfo */
export const ScreenInfo = z.object({ name: z.string(), description: z.string(), lookback: z.number() });
export type ScreenInfo = z.infer<typeof ScreenInfo>;
export const ScreenInfos = z.array(ScreenInfo);

/** com.auratrade.screener.Screen.Hit */
export const ScreenHit = z.object({
  symbol: z.string(),
  name: z.string(),
  score: z.number(),
  metrics: z.record(z.string(), z.number()),
  reason: z.string(),
});
export type ScreenHit = z.infer<typeof ScreenHit>;

/** com.auratrade.screener.ScreenerService.Result */
export const ScreenResult = z.object({
  screen: z.string(),
  description: z.string(),
  asOf: isoDate,
  source: z.string(),
  evaluated: z.number(),
  matched: z.number(),
  skipped: z.record(z.string(), z.number()),
  shortlist: z.array(ScreenHit),
});
export type ScreenResult = z.infer<typeof ScreenResult>;

/** com.auratrade.backtest.PortfolioSimulationService.Request (echoed back in the report) */
export const PortfolioRequest = z.object({
  screen: z.string(),
  from: isoDate,
  to: isoDate,
  initialCapital: z.number(),
  maxPositions: z.number(),
  riskPerTradePct: z.number(),
  maxPositionPct: z.number(),
  atrMultiple: z.number(),
  maxHoldSessions: z.number(),
  slippageBps: z.number(),
  costs: z.boolean(),
  regimeFilter: z.boolean(),
});

/** com.auratrade.backtest.PortfolioSimulator.Trade */
export const Trade = z.object({
  symbol: z.string(),
  entryDate: isoDate,
  entryPrice: z.number(),
  quantity: z.number(),
  exitDate: isoDate,
  exitPrice: z.number(),
  exitReason: z.string(),
  sessionsHeld: z.number(),
  costs: z.number(),
  netPnl: z.number(),
});
export type Trade = z.infer<typeof Trade>;

/** com.auratrade.backtest.PortfolioSimulator.Day. `benchmark` is Nifty scaled to the starting capital. */
export const EquityDay = z.object({
  date: isoDate,
  equity: z.number(),
  cash: z.number(),
  positions: z.number(),
  benchmark: dec,
});
export type EquityDay = z.infer<typeof EquityDay>;

/** com.auratrade.backtest.PortfolioMetrics.Summary. Percentages are already ×100; drawdowns negative. */
export const PortfolioSummary = z.object({
  startEquity: z.number(),
  endEquity: z.number(),
  totalReturnPct: dec,
  cagrPct: dec,
  maxDrawdownPct: dec,
  annualVolatilityPct: dec,
  sharpe: dec,
  benchmarkTotalReturnPct: dec,
  benchmarkCagrPct: dec,
  benchmarkMaxDrawdownPct: dec,
  trades: z.number(),
  winRatePct: dec,
  avgWinPct: dec,
  avgLossPct: dec,
  profitFactor: dec,
  avgSessionsHeld: dec,
  avgPositions: dec,
  totalCosts: dec,
  exitsByReason: z.record(z.string(), z.number()),
  returnPctByYear: byYear,
  benchmarkReturnPctByYear: byYear,
});
export type PortfolioSummary = z.infer<typeof PortfolioSummary>;

/** com.auratrade.backtest.PortfolioSimulationService.Report */
export const PortfolioReport = z.object({
  request: PortfolioRequest,
  source: z.string(),
  benchmark: z.string(),
  signalDays: z.number(),
  candidateShares: z.number(),
  seconds: z.number(),
  summary: PortfolioSummary,
  worstTrades: z.array(Trade),
  bestTrades: z.array(Trade),
  equityCurveWeekly: z.array(EquityDay),
  caveats: z.array(z.string()),
});
export type PortfolioReport = z.infer<typeof PortfolioReport>;

/** com.auratrade.backtest.StrategySweepService.Window */
export const SweepWindow = z.object({
  cagrPct: dec,
  maxDrawdownPct: dec,
  sharpe: dec,
  trades: z.number(),
  benchmarkCagrPct: dec,
  benchmarkMaxDrawdownPct: dec,
});
export type SweepWindow = z.infer<typeof SweepWindow>;

/** com.auratrade.backtest.StrategySweepService.Row */
export const SweepRow = z.object({
  atrMultiple: z.number(),
  maxHold: z.number(),
  regimeFilter: z.boolean(),
  inSample: SweepWindow,
  outOfSample: SweepWindow,
});
export type SweepRow = z.infer<typeof SweepRow>;

/** com.auratrade.backtest.StrategySweepService.Report */
export const SweepReport = z.object({
  screen: z.string(),
  from: isoDate,
  split: isoDate,
  to: isoDate,
  fixedSettings: z.string(),
  chosen: SweepRow,
  combinations: z.number(),
  combinationsBeatingBenchmarkOutOfSample: z.number(),
  seconds: z.number(),
  rowsByInSampleSharpe: z.array(SweepRow),
  caveats: z.array(z.string()),
});
export type SweepReport = z.infer<typeof SweepReport>;
