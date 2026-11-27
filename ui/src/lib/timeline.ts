import type { JobRun } from "@/api/schemas";

export type Bar = {
  id: number;
  lane: number;
  /** Start and end as fractions of the window, 0 (left) to 1 (now). */
  x0: number;
  x1: number;
  status: JobRun["status"];
  run: JobRun;
};
export type Layout = { lanes: string[]; bars: Bar[]; windowStart: Date; now: Date };

/**
 * Places job runs on lanes over the last `hours` hours. Known jobs keep a fixed order; others follow
 * alphabetically. Runs still RUNNING extend to now. Runs outside the window are left out.
 */
export function layoutRuns(runs: JobRun[], now: Date, known: readonly string[], hours = 24): Layout {
  const span = hours * 3_600_000;
  const start = now.getTime() - span;
  const others = [...new Set(runs.map((r) => r.jobName))].filter((n) => !known.includes(n)).sort();
  const lanes = [...known, ...others];
  const bars: Bar[] = [];
  for (const run of runs) {
    const s = Date.parse(run.startedAt);
    const e = run.finishedAt ? Date.parse(run.finishedAt) : now.getTime();
    if (e < start || s > now.getTime()) continue;
    const x0 = Math.max(0, (s - start) / span);
    const x1 = Math.min(1, (Math.max(e, s) - start) / span);
    bars.push({ id: run.id, lane: lanes.indexOf(run.jobName), x0, x1, status: run.status, run });
  }
  return { lanes, bars, windowStart: new Date(start), now };
}

/** The latest run of each job, by start time. */
export function latestByJob(runs: JobRun[]): Map<string, JobRun> {
  const out = new Map<string, JobRun>();
  for (const r of runs) {
    const prev = out.get(r.jobName);
    if (!prev || Date.parse(r.startedAt) > Date.parse(prev.startedAt)) out.set(r.jobName, r);
  }
  return out;
}

export type Health = "ok" | "warn" | "fail" | "running" | "unknown";
export function health(run: JobRun | undefined): Health {
  if (!run) return "unknown";
  switch (run.status) {
    case "SUCCEEDED":
      return "ok";
    case "PARTIAL":
      return "warn";
    case "FAILED":
      return "fail";
    case "RUNNING":
      return "running";
  }
}

/** One plain sentence per run for the system narrative. */
export function describe(run: JobRun): string {
  const counts = `${run.itemsOk} ok${run.itemsFailed ? `, ${run.itemsFailed} failed` : ""}`;
  const verb = { RUNNING: "running", SUCCEEDED: "succeeded", PARTIAL: "partly done", FAILED: "failed" }[
    run.status
  ];
  const by = run.trigger === "MANUAL" ? " (by hand)" : run.trigger === "STARTUP" ? " (startup catch-up)" : "";
  return `${verb}${by}: ${counts}${run.detail ? `. ${run.detail}` : ""}`;
}

/**
 * Rows the instrument-master run rejected. The job reports them only in its detail ("... 1 rejected
 * [...]"), not as failed items, because a few bad rows must not make the run PARTIAL.
 */
export function rejectedRows(run: JobRun): number {
  const m = /(\d[\d,]*) rejected/.exec(run.detail ?? "");
  const count = m?.[1];
  return count ? Number(count.replaceAll(",", "")) : run.itemsFailed;
}
