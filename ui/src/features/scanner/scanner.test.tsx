import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

const CANDIDATE = {
  instrumentId: 7,
  symbol: "ACME",
  name: "Acme Ltd",
  bestScore: 75.3,
  combinedScore: 44.4,
  passedStrategies: ["momentum-breakout@1.0.0"],
  evaluations: [
    {
      strategy: "momentum-breakout@1.0.0",
      status: "PASSED",
      score: 75.3,
      explanation: "Closed above the 20-session high",
      conditions: [
        {
          id: "volume_ratio",
          description: "Volume against the average",
          required: true,
          outcome: "PASS",
          value: 2,
          threshold: ">= 1.5",
        },
        {
          id: "outperforms_nifty_3m",
          description: "Three-month return ahead of Nifty 50 (preferred)",
          required: false,
          outcome: "UNKNOWN",
          value: null,
          threshold: "> 0",
        },
      ],
      plan: {
        entryType: "BUY_STOP",
        trigger: 113.01,
        maxPrice: 115.27,
        validSessions: 3,
        stop: 105,
        stopReason: "lower of the breakout level and the swing low",
        riskPerShare: 8.01,
        targets: [{ rMultiple: 2, fraction: 0.5, price: 129.03 }],
        maxHoldSessions: 15,
      },
    },
  ],
};

const NEAR = {
  instrumentId: 9,
  symbol: "NEARCO",
  name: "Nearco Ltd",
  bestScore: 0,
  combinedScore: 0,
  passedStrategies: [],
  evaluations: [
    {
      strategy: "momentum-breakout@1.0.0",
      status: "PARTIAL",
      score: 0,
      explanation: "one condition short",
      conditions: [
        {
          id: "rsi_in_range",
          description: "RSI in range",
          required: true,
          outcome: "FAIL",
          value: 52,
          threshold: "55 to 70",
        },
      ],
      plan: null,
    },
  ],
};

function view(approved: string[], shortlist: unknown[], almost: unknown[]) {
  return {
    asOf: "2026-11-27",
    source: "UPSTOX",
    approvedStrategies: approved,
    regime: { regime: "SIDEWAYS", score: 0.5, evidence: "NIFTY close 100" },
    scoringProfile: {
      id: "balanced-swing",
      name: "Balanced Swing",
      policy: "AUTO_NORMALIZED",
      weights: {
        "momentum-breakout": 25,
        "pullback-20ema": 20,
        "relative-strength": 20,
        "high-52w-momentum": 15,
      },
    },
    evaluated: 1496,
    passing: shortlist.length,
    skipped: { ILLIQUID: 826, SHORT_HISTORY: 94 },
    shortlist,
    almostQualified: almost,
  };
}

afterEach(() => {
  server.resetHandlers();
});

describe("stock scanner", () => {
  it("says the shortlist is empty by design when no strategy has passed its gate", async () => {
    server.use(http.get("*/dev/selection", () => HttpResponse.json(view([], [], [NEAR]))));
    renderApp("/scanner");
    expect(await screen.findByText(/No strategy has passed its gate yet/)).toBeInTheDocument();
    expect(screen.getByLabelText("approved strategies")).toHaveTextContent("none yet");
    expect(
      screen.getByText("No strategy has passed its gate, so the shortlist is empty by design."),
    ).toBeInTheDocument();
    expect(screen.getByText("NEARCO")).toBeInTheDocument();
  });

  it("lists approved passing shares, and shows each strategy's conditions and plan on selection", async () => {
    server.use(
      http.get("*/dev/selection", () => HttpResponse.json(view(["momentum-breakout"], [CANDIDATE], []))),
    );
    const user = userEvent.setup();
    renderApp("/scanner");
    const list = await screen.findByRole("list", { name: "candidates" });
    expect(within(list).getByText("ACME")).toBeInTheDocument();
    expect(screen.getByLabelText("approved strategies")).toHaveTextContent("momentum-breakout");
    expect(screen.getByLabelText("market regime")).toHaveTextContent("sideways · NIFTY close 100");

    await user.click(within(list).getByRole("button", { name: /ACME/ }));
    const detail = screen.getByRole("region", { name: "ACME · every strategy" });
    const strategy = within(detail).getByRole("region", { name: "strategy momentum-breakout@1.0.0" });
    expect(within(strategy).getByText("passed")).toBeInTheDocument();
    expect(within(strategy).getByText(/Volume against the average/)).toBeInTheDocument();
    expect(within(strategy).getByText(/three-month return ahead of nifty 50/i)).toBeInTheDocument();
    expect(within(strategy).getByText("BUY STOP", { exact: false })).toBeInTheDocument();
  });

  it("shows the reasons shares were not screened", async () => {
    server.use(http.get("*/dev/selection", () => HttpResponse.json(view([], [], []))));
    renderApp("/scanner");
    expect(await screen.findByText(/826 illiquid, 94 short history/)).toBeInTheDocument();
  });
});
