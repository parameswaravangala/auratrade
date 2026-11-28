import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

const REPORT = {
  strategy: "momentum-breakout@1.0.0",
  passed: false,
  from: "2019-01-01",
  oosStart: "2023-01-01",
  to: "2026-11-27",
  source: "UPSTOX",
  benchmark: "NIFTY",
  settings: 8,
  candidateShares: 897,
  seconds: 391,
  chosenSetting: "res20-vol2.0-hold10",
  checks: [
    { name: "Out-of-sample trades", passed: true, value: "486", threshold: ">= 100" },
    {
      name: "Out-of-sample expectancy after costs (INR per trade)",
      passed: false,
      value: "-277.60",
      threshold: "> 0",
    },
  ],
  outOfSample: { trades: 486, winRate: 0.31, expectancy: -277.6, profitFactor: 0.86, totalNet: -134914 },
  walkForward: { trades: 486, winRate: 0.31, expectancy: -277.6, profitFactor: 0.86, totalNet: -134914 },
  settingsBeatingNifty: 0,
  settingCount: 8,
  defaultMaxDrawdown: -0.313,
  caveats: ["Survivorship bias: only today's listed shares."],
};

afterEach(() => {
  server.resetHandlers();
});

describe("strategy gate", () => {
  it("does not run the gate on page load, and shows the result when asked", async () => {
    let calls = 0;
    server.use(
      http.get("*/dev/strategies/:id/gate", () => {
        calls += 1;
        return HttpResponse.json(REPORT);
      }),
    );
    const user = userEvent.setup();
    renderApp("/gates");
    await screen.findByRole("heading", { name: /pre-registered|Strategy gate/ }).catch(() => undefined);
    expect(calls).toBe(0);

    await user.selectOptions(screen.getByLabelText("strategy"), "momentum-breakout");
    await user.click(screen.getByRole("button", { name: "Run the gate" }));
    const panel = await screen.findByRole("region", { name: /momentum-breakout@1\.0\.0 · not passed/ });
    expect(calls).toBe(1);
    expect(within(panel).getByText("not approved")).toBeInTheDocument();
    expect(
      within(panel).getByText("Out-of-sample expectancy after costs (INR per trade)"),
    ).toBeInTheDocument();
    expect(within(panel).getByText("-277.60")).toBeInTheDocument();
    expect(within(panel).getByText("0 of 8")).toBeInTheDocument();
  });

  it("disables the run button until a strategy is chosen", async () => {
    renderApp("/gates");
    await waitFor(() => expect(screen.getByRole("button", { name: "Run the gate" })).toBeDisabled());
  });
});
