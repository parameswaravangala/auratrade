import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { candles } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

// The real chart draws on canvas, which jsdom lacks; the dossier's data flow is what we test here.
vi.mock("@/features/dossier/CandleChart", () => ({
  CandleChart: ({ bars }: { bars: unknown[] }) => <div data-testid="candle-chart">{bars.length} bars</div>,
}));

describe("command center", () => {
  it("shows job narrative, budget and instrument universe from the backend", async () => {
    renderApp("/");
    const narrative = await screen.findByRole("region", { name: "System narrative" });
    expect(await within(narrative).findByText(/Upstox token not set/)).toBeInTheDocument();
    expect(await screen.findByRole("img", { name: /LLM budget 0.8% used/ })).toBeInTheDocument();
    expect(await screen.findByRole("img", { name: "3,512 active instruments" })).toBeInTheDocument();
    expect(screen.getByText("Paper · Dev")).toBeInTheDocument();
  });

  it("resolves a name and shows which stage matched", async () => {
    const user = userEvent.setup();
    renderApp("/");
    await user.type(await screen.findByLabelText("Company name or symbol"), "Acme Cookers Ltd{Enter}");
    const stages = await screen.findByRole("list", { name: "Resolver stages" });
    const states = within(stages)
      .getAllByRole("listitem")
      .map((li) => li.getAttribute("data-state"));
    expect(states).toEqual(["miss", "miss", "hit", "skip"]);
    expect(screen.getByText("RESOLVED")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open dossier" })).toHaveAttribute(
      "href",
      "/dossier?symbol=ACME",
    );
  });

  it("lists every candidate for an ambiguous name and picks none", async () => {
    const user = userEvent.setup();
    renderApp("/");
    await user.type(await screen.findByLabelText("Company name or symbol"), "Acme{Enter}");
    expect(await screen.findByText("AMBIGUOUS")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ACMEFIN" })).toBeInTheDocument();
    expect(screen.queryByText("RESOLVED")).not.toBeInTheDocument();
  });

  it("shows a clear error when the backend is down", async () => {
    server.use(http.get("*/dev/jobs/runs", () => new HttpResponse(null, { status: 502 })));
    renderApp("/");
    expect((await screen.findAllByRole("alert"))[0]).toHaveTextContent(/Backend unavailable/);
  });
});

describe("dossier", () => {
  it("asks for a symbol when none is chosen", async () => {
    renderApp("/dossier");
    expect(await screen.findByText("Pick a share")).toBeInTheDocument();
  });

  it("shows the empty state with a dev load action when nothing is stored", async () => {
    renderApp("/dossier?symbol=ACME");
    expect(await screen.findByText("No stored candles for ACME")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Load from provider/ })).toBeEnabled();
  });

  it("charts stored candles and summarises them", async () => {
    server.use(http.get("*/dev/marketdata/candles", () => HttpResponse.json(candles(5))));
    renderApp("/dossier?symbol=ACME&range=1M");
    expect(await screen.findByTestId("candle-chart")).toHaveTextContent("5 bars");
    expect(screen.getByText(/5 day bars, 2026-11-02 to 2026-11-06/)).toBeInTheDocument();
    expect(screen.getByText("₹104.00")).toBeInTheDocument();
  });

  it("shows model output as unverified text", async () => {
    server.use(
      http.post("*/dev/llm/ask", () =>
        HttpResponse.json({
          text: "<b>not html</b> A calm trend.",
          tier: "LOCAL",
          model: "llama3.2:3b",
          usage: { inputTokens: 20, outputTokens: 10, cacheReadTokens: 0, cacheWriteTokens: 0 },
          costUsd: 0,
          runId: "r1",
        }),
      ),
    );
    const user = userEvent.setup();
    renderApp("/dossier?symbol=ACME");
    await user.type(await screen.findByLabelText("Ask the copilot"), "What changed?{Enter}");
    expect(await screen.findByText("Model output · unverified")).toBeInTheDocument();
    expect(screen.getByText("<b>not html</b> A calm trend.")).toBeInTheDocument();
  });
});

describe("shell", () => {
  it("opens the command palette with Ctrl K and navigates", async () => {
    const user = userEvent.setup();
    const { router } = renderApp("/");
    await screen.findByRole("region", { name: "Data pipeline" });
    await user.keyboard("{Control>}k{/Control}");
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("combobox"), "Agent observatory");
    await user.keyboard("{Enter}");
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/agents");
    });
    expect(await screen.findByText("designed · not built yet")).toBeInTheDocument();
  });
});
