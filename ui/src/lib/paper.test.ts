import { describe, expect, it } from "vitest";
import { PAPER_VIEW, PROPOSED } from "@/test/fixtures";
import {
  approvalEdits,
  byFillDate,
  decisionDeadline,
  estimate,
  riskToStop,
  summarise,
  timeLeft,
} from "./paper";

describe("paper trading helpers", () => {
  it("closes decisions at 09:15 IST on the fill date", () => {
    const d = decisionDeadline({ decideBy: "2026-11-27T03:45:00Z" });
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

  it("estimates an order at its reference price for the traded quantity", () => {
    const acme = PROPOSED[0]!;
    expect(estimate(acme)).toEqual({ price: 450, quantity: 120, value: 54000, riskToStop: 4740 });
    expect(estimate({ ...acme, approvedQuantity: 100 })?.value).toBe(45000);
    expect(estimate(acme, 10)?.riskToStop).toBeCloseTo(395, 6);
    expect(estimate({ ...acme, referencePrice: null })).toBeNull();
  });

  it("checks approval edits like the backend: fewer shares only, positive limit, sells fixed", () => {
    const buy = PROPOSED[0]!;
    const sell = PROPOSED[1]!;
    expect(approvalEdits(buy, "120", "")).toEqual({});
    expect(approvalEdits(buy, "100", "455.5")).toEqual({ quantity: 100, limitPrice: 455.5 });
    expect(approvalEdits(buy, "121", "")).toEqual({
      error: "Quantity must be a whole number from 1 to 120.",
    });
    expect(approvalEdits(buy, "1.5", "")).toHaveProperty("error");
    expect(approvalEdits(buy, "120", "0")).toEqual({ error: "Limit price must be a positive number." });
    expect(approvalEdits(sell, "5", "97")).toEqual({ limitPrice: 97 });
  });
});
