import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NOW, PAPER_VIEW, run, RUNS } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

const at = (iso: string) => {
  vi.setSystemTime(new Date(iso));
};
const tile = (name: string) => screen.findByRole("region", { name });

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"], now: NOW }); // 17:00 IST on 26 Nov; deadline 09:15 IST on 27 Nov
});
afterEach(() => {
  vi.useRealTimers();
});

describe("today strip: paper decisions", () => {
  it("waiting: count and countdown in IST from decideBy, linking to the approval queue", async () => {
    renderApp("/");
    const link = await screen.findByRole("link", { name: "2 paper orders waiting for rs-paper" });
    expect(link).toHaveAttribute("href", "/approvals?account=1");
    expect(link).toHaveTextContent("decide by 27 Nov 09:15 IST · 16 h 15 min left");
  });

  it("follows decideBy rather than a fixed 09:15", async () => {
    const at10 = PAPER_VIEW.proposed.map((o) => ({ ...o, decideBy: "2026-11-27T04:30:00Z" }));
    server.use(
      http.get("*/api/v1/paper/accounts/:id", () => HttpResponse.json({ ...PAPER_VIEW, proposed: at10 })),
    );
    renderApp("/");
    expect(await screen.findByRole("link", { name: /2 paper orders waiting/ })).toHaveTextContent(
      "decide by 27 Nov 10:00 IST · 17 h 0 min left",
    );
  });

  it("soon: under two hours left, just before 09:15 IST", async () => {
    at("2026-11-27T02:30:00Z"); // 08:00 IST
    renderApp("/");
    expect(await screen.findByRole("link", { name: /2 paper orders waiting/ })).toHaveTextContent(
      "1 h 15 min left",
    );
  });

  it("deadline passed: just after 09:15 IST the orders are flagged to expire", async () => {
    at("2026-11-27T03:46:00Z"); // 09:16 IST
    renderApp("/");
    expect(await screen.findByRole("link", { name: /2 paper orders waiting/ })).toHaveTextContent(
      "deadline passed at 09:15 IST · not decided, they will expire",
    );
  });

  it("none: says no decisions wait, and reports orders that expired undecided", async () => {
    at("2026-11-27T06:00:00Z");
    server.use(
      http.get("*/api/v1/paper/accounts/:id", () => HttpResponse.json({ ...PAPER_VIEW, proposed: [] })),
      http.get("*/api/v1/paper/accounts/:id/orders", () =>
        HttpResponse.json(PAPER_VIEW.proposed.map((o) => ({ ...o, status: "EXPIRED" }))),
      ),
    );
    renderApp("/");
    const link = await screen.findByRole("link", { name: "No decisions waiting for rs-paper" });
    expect(await within(link).findByText("2 expired undecided in the last 24 h")).toBeInTheDocument();
  });

  it("fails closed: an API error reads unavailable, not zero", async () => {
    server.use(
      http.get("*/api/v1/paper/accounts", () => HttpResponse.json({ detail: "boom" }, { status: 500 })),
    );
    renderApp("/");
    const t = await tile("Paper decisions");
    expect(await within(t).findByRole("alert")).toHaveTextContent("Unavailable");
    expect(t).not.toHaveTextContent(/No decisions|\b0\b/);
  });
});

describe("today strip: evening candles and job problems", () => {
  it("shows the last completed eod-candles session, status and coverage, linking to the timeline", async () => {
    renderApp("/");
    const eod = await screen.findByRole("link", { name: "Last evening candle run" });
    expect(eod).toHaveAttribute("href", "#job-timeline");
    expect(eod).toHaveTextContent("2026-11-26");
    expect(eod).toHaveTextContent("partial");
    expect(eod).toHaveTextContent("0.0% loaded");
    expect(eod).toHaveTextContent("Upstox token not set");
  });

  it("says when no eod-candles run has finished yet", async () => {
    server.use(http.get("*/dev/jobs/runs", () => HttpResponse.json([RUNS[0]])));
    renderApp("/");
    const t = await tile("Evening candles");
    expect(await within(t).findByText("No completed eod-candles run yet.")).toBeInTheDocument();
  });

  it("lists failed, partial and stuck runs since each job's last success", async () => {
    server.use(
      http.get("*/dev/jobs/runs", () =>
        HttpResponse.json([
          ...RUNS,
          run({
            id: 7,
            jobName: "eod-candles",
            status: "FAILED",
            detail: "benchmark NIFTY has no candle",
            startedAt: "2026-11-26T10:00:00Z",
          }),
          run({
            id: 8,
            jobName: "backfill",
            status: "RUNNING",
            startedAt: "2026-11-26T06:00:00Z",
            finishedAt: null,
          }),
        ]),
      ),
    );
    renderApp("/");
    const list = await screen.findByRole("list", { name: "Job problems" });
    expect(within(list).getByRole("link", { name: "eod-candles run 7 failed" })).toHaveTextContent(
      "benchmark NIFTY has no candle",
    );
    expect(within(list).getByRole("link", { name: "eod-candles run 2 partial" })).toBeInTheDocument();
    expect(within(list).getByRole("link", { name: "backfill run 8 stuck" })).toHaveTextContent(
      "probably abandoned",
    );
  });

  it("says No problems when every job's latest outcome is a success", async () => {
    server.use(
      http.get("*/dev/jobs/runs", () =>
        HttpResponse.json([
          run({ id: 7, jobName: "eod-candles", status: "FAILED", startedAt: "2026-11-26T09:00:00Z" }),
          run({ id: 9, jobName: "eod-candles", status: "SUCCEEDED", startedAt: "2026-11-26T10:00:00Z" }),
        ]),
      ),
    );
    renderApp("/");
    expect(await within(await tile("Job problems")).findByText("No problems")).toBeInTheDocument();
  });

  it("fails closed: a job-runs error makes both job tiles read unavailable", async () => {
    server.use(http.get("*/dev/jobs/runs", () => HttpResponse.json({ detail: "down" }, { status: 503 })));
    renderApp("/");
    for (const name of ["Evening candles", "Job problems"]) {
      const t = await tile(name);
      expect(await within(t).findByRole("alert")).toHaveTextContent("Unavailable");
      expect(t).not.toHaveTextContent("No problems");
    }
  });
});
