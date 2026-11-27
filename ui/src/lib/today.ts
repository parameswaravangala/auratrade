/**
 * What needs attention today, for the Command center's Today strip (B-040). Pure functions over data the
 * backend already returns; nothing here decides anything.
 */
import type { JobRun, PaperOrder } from "@/api/schemas";

/** Proposed orders and the earliest deadline among them (the API's decideBy); null when nothing waits. */
export function waiting(orders: PaperOrder[]): { count: number; decideBy: Date } | null {
  const proposed = orders.filter((o) => o.status === "PROPOSED");
  if (proposed.length === 0) return null;
  return {
    count: proposed.length,
    decideBy: new Date(Math.min(...proposed.map((o) => Date.parse(o.decideBy)))),
  };
}

/** Orders that expired undecided with a deadline in the last 24 hours. */
export function recentlyExpired(orders: PaperOrder[], now: Date): number {
  const dayAgo = now.getTime() - 24 * 3_600_000;
  return orders.filter(
    (o) =>
      o.status === "EXPIRED" && Date.parse(o.decideBy) >= dayAgo && Date.parse(o.decideBy) <= now.getTime(),
  ).length;
}

/** How urgent a deadline is: passed, under two hours (soon) or later. */
export function urgency(decideBy: Date, now: Date): "passed" | "soon" | "later" {
  const ms = decideBy.getTime() - now.getTime();
  if (ms <= 0) return "passed";
  return ms < 2 * 3_600_000 ? "soon" : "later";
}

/** The newest finished run of a job, or undefined. */
export function lastCompleted(runs: JobRun[], job: string): JobRun | undefined {
  let best: JobRun | undefined;
  for (const r of runs) {
    if (r.jobName !== job || r.finishedAt === null) continue;
    if (!best || Date.parse(r.startedAt) > Date.parse(best.startedAt)) best = r;
  }
  return best;
}

/** Share of items the run loaded, as a percentage; null when it attempted none. */
export function coveragePct(run: JobRun): number | null {
  const total = run.itemsOk + run.itemsFailed;
  return total === 0 ? null : (run.itemsOk / total) * 100;
}

/**
 * A run still RUNNING after this long is probably abandoned: its process died and only the job lease
 * (PT3H, auratrade.jobs.lease) would release it.
 */
export const ABANDONED_AFTER_MS = 3 * 3_600_000;

export type Problem = { run: JobRun; kind: "failed" | "partial" | "abandoned" };

/**
 * Runs that still need attention: for each job, the FAILED and PARTIAL runs since its last success,
 * and runs RUNNING for longer than the lease. Newest first. A failure followed by a success is history,
 * not a problem.
 */
export function problems(runs: JobRun[], now: Date): Problem[] {
  const lastSuccess = new Map<string, number>();
  for (const r of runs) {
    if (r.status !== "SUCCEEDED") continue;
    const t = Date.parse(r.startedAt);
    if (t > (lastSuccess.get(r.jobName) ?? -Infinity)) lastSuccess.set(r.jobName, t);
  }
  return runs
    .flatMap((run): Problem[] => {
      const started = Date.parse(run.startedAt);
      if (started <= (lastSuccess.get(run.jobName) ?? -Infinity)) return [];
      if (run.status === "FAILED") return [{ run, kind: "failed" }];
      if (run.status === "PARTIAL") return [{ run, kind: "partial" }];
      if (run.status === "RUNNING" && now.getTime() - started > ABANDONED_AFTER_MS) {
        return [{ run, kind: "abandoned" }];
      }
      return [];
    })
    .sort((a, b) => Date.parse(b.run.startedAt) - Date.parse(a.run.startedAt));
}
