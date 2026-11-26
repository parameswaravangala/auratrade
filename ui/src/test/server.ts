import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { RUNS, SERIES, SHARE, SPEND } from "./fixtures";

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
  http.get("*/dev/marketdata/candles", () => HttpResponse.json([])),
];

export const server = setupServer(...handlers);
