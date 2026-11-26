/**
 * Every backend call the UI makes, in one place. Today these hit the dev-profile endpoints; when the
 * backend adds `/api/v1` (ADR 0019) only this file changes.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getJson } from "./client";
import {
  Candles,
  Health,
  IngestionResult,
  JobRuns,
  JobTriggered,
  LlmResult,
  Resolution,
  SeriesCounts,
  SpendReport,
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
