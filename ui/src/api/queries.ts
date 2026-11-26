/**
 * Every backend call the UI makes, in one place. Today these hit the dev-profile endpoints; when the
 * backend adds `/api/v1` (ADR 0019) only this file changes.
 */
import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { getJson } from "./client";
import {
  Candles,
  Health,
  IngestionResult,
  JobRuns,
  JobTriggered,
  LlmResult,
  PaperAccounts,
  PaperAccountView,
  PaperOrder,
  PaperOrders,
  PaperTrades,
  PortfolioReport,
  Resolution,
  ScreenInfos,
  ScreenResult,
  SeriesCounts,
  SpendReport,
  SweepReport,
} from "./schemas";

export const JOBS = ["instrument-master", "eod-candles"] as const;
export type JobName = (typeof JOBS)[number];

export const keys = {
  health: ["health"] as const,
  runs: ["jobs", "runs"] as const,
  series: ["instruments", "series"] as const,
  spend: ["llm", "spend"] as const,
  resolve: (name: string) => ["resolve", name] as const,
  candles: (symbol: string, from: string, to: string) => ["candles", symbol, from, to] as const,
  screens: ["screener", "screens"] as const,
  shortlist: (screen: string) => ["screener", "run", screen] as const,
  portfolio: (p: PortfolioParams) => ["backtest", "portfolio", p] as const,
  sweep: (p: SweepParams) => ["backtest", "sweep", p] as const,
  paper: ["paper"] as const,
  paperAccounts: ["paper", "accounts"] as const,
  paperAccount: (id: number) => ["paper", "account", id] as const,
  paperOrders: (id: number) => ["paper", "orders", id] as const,
  paperTrades: (id: number) => ["paper", "trades", id] as const,
};

export function useHealth() {
  return useQuery({
    queryKey: keys.health,
    queryFn: ({ signal }) => getJson("/actuator/health", Health, { signal }),
    refetchInterval: 15_000,
    retry: false,
  });
}

/** Job runs refresh every 5 s while any run is still RUNNING, otherwise every 30 s. */
export function useJobRuns(limit = 50) {
  return useQuery({
    queryKey: keys.runs,
    queryFn: ({ signal }) => getJson(`/dev/jobs/runs?limit=${limit}`, JobRuns, { signal }),
    refetchInterval: (q) => (q.state.data?.some((r) => r.status === "RUNNING") ? 5_000 : 30_000),
  });
}

export function useSeriesCounts() {
  return useQuery({
    queryKey: keys.series,
    queryFn: ({ signal }) => getJson("/dev/marketdata/instruments/series", SeriesCounts, { signal }),
    staleTime: 5 * 60_000,
  });
}

export function useSpend() {
  return useQuery({
    queryKey: keys.spend,
    queryFn: ({ signal }) => getJson("/dev/llm/spend", SpendReport, { signal }),
    refetchInterval: 60_000,
  });
}

export function useResolve(name: string) {
  const trimmed = name.trim();
  return useQuery({
    queryKey: keys.resolve(trimmed),
    queryFn: ({ signal }) =>
      getJson(`/dev/marketdata/resolve?name=${encodeURIComponent(trimmed)}`, Resolution, { signal }),
    enabled: trimmed.length > 0,
    staleTime: 60_000,
    retry: false,
  });
}

export function useCandles(symbol: string, from: string, to: string) {
  return useQuery({
    queryKey: keys.candles(symbol, from, to),
    queryFn: ({ signal }) =>
      getJson(
        `/dev/marketdata/candles?symbol=${encodeURIComponent(symbol)}&interval=DAY&from=${from}&to=${to}`,
        Candles,
        { signal },
      ),
    enabled: symbol.length > 0,
    retry: false,
  });
}

/** Dev only: asks the provider for candles, validates and stores them. */
export function useLoadCandles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { symbol: string; from: string; to: string }) =>
      getJson(
        `/dev/marketdata/candles/load?symbol=${encodeURIComponent(v.symbol)}&interval=DAY&from=${v.from}&to=${v.to}`,
        IngestionResult,
        { method: "POST" },
      ),
    onSettled: (_d, _e, v) => qc.invalidateQueries({ queryKey: ["candles", v.symbol] }),
  });
}

/** Dev only: runs whatever the job still owes. */
export function useRunJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (job: JobName) => getJson(`/dev/jobs/${job}/run`, JobTriggered, { method: "POST" }),
    onSettled: () => qc.invalidateQueries({ queryKey: keys.runs }),
  });
}

/** Read-only question through the LLM gateway. The answer is untrusted text. */
export function useAsk() {
  return useMutation({
    mutationFn: (prompt: string) =>
      getJson("/dev/llm/ask?agent=ADHOC_CHAT", LlmResult, {
        method: "POST",
        body: prompt,
        contentType: "text/plain",
      }),
  });
}

// ---- Playbook lab: screener and backtests (all computed in Java) ----

export function useScreens() {
  return useQuery({
    queryKey: keys.screens,
    queryFn: ({ signal }) => getJson("/dev/screener/screens", ScreenInfos, { signal }),
    staleTime: Infinity,
  });
}

