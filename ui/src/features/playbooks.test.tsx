import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { PORTFOLIO } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

// The chart draws on canvas, which jsdom lacks.
vi.mock("@/features/playbooks/EquityChart", () => ({
  EquityChart: ({ days }: { days: unknown[] }) => <div data-testid="equity-chart">{days.length} weeks</div>,
}));

describe("playbook lab", () => {
  it("shows the screens, the rules and today's shortlist linked to the dossier", async () => {
    renderApp("/playbooks");
    const picker = await screen.findByRole("radiogroup", { name: "Screen" });
    expect(within(picker).getByRole("radio", { name: /Relative-strength leaders/ })).toBeChecked();
    expect(screen.getByRole("list", { name: "Rules" })).toHaveTextContent("Six-month return ahead");

    const list = await screen.findByRole("list", { name: "Shortlist" });
    expect(within(list).getByRole("link", { name: "ACME" })).toHaveAttribute("href", "/dossier?symbol=ACME");
    expect(screen.getByLabelText("Screen funnel")).toHaveTextContent("1,200 evaluated");
    expect(screen.getByText("600 illiquid")).toBeInTheDocument();
    expect(screen.getByText(/as of 2026-11-25/)).toBeInTheDocument();
  });

  it("switches screen through the URL", async () => {
    const user = userEvent.setup();
    const { router } = renderApp("/playbooks");
    await user.click(await screen.findByRole("radio", { name: /Pullback to the 50-day average/ }));
    await waitFor(() => {
      expect(router.state.location.search).toEqual({ screen: "pullback-to-50dma" });
    });
    expect(screen.getByRole("radio", { name: /Pullback/ })).toBeChecked();
  });

  it("runs a backtest only when asked, with the chosen settings, and shows it against Nifty", async () => {
    const user = userEvent.setup();
    const asked: URLSearchParams[] = [];
    server.use(
      http.get("*/dev/backtest/portfolio", async ({ request }) => {
        asked.push(new URL(request.url).searchParams);
        await delay(50);
        return HttpResponse.json(PORTFOLIO);
      }),
    );
    renderApp("/playbooks");
    const run = await screen.findByRole("button", { name: /Run backtest/ });
    await waitFor(() => {
      expect(run).toBeEnabled();
    });
    expect(asked).toHaveLength(0);

    await user.selectOptions(screen.getByLabelText("Stop (× ATR 14)"), "3");
    await user.click(screen.getByLabelText(/Regime filter: enter only/));
    const toDate = screen.getByLabelText(/^To\b(?!ggle)/);
    expect(toDate).toHaveValue("2026-11-25");
    fireEvent.change(toDate, { target: { value: "2026-11-24" } });
    await user.click(run);

    expect(await screen.findByRole("status", { name: "Simulating the account in Java" })).toBeInTheDocument();
    const headline = await screen.findByLabelText("Headline numbers");
    expect(headline).toHaveTextContent("+12.3%");
    expect(headline).toHaveTextContent("Nifty +8.7%");
    expect(headline).toHaveTextContent("−31.4%");
    expect(screen.getByRole("table", { name: "Returns by year" })).toHaveTextContent("2024+30.1%+8.8%yes");
    expect(screen.getByRole("table", { name: "Worst trades" })).toHaveTextContent("Gapped through stop");
    expect(screen.getByText(/Survivorship bias/)).toBeInTheDocument();

    expect(asked).toHaveLength(1);
    const p = asked[0];
    expect(p?.get("screen")).toBe("relative-strength-leaders");
    expect(p?.get("to")).toBe("2026-11-24");
    expect(p?.get("atrMultiple")).toBe("3");
    expect(p?.get("regime")).toBe("true");
  });

  it("keeps a finished backtest when you visit the dossier and come back", async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get("*/dev/backtest/portfolio", () => {
        calls++;
        return HttpResponse.json(PORTFOLIO);
      }),
    );
    const { router } = renderApp("/playbooks");
    const run = await screen.findByRole("button", { name: /Run backtest/ });
    await waitFor(() => {
      expect(run).toBeEnabled();
    });
    await user.click(run);
    await screen.findByLabelText("Headline numbers");

    await user.click(
      within(screen.getByRole("list", { name: "Shortlist" })).getByRole("link", { name: "ACME" }),
    );
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/dossier");
    });
    router.history.back();
    expect(await screen.findByLabelText("Headline numbers")).toHaveTextContent("+12.3%");
    expect(calls).toBe(1);
  });

  it("shows the robustness sweep as grids with the in-sample pick outlined", async () => {
    const user = userEvent.setup();
    renderApp("/playbooks");
    const run = await screen.findByRole("button", { name: /Test robustness/ });
    await waitFor(() => {
      expect(run).toBeEnabled();
    });
    await user.click(run);
    expect(await screen.findByLabelText("Settings beating Nifty out of sample")).toHaveTextContent("2 / 3");
    const on = screen.getByRole("table", { name: "Regime filter on" });
    const cells = on.querySelectorAll("[data-beats]");
    expect([...cells].map((c) => c.getAttribute("data-beats"))).toEqual(["true", "true"]);
    expect(screen.getByLabelText("Chosen setting")).toHaveTextContent("stop 3 × ATR, hold 120 sessions");
  });

  it("explains a backend refusal instead of failing silently", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("*/dev/backtest/portfolio", () =>
        HttpResponse.json(
          { title: "Conflict", status: 409, detail: "no NIFTY history stored" },
          { status: 409 },
        ),
      ),
    );
    renderApp("/playbooks");
    const run = await screen.findByRole("button", { name: /Run backtest/ });
    await waitFor(() => {
      expect(run).toBeEnabled();
    });
    await user.click(run);
    expect(await screen.findByRole("alert")).toHaveTextContent("no NIFTY history stored");
  });
});
