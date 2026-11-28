import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import type { CalendarCoverage, Freshness } from "@/api/schemas";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

const FRESHNESS: Freshness = {
  asOf: "2026-11-26T11:30:00Z",
  expectedSession: "2026-11-26",
  eodCutoff: "19:00",
  source: "upstox",
  latestSession: "2026-11-25",
  sessionsBehind: 1,
  shares: {
    total: 3000,
    current: 2700,
    behind: { "1": 150, "2-5": 100, "6+": 30 },
    noData: 20,
    mostBehind: [{ symbol: "OLDCO", latestSession: "2026-11-12", sessionsBehind: 9 }],
  },
  benchmarks: [{ symbol: "NIFTY", latestSession: "2026-11-25", sessionsBehind: 1 }],
  otherSources: [{ source: "kite", latestSession: null }],
};

const COVERAGE: CalendarCoverage = {
  exchange: "NSE",
  timezone: "Asia/Kolkata",
  coveredYears: [2025, 2026],
  firstDate: "2025-01-01",
  lastDate: "2026-12-31",
  today: "2026-11-26",
  todayCovered: true,
  todayIsTradingDay: true,
  lastClosedSession: "2026-11-25",
  nextSession: "2026-11-27",
  nextYearCovered: false,
  coverageWarning: "2027 is not covered; the trading calendar will refuse 2027 dates",
  years: [
    {
      year: 2026,
      tradingDays: 245,
      holidays: [{ date: "2026-10-02", name: "Gandhi Jayanti" }],
      specialSessions: [{ date: "2026-11-01", name: "Muhurat trading" }],
    },
  ],
};

function serve(freshness: Freshness | number, coverage: CalendarCoverage) {
  server.use(
    http.get("*/api/v1/marketdata/freshness", () =>
      typeof freshness === "number"
        ? HttpResponse.json({ title: "unavailable" }, { status: freshness })
        : HttpResponse.json(freshness),
    ),
    http.get("*/api/v1/calendar/coverage", () => HttpResponse.json(coverage)),
  );
}

afterEach(() => {
  server.resetHandlers();
});

describe("data calendar", () => {
  it("shows freshness in trading sessions, with the expected session and the lag buckets", async () => {
    serve(FRESHNESS, COVERAGE);
    renderApp("/calendar");
    const panel = await screen.findByRole("region", { name: "Data freshness" });
    expect(await within(panel).findByLabelText("expected session")).toHaveTextContent("2026-11-26");
    expect(within(panel).getByLabelText("latest session")).toHaveTextContent("2026-11-25 · 1 session behind");
    expect(within(panel).getByLabelText("share freshness")).toHaveTextContent(
      "2700 current · 150 one behind · 100 two to five · 30 six or more · 20 with no data",
    );
    expect(within(panel).getByText("OLDCO")).toBeInTheDocument();
    expect(within(panel).getByText("9 sessions behind")).toBeInTheDocument();
    expect(within(panel).getByText("kite: no data")).toBeInTheDocument();
  });

  it("shows coverage, the warning for next year, and per-year holidays and special sessions", async () => {
    serve(FRESHNESS, COVERAGE);
    renderApp("/calendar");
    const panel = await screen.findByRole("region", { name: "Trading calendar" });
    expect(await within(panel).findByRole("alert")).toHaveTextContent("2027 is not covered");
    expect(within(panel).getByText("2025, 2026")).toBeInTheDocument();
    expect(within(panel).getByText("2026-11-27")).toBeInTheDocument();
    expect(within(panel).getByText("245 trading days")).toBeInTheDocument();
    expect(within(panel).getByText("Gandhi Jayanti")).toBeInTheDocument();
    expect(within(panel).getByText("Muhurat trading")).toBeInTheDocument();
  });

  it("does not show a guessed freshness when the API fails", async () => {
    serve(503, COVERAGE);
    renderApp("/calendar");
    await screen.findByRole("region", { name: "Trading calendar" });
    expect(screen.queryByLabelText("latest session")).not.toBeInTheDocument();
  });
});
