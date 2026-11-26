import { describe, expect, it } from "vitest";
import { PORTFOLIO, SWEEP } from "@/test/fixtures";
import {
  beats,
  EXIT_LABEL,
  exitRows,
  isChosen,
  label,
  metricLabel,
  metricText,
  screenTitle,
  sweepGrid,
  yearRows,
} from "./playbook";

describe("playbook display helpers", () => {
  it("names known screens and falls back to the id", () => {
    expect(screenTitle("pullback-to-50dma")).toBe("Pullback to the 50-day average");
    expect(screenTitle("new-screen")).toBe("new-screen");
    expect(label(EXIT_LABEL, "STOP_GAP")).toBe("Gapped through stop");
    expect(label(EXIT_LABEL, "SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("orders calendar years oldest first and pairs them with the benchmark", () => {
    expect(yearRows(PORTFOLIO.summary)).toEqual([
      { year: "2024", strategy: 30.1, benchmark: 8.8 },
      { year: "2025", strategy: -4.2, benchmark: 2.0 },
    ]);
  });

  it("lists exits most frequent first", () => {
    expect(exitRows(PORTFOLIO.summary).map((e) => e.reason)).toEqual(["STOP", "TIME", "END"]);
  });

  it("arranges sweep rows as a stop by hold grid and finds the chosen one", () => {
    const g = sweepGrid(SWEEP);
    expect(g.stops).toEqual([2, 3]);
    expect(g.holds).toEqual([60, 120]);
    const chosen = g.cell(3, 120, true);
    expect(chosen && isChosen(SWEEP, chosen)).toBe(true);
    expect(g.cell(3, 120, false)).toBeUndefined();
    expect(chosen && beats(chosen.outOfSample)).toBe(true);
    expect(beats({ ...SWEEP.chosen.outOfSample, cagrPct: null })).toBeNull();
  });
});

describe("screen metrics", () => {
  it("labels every metric the screens emit and formats by kind", () => {
    expect(metricLabel("volumeRatio")).toEqual({ text: "Volume vs 50-day avg", kind: "times" });
    expect(metricText("times", 10.8962)).toBe("10.9×");
    expect(metricText("pct", 10.123)).toBe("10.12%");
    expect(metricText("number", 42.06)).toBe("42.1");
    expect(metricLabel("somethingPct").kind).toBe("pct");
  });
});
