import { describe, expect, it } from "vitest";
import { NOW, order, PROPOSED, run, RUNS } from "@/test/fixtures";
import { coveragePct, lastCompleted, problems, recentlyExpired, urgency, waiting } from "./today";

describe("today strip helpers", () => {
  it("counts proposed orders and takes the earliest decideBy from the API", () => {
    const early = order({ id: 30, symbol: "EARLY", decideBy: "2026-11-26T20:00:00Z" });
    const decided = order({ id: 31, symbol: "DONE", status: "APPROVED", decideBy: "2026-11-26T01:00:00Z" });
    expect(waiting([...PROPOSED, early, decided])).toEqual({
      count: 3,
      decideBy: new Date("2026-11-26T20:00:00Z"),
    });
    expect(waiting([decided])).toBeNull();
  });

  it("counts orders that expired undecided in the last 24 hours", () => {
    const exp = (id: number, decideBy: string) => order({ id, symbol: "X", status: "EXPIRED", decideBy });
    expect(
      recentlyExpired([exp(1, "2026-11-26T03:45:00Z"), exp(2, "2026-11-24T03:45:00Z"), ...PROPOSED], NOW),
    ).toBe(1);
  });

  it("grades a deadline as later, soon (under two hours) or passed", () => {
    const at = (iso: string) => urgency(new Date(iso), NOW);
    expect(at("2026-11-27T03:45:00Z")).toBe("later");
    expect(at("2026-11-26T13:00:00Z")).toBe("soon");
    expect(at("2026-11-26T11:30:00Z")).toBe("passed");
  });

  it("finds the newest finished run of a job and its coverage", () => {
    const running = run({
      id: 9,
      jobName: "eod-candles",
      status: "RUNNING",
      startedAt: "2026-11-26T11:20:00Z",
      finishedAt: null,
    });
    expect(lastCompleted([...RUNS, running], "eod-candles")?.id).toBe(2);
    expect(lastCompleted(RUNS, "backfill")).toBeUndefined();
    expect(coveragePct(run({ id: 1, jobName: "x", itemsOk: 1691, itemsFailed: 1095 }))).toBeCloseTo(60.7, 1);
    expect(coveragePct(run({ id: 1, jobName: "x", itemsOk: 0, itemsFailed: 0 }))).toBeNull();
  });

  it("lists failed, partial and stuck runs since each job's last success, newest first", () => {
    type S = "FAILED" | "PARTIAL" | "SUCCEEDED" | "RUNNING";
    const at = (id: number, jobName: string, status: S, startedAt: string) =>
      run({ id, jobName, status, startedAt, finishedAt: status === "RUNNING" ? null : startedAt });
    const runs = [
      at(1, "eod-candles", "FAILED", "2026-11-25T14:00:00Z"), // before the success: history
      at(2, "eod-candles", "SUCCEEDED", "2026-11-25T15:00:00Z"),
      at(3, "eod-candles", "PARTIAL", "2026-11-26T09:00:00Z"),
      at(4, "eod-candles", "FAILED", "2026-11-26T10:00:00Z"),
      at(5, "backfill", "RUNNING", "2026-11-26T06:00:00Z"), // 5.5 h: stuck
      at(6, "backfill", "RUNNING", "2026-11-26T11:00:00Z"), // 30 min: still running
    ];
    expect(problems(runs, NOW).map((p) => [p.run.id, p.kind])).toEqual([
      [4, "failed"],
      [3, "partial"],
      [5, "abandoned"],
    ]);
  });
});
