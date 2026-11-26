import { describe, expect, it } from "vitest";
import { PAPER_VIEW, PROPOSED } from "@/test/fixtures";
import { byFillDate, decisionDeadline, riskToStop, summarise, timeLeft } from "./paper";

describe("paper trading helpers", () => {
  it("closes decisions at 09:15 IST on the fill date", () => {
    const d = decisionDeadline("2026-11-27");
    expect(d.toISOString()).toBe("2026-11-27T03:45:00.000Z");
    expect(timeLeft(d, new Date("2026-11-26T11:30:00Z"))).toBe("16 h 15 min left");
    expect(timeLeft(d, new Date("2026-11-27T03:40:30Z"))).toBe("5 min left");
    expect(timeLeft(d, d)).toBeNull();
  });

  it("groups proposals by fill date, soonest first, sells before buys", () => {
    const later = { ...PROPOSED[0]!, id: 99, fillDate: "2026-11-30" };
    const groups = byFillDate([later, ...PROPOSED]);
    expect(groups.map((g) => g.fillDate)).toEqual(["2026-11-27", "2026-11-30"]);
    expect(groups[0]?.orders.map((o) => o.side)).toEqual(["SELL", "BUY"]);
  });

  it("summarises the account from the newest close", () => {
    const s = summarise(PAPER_VIEW);
    expect(s.equity).toBe(1012345.5);
    expect(s.returnPct).toBeCloseTo(1.23455, 5);
    expect(s.invested).toBe(200000);
    expect(summarise({ ...PAPER_VIEW, equity: [] })).toMatchObject({ equity: 812345.5, asOf: null });
    expect(riskToStop(PAPER_VIEW.positions[0]!)).toBe(840);
  });
});
