import { describe, expect, it } from "vitest";
import { Resolution } from "@/api/schemas";
import { NOW, run, RUNS } from "@/test/fixtures";
import { budgetStage, ringFill } from "./budget";
import { calendarDaysBehind, rangeDates, summarise, toChartBars } from "./candles";
import { formatDuration, formatInt, formatPrice, formatSigned } from "./format";
import { trace } from "./resolution";
import { addDays, daysBetween, istDate, istTime } from "./time";
import { describe as describeRun, health, latestByJob, layoutRuns, rejectedRows } from "./timeline";

describe("time in IST", () => {
  it("shows UTC instants in Asia/Kolkata", () => {
    expect(istTime(new Date("2026-11-26T11:30:00Z"))).toBe("17:00:00");
  });
  it("rolls the date over at IST midnight, not UTC midnight", () => {
    expect(istDate(new Date("2026-11-26T18:29:59Z"))).toBe("2026-11-26");
    expect(istDate(new Date("2026-11-26T18:30:00Z"))).toBe("2026-11-27");
  });
  it("does calendar arithmetic across month and year ends", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2024-03-01", -1)).toBe("2024-02-29");
    expect(daysBetween("2026-11-20", "2026-11-26")).toBe(6);
  });
});

describe("formatting", () => {
  it("uses Indian digit grouping", () => {
    expect(formatInt(1_084_210)).toBe("10,84,210");
    expect(formatPrice(2039.5)).toBe("₹2,039.50");
  });
  it("formats signed percentages and durations", () => {
    expect(formatSigned(1.234)).toBe("+1.23%");
    expect(formatSigned(-0.5)).toBe("−0.50%");
    expect(formatDuration(850)).toBe("850 ms");
    expect(formatDuration(9_800)).toBe("9.8 s");
    expect(formatDuration(125_000)).toBe("2 min 05 s");
  });
});

describe("resolution", () => {
  it("tells the three untagged backend shapes apart", () => {
    const share = { instrumentId: 1, tradingSymbol: "ACME", name: "Acme", series: "EQ" };
    expect(Resolution.parse({ share, kind: "FUZZY", score: 0.8 }).type).toBe("RESOLVED");
    expect(Resolution.parse({ candidates: [share] }).type).toBe("AMBIGUOUS");
    expect(Resolution.parse({ reason: "no match" }).type).toBe("UNRESOLVED");
    expect(() => Resolution.parse({ something: "else" })).toThrow();
  });
  it("marks earlier stages missed and later stages skipped", () => {
    const r = Resolution.parse({
      share: { instrumentId: 1, tradingSymbol: "ACME", name: "Acme", series: "EQ" },
      kind: "NAME",
      score: 1,
    });
    expect(trace(r).map((s) => s.state)).toEqual(["miss", "miss", "hit", "skip"]);
  });
  it("does not invent a trace for ambiguous names", () => {
    expect(trace(Resolution.parse({ candidates: [] })).every((s) => s.state === "unknown")).toBe(true);
  });
});

describe("job timeline", () => {
  it("puts known jobs first and places runs inside the 24 h window", () => {
    const old = run({
      id: 9,
      jobName: "eod-candles",
      startedAt: "2026-11-24T10:00:00Z",
      finishedAt: "2026-11-24T10:01:00Z",
    });
    const extra = run({ id: 10, jobName: "backfill", startedAt: "2026-11-26T09:00:00Z" });
    const { lanes, bars } = layoutRuns([...RUNS, old, extra], NOW, ["instrument-master", "eod-candles"]);
    expect(lanes).toEqual(["instrument-master", "eod-candles", "backfill"]);
    expect(bars.map((b) => b.id).sort()).toEqual([1, 10, 2]);
    for (const b of bars) {
      expect(b.x0).toBeGreaterThanOrEqual(0);
      expect(b.x1).toBeLessThanOrEqual(1);
    }
  });
  it("extends a running job to now", () => {
    const live = run({
      id: 3,
      jobName: "eod-candles",
      status: "RUNNING",
      startedAt: "2026-11-26T11:00:00Z",
      finishedAt: null,
    });
    expect(layoutRuns([live], NOW, []).bars[0]?.x1).toBe(1);
  });
  it("finds the latest run per job and maps its health", () => {
    const later = run({ id: 4, jobName: "eod-candles", status: "FAILED", startedAt: "2026-11-26T11:20:00Z" });
    const latest = latestByJob([...RUNS, later]);
    expect(latest.get("eod-candles")?.id).toBe(4);
    expect(health(latest.get("eod-candles"))).toBe("fail");
    expect(health(undefined)).toBe("unknown");
  });
  it("describes a run in one sentence", () => {
    expect(describeRun(RUNS[1]!)).toBe(
      "partly done (startup catch-up): 0 ok, 50 failed. Upstox token not set",
    );
  });
  it("reads rejected instrument rows from the run detail", () => {
    const im = run({
      id: 5,
      jobName: "instrument-master",
      itemsOk: 10049,
      itemsFailed: 0,
      detail:
        "UPSTOX: 10049 rows, 0 added, 0 not in file, 0 renamed, 1 rejected [X (NSE_EQ|IN0000000000): invalid ISIN]",
    });
    expect(rejectedRows(im)).toBe(1);
    expect(rejectedRows(run({ id: 6, jobName: "instrument-master", itemsFailed: 2, detail: null }))).toBe(2);
  });
});

describe("budget", () => {
  it("follows the ADR 0006 thresholds", () => {
    expect(budgetStage(59.9)).toBe("normal");
    expect(budgetStage(60)).toBe("downgraded");
    expect(budgetStage(75)).toBe("at-floor");
    expect(budgetStage(85)).toBe("non-critical-stopped");
    expect(budgetStage(100)).toBe("stopped");
  });
  it("keeps a visible sliver for small spend and caps at a full ring", () => {
    expect(ringFill(0)).toBe(0);
    expect(ringFill(0.2)).toBe(0.015);
    expect(ringFill(140)).toBe(1);
  });
});

describe("candles", () => {
  it("uses the IST trading date of each bar, oldest first", () => {
    const bars = toChartBars([
      { ts: "2026-11-24T18:30:00Z", open: 1, high: 2, low: 0.5, close: 1.5, volume: 10 },
      { ts: "2026-11-23T18:30:00Z", open: 1, high: 2, low: 0.5, close: 1, volume: 10 },
    ]);
    expect(bars.map((b) => b.time)).toEqual(["2026-11-24", "2026-11-25"]);
    expect(summarise(bars)?.changePct).toBeCloseTo(50);
    expect(summarise([])).toBeNull();
  });
  it("computes ranges and calendar staleness in IST", () => {
    expect(rangeDates("1M", NOW)).toEqual({ from: "2026-10-27", to: "2026-11-26" });
    expect(calendarDaysBehind("2026-11-25", NOW)).toBe(1);
  });
});