/** Today's shortlist for one screen, on the newest stored data. Takes several seconds. */
export function useShortlist(screen: string | undefined, limit = 10) {
  return useQuery({
    queryKey: keys.shortlist(screen ?? ""),
    queryFn: ({ signal }) =>
      getJson(`/dev/screener/${encodeURIComponent(screen ?? "")}?limit=${limit}`, ScreenResult, { signal }),
    enabled: !!screen,
    staleTime: 10 * 60_000,
    retry: false,
  });
}

/** Settings for one portfolio simulation; names match the backend's request parameters. */
export type PortfolioParams = {
  screen: string;
  from: string;
  to: string;
  atrMultiple: number;
  maxHold: number;
  regime: boolean;
  riskPct: number;
  maxPositions: number;
  costs: boolean;
};

export type SweepParams = { screen: string; from: string; split: string; to: string };

const query = (p: Record<string, string | number | boolean>) =>
  new URLSearchParams(Object.entries(p).map(([k, v]) => [k, String(v)])).toString();

/**
 * Backtests take one to several minutes, so they run only when asked (`params` set) and the result
 * is kept for these exact settings. No abort signal: leaving the screen must not throw away a run.
 */
const LONG_RUN = { staleTime: Infinity, gcTime: 60 * 60_000, retry: false } as const;

export function usePortfolio(params: PortfolioParams | null) {
  return useQuery({
    queryKey: keys.portfolio(params ?? EMPTY_PORTFOLIO),
    queryFn: () => getJson(`/dev/backtest/portfolio?${query(params ?? EMPTY_PORTFOLIO)}`, PortfolioReport),
    enabled: params !== null,
    ...LONG_RUN,
  });
}

export function useSweep(params: SweepParams | null) {
  return useQuery({
    queryKey: keys.sweep(params ?? EMPTY_SWEEP),
    queryFn: () => getJson(`/dev/backtest/sweep?${query(params ?? EMPTY_SWEEP)}`, SweepReport),
    enabled: params !== null,
    ...LONG_RUN,
  });
}

/**
 * The settings of the newest backtest of `kind` for `screen` still in the cache, running or done, so
 * a result survives leaving the page and coming back (for example to open a share in the dossier).
 */
export function lastRun<K extends "portfolio" | "sweep">(
  qc: QueryClient,
  kind: K,
  screen: string,
): (K extends "portfolio" ? PortfolioParams : SweepParams) | null {
  type P = K extends "portfolio" ? PortfolioParams : SweepParams;
  let best: { at: number; params: P } | null = null;
  for (const q of qc.getQueryCache().findAll({ queryKey: ["backtest", kind] })) {
    const params = q.queryKey[2] as P | undefined;
    if (params?.screen !== screen) continue;
    const s = q.state;
    const at =
      s.fetchStatus === "fetching" ? Number.MAX_SAFE_INTEGER : Math.max(s.dataUpdatedAt, s.errorUpdatedAt);
    if (at > 0 && (!best || at > best.at)) best = { at, params };
  }
  return best?.params ?? null;
}

const EMPTY_PORTFOLIO: PortfolioParams = {
  screen: "",
  from: "",
  to: "",
  atrMultiple: 0,
  maxHold: 0,
  regime: false,
  riskPct: 0,
  maxPositions: 0,
  costs: true,
};
const EMPTY_SWEEP: SweepParams = { screen: "", from: "", split: "", to: "" };

// ---- Paper trading (ADR 0024). Code proposes, a person decides every order. ----

const PAPER = "/api/v1/paper";

export function usePaperAccounts() {
  return useQuery({
    queryKey: keys.paperAccounts,
    queryFn: ({ signal }) => getJson(`${PAPER}/accounts`, PaperAccounts, { signal }),
    staleTime: 60_000,
  });
}

/** One account with its positions, proposed orders and equity. Refreshes each minute. */
export function usePaperAccount(id: number | undefined) {
  return useQuery({
    queryKey: keys.paperAccount(id ?? 0),
    queryFn: ({ signal }) => getJson(`${PAPER}/accounts/${String(id)}`, PaperAccountView, { signal }),
    enabled: id !== undefined,
    refetchInterval: 60_000,
  });
}

/** The account's newest orders in every status, for the decision log. */
export function usePaperOrders(id: number | undefined, limit = 50) {
  return useQuery({
    queryKey: keys.paperOrders(id ?? 0),
    queryFn: ({ signal }) =>
      getJson(`${PAPER}/accounts/${String(id)}/orders?limit=${limit}`, PaperOrders, { signal }),
    enabled: id !== undefined,
    refetchInterval: 60_000,
  });
}

export function usePaperTrades(id: number | undefined) {
  return useQuery({
    queryKey: keys.paperTrades(id ?? 0),
    queryFn: ({ signal }) => getJson(`${PAPER}/accounts/${String(id)}/trades`, PaperTrades, { signal }),
    enabled: id !== undefined,
  });
}

export type Decision = { orderId: number; approve: boolean; by: string; note: string };

/**
 * Approves or rejects one PROPOSED order. The backend refuses after 09:15 IST on the fill date and
 * records who decided and why. Never retried: a decision is sent once, on a person's click.
 */
export function useDecide() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: Decision) =>
      getJson(`${PAPER}/orders/${String(d.orderId)}/${d.approve ? "approve" : "reject"}`, PaperOrder, {
        method: "POST",
        body: JSON.stringify({ by: d.by, note: d.note }),
        contentType: "application/json",
      }),
    retry: false,
    onSettled: () => qc.invalidateQueries({ queryKey: keys.paper }),
  });
}
