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

// ---- Paper trading (ADR 0024), /api/v1/paper. Money arrives as JSON numbers; display only. ----

/** com.auratrade.paperbroker.PaperRepository.Account. riskPerTrade and maxPosition are fractions. */
export const PaperAccount = z.object({
  id: z.number(),
  name: z.string(),
  screen: z.string(),
  initialCapital: z.number(),
  cash: z.number(),
  maxPositions: z.number(),
  riskPerTrade: z.number(),
  maxPosition: z.number(),
  atrMultiple: z.number(),
  maxHoldSessions: z.number(),
  regimeFilter: z.boolean(),
  slippageBps: z.number(),
  goLiveCriteria: z.string().nullable(),
  startedOn: isoDate,
  active: z.boolean(),
});
export type PaperAccount = z.infer<typeof PaperAccount>;
export const PaperAccounts = z.array(PaperAccount);

export const ORDER_STATUSES = ["PROPOSED", "APPROVED", "REJECTED", "EXPIRED", "FILLED", "UNFILLED"] as const;

/** com.auratrade.paperbroker.PaperRepository.Order */
export const PaperOrder = z.object({
  id: z.number(),
  accountId: z.number(),
  instrumentId: z.number(),
  symbol: z.string(),
  side: z.enum(["BUY", "SELL"]),
  quantity: z.number(),
  reason: z.string(),
  signalDate: isoDate,
  fillDate: isoDate,
  stopPrice: dec,
  score: dec,
  detail: z.string().nullable(),
  status: z.enum(ORDER_STATUSES),
  decidedAt: instant.nullable(),
  decidedBy: z.string().nullable(),
  fillPrice: dec,
  fillCosts: dec,
  statusNote: z.string().nullable(),
  /** The signal-day close the order was sized from; the fill is at the next open, so it differs. */
  referencePrice: dec,
  /** Set when the person approved fewer shares than proposed; traded = approvedQuantity ?? quantity. */
  approvedQuantity: z.number().nullable(),
  /** BUY: highest acceptable fill; SELL: lowest. Beyond it the order is UNFILLED. */
  limitPrice: dec,
  /** 09:15 IST on the fill date; decisions after it are refused. */
  decideBy: instant,
});
export type PaperOrder = z.infer<typeof PaperOrder>;
export const PaperOrders = z.array(PaperOrder);

/** One result of POST /api/v1/paper/orders/decide. */
export const DecideOutcome = z.object({
  orderId: z.number(),
  ok: z.boolean(),
  order: PaperOrder.nullable(),
  error: z.string().nullable(),
});
export type DecideOutcome = z.infer<typeof DecideOutcome>;
export const DecideOutcomes = z.array(DecideOutcome);

/** com.auratrade.paperbroker.PaperRepository.Position */
export const PaperPosition = z.object({
  accountId: z.number(),
  instrumentId: z.number(),
  symbol: z.string(),
  quantity: z.number(),
  entryPrice: z.number(),
  entryDate: isoDate,
  entryCosts: z.number(),
  stopPrice: z.number(),
  sessionsHeld: z.number(),
});
export type PaperPosition = z.infer<typeof PaperPosition>;

/** com.auratrade.paperbroker.PaperRepository.EquityPoint, recorded at each session's close. */
export const PaperEquityPoint = z.object({
  date: isoDate,
  cash: z.number(),
  equity: z.number(),
  positions: z.number(),
});
export type PaperEquityPoint = z.infer<typeof PaperEquityPoint>;

/** PaperTradingController.AccountView */
export const PaperAccountView = z.object({
  account: PaperAccount,
  positions: z.array(PaperPosition),
  proposed: z.array(PaperOrder),
  equity: z.array(PaperEquityPoint),
});
export type PaperAccountView = z.infer<typeof PaperAccountView>;

/** com.auratrade.paperbroker.PaperRepository.Trade, a closed round trip in the journal. */
export const PaperTrade = z.object({
  id: z.number(),
  symbol: z.string(),
  quantity: z.number(),
  entryDate: isoDate,
  entryPrice: z.number(),
  exitDate: isoDate,
  exitPrice: z.number(),
  exitReason: z.string(),
  sessionsHeld: z.number(),
  costs: z.number(),
  netPnl: z.number(),
});
export type PaperTrade = z.infer<typeof PaperTrade>;
export const PaperTrades = z.array(PaperTrade);

/** com.auratrade.marketdata.DataFreshnessService.Lag: latestSession and sessionsBehind are null when unknown. */
export const FreshnessLag = z.object({
  symbol: z.string(),
  latestSession: isoDate.nullable(),
  sessionsBehind: z.number().int().nullable(),
});
export type FreshnessLag = z.infer<typeof FreshnessLag>;

