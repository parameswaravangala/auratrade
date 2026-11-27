import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import {
  DECIDED,
  PAPER_ACCOUNT,
  PAPER_TRADES,
  PAPER_VIEW,
  PORTFOLIO,
  PROPOSED,
  RUNS,
  SCREENS,
  SERIES,
  SHARE,
  SHORTLIST,
  SPEND,
  SWEEP,
} from "./fixtures";

/** A fake backend for component tests (like WireMock). Tests override handlers with server.use(). */
export const handlers = [
  http.get("*/actuator/health", () => HttpResponse.json({ status: "UP" })),
  http.get("*/dev/jobs/runs", () => HttpResponse.json(RUNS)),
  http.get("*/dev/llm/spend", () => HttpResponse.json(SPEND)),
  http.get("*/dev/marketdata/instruments/series", () => HttpResponse.json(SERIES)),
  http.get("*/dev/marketdata/resolve", ({ request }) => {
    const name = new URL(request.url).searchParams.get("name") ?? "";
    if (/acme cookers/i.test(name)) return HttpResponse.json({ share: SHARE, kind: "NAME", score: 1.0 });
    if (/acme/i.test(name)) {
      return HttpResponse.json({
        candidates: [
          SHARE,
          { ...SHARE, instrumentId: 8, tradingSymbol: "ACMEFIN", name: "Acme Finance Limited" },
        ],
      });
    }
    return HttpResponse.json({ reason: `no NSE share matches '${name}'` });
  }),
  http.get("*/dev/marketdata/candles", ({ request }) =>
    new URL(request.url).searchParams.get("symbol") === "ACME" &&
    new URL(request.url).searchParams.get("from") === "2026-11-26"
      ? HttpResponse.json([
          { ts: "2026-11-25T18:30:00Z", open: 440, high: 455, low: 438, close: 450, volume: 1000 },
        ])
      : HttpResponse.json([]),
  ),
  http.get("*/dev/screener/screens", () => HttpResponse.json(SCREENS)),
  http.get("*/dev/screener/:name", ({ params }) =>
    HttpResponse.json({ ...SHORTLIST, screen: String(params.name) }),
  ),
  http.get("*/dev/backtest/portfolio", () => HttpResponse.json(PORTFOLIO)),
  http.get("*/dev/backtest/sweep", () => HttpResponse.json(SWEEP)),
  http.get("*/api/v1/paper/accounts", () => HttpResponse.json([PAPER_ACCOUNT])),
  http.get("*/api/v1/paper/accounts/:id", () => HttpResponse.json(PAPER_VIEW)),
  http.get("*/api/v1/paper/accounts/:id/orders", () => HttpResponse.json([...PROPOSED, ...DECIDED])),
  http.get("*/api/v1/paper/accounts/:id/trades", () => HttpResponse.json(PAPER_TRADES)),
];

export const server = setupServer(...handlers);