/** com.auratrade.marketdata.DataFreshnessService.Freshness (GET /api/v1/marketdata/freshness) */
export const Freshness = z.object({
  asOf: instant,
  expectedSession: isoDate.nullable(),
  eodCutoff: z.string(),
  source: z.string(),
  latestSession: isoDate.nullable(),
  sessionsBehind: z.number().int().nullable(),
  shares: z.object({
    total: z.number().int(),
    current: z.number().int(),
    behind: z.record(z.string(), z.number().int()),
    noData: z.number().int(),
    mostBehind: z.array(FreshnessLag),
  }),
  benchmarks: z.array(FreshnessLag),
  otherSources: z.array(z.object({ source: z.string(), latestSession: isoDate.nullable() })),
});
export type Freshness = z.infer<typeof Freshness>;

/** com.auratrade.marketdata.TradingCalendar.NamedDay */
const NamedDay = z.object({ date: isoDate, name: z.string() });

/** com.auratrade.marketdata.CalendarCoverage (GET /api/v1/calendar/coverage) */
export const CalendarCoverage = z.object({
  exchange: z.string(),
  timezone: z.string(),
  coveredYears: z.array(z.number().int()),
  firstDate: isoDate.nullable(),
  lastDate: isoDate.nullable(),
  today: isoDate,
  todayCovered: z.boolean(),
  todayIsTradingDay: z.boolean().nullable(),
  lastClosedSession: isoDate.nullable(),
  nextSession: isoDate.nullable(),
  nextYearCovered: z.boolean(),
  coverageWarning: z.string().nullable(),
  years: z.array(
    z.object({
      year: z.number().int(),
      tradingDays: z.number().int(),
      holidays: z.array(NamedDay),
      specialSessions: z.array(NamedDay),
    }),
  ),
});
export type CalendarCoverage = z.infer<typeof CalendarCoverage>;

/** com.auratrade.selection.Selection.Result as returned by GET /dev/selection */
const SelectionCondition = z.object({
  id: z.string(),
  description: z.string(),
  required: z.boolean(),
  outcome: z.enum(["PASS", "FAIL", "UNKNOWN"]),
  value: z.number().nullable(),
  threshold: z.string(),
});
const SelectionTarget = z.object({ rMultiple: z.number(), fraction: z.number(), price: z.number() });
const SelectionPlan = z.object({
  entryType: z.enum(["BUY_STOP", "NEXT_OPEN"]),
  trigger: z.number(),
  maxPrice: z.number(),
  validSessions: z.number().int(),
  stop: z.number(),
  stopReason: z.string(),
  riskPerShare: z.number(),
  targets: z.array(SelectionTarget),
  maxHoldSessions: z.number().int(),
});
const SelectionEvaluation = z.object({
  strategy: z.string(),
  status: z.enum(["PASSED", "PARTIAL", "FAILED", "NOT_EVALUATED"]),
  score: z.number(),
  explanation: z.string(),
  conditions: z.array(SelectionCondition),
  plan: SelectionPlan.nullable(),
});
const SelectionCandidate = z.object({
  instrumentId: z.number(),
  symbol: z.string(),
  name: z.string(),
  bestScore: z.number(),
  passedStrategies: z.array(z.string()),
  evaluations: z.array(SelectionEvaluation),
});
export const SelectionView = z.object({
  asOf: isoDate,
  source: z.string(),
  approvedStrategies: z.array(z.string()),
  evaluated: z.number().int(),
  passing: z.number().int(),
  skipped: z.record(z.string(), z.number().int()),
  shortlist: z.array(SelectionCandidate),
  almostQualified: z.array(SelectionCandidate),
});
export type SelectionView = z.infer<typeof SelectionView>;
export type SelectionCandidate = z.infer<typeof SelectionCandidate>;
export type SelectionEvaluation = z.infer<typeof SelectionEvaluation>;

/** GateController View (GET /dev/strategies/{id}/gate) */
const GateMetrics = z.object({
  trades: z.number().int(),
  winRate: z.number(),
  expectancy: z.number(),
  profitFactor: z.number().nullable(),
  totalNet: z.number(),
});
export const GateView = z.object({
  strategy: z.string(),
  passed: z.boolean(),
  from: isoDate,
  oosStart: isoDate,
  to: isoDate,
  source: z.string(),
  benchmark: z.string(),
  settings: z.number().int(),
  candidateShares: z.number().int(),
  seconds: z.number(),
  chosenSetting: z.string(),
  checks: z.array(
    z.object({ name: z.string(), passed: z.boolean(), value: z.string(), threshold: z.string() }),
  ),
  outOfSample: GateMetrics,
  walkForward: GateMetrics,
  settingsBeatingNifty: z.number().int(),
  settingCount: z.number().int(),
  defaultMaxDrawdown: z.number(),
  caveats: z.array(z.string()),
});
export type GateView = z.infer<typeof GateView>;
