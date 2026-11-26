import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NOW, PAPER_VIEW } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { server } from "@/test/server";

// The chart draws on canvas, which jsdom lacks.
vi.mock("@/features/playbooks/EquityChart", () => ({
  EquityChart: ({ days }: { days: unknown[] }) => <div data-testid="equity-chart">{days.length} closes</div>,
}));

beforeEach(() => {
  // Only Date is faked, so timers, MSW and user-event behave normally. NOW is 17:00 IST on 26 Nov.
  vi.useFakeTimers({ toFake: ["Date"], now: NOW });
  localStorage.clear();
});
afterEach(() => {
  vi.useRealTimers();
});

describe("approval queue", () => {
  it("lists proposed orders, sells first, with the time left to decide and the recent decisions", async () => {
    renderApp("/approvals");
    const group = await screen.findByRole(
      "region",
      { name: "Orders for 2026-11-27" },
      { timeout: 5000 }, // the first render in a file also loads the modules
    );
    expect(group).toHaveTextContent("decide by 09:15 IST · 16 h 15 min left");
    const cards = within(group).getAllByRole("listitem");
    expect(cards.map((c) => c.getAttribute("aria-label"))).toEqual([
      "Proposed SELL 40 NOVA",
      "Proposed BUY 120 ACME",
    ]);
    expect(cards[1]).toHaveTextContent("Screen entry");

    const decided = await screen.findByRole("list", { name: "Recent decisions" });
    expect(decided).toHaveTextContent("filled");
    expect(decided).toHaveTextContent("Tester at 24 Nov 21:40 IST");
    expect(decided).toHaveTextContent("not decided before the open");
  });

  it("asks who decides before any decision can be made", async () => {
    const user = userEvent.setup();
    renderApp("/approvals");
    const card = await screen.findByRole("listitem", { name: "Proposed BUY 120 ACME" });
    expect(within(card).getByRole("button", { name: "Approve…" })).toBeDisabled();
    expect(card).toHaveTextContent("Enter your name above first.");
    await user.type(screen.getByLabelText(/Decide as/), "Tester");
    expect(within(card).getByRole("button", { name: "Approve…" })).toBeEnabled();
  });

  it("approves one order after a confirmation that names it, recording who and why", async () => {
    const user = userEvent.setup();
    const sent: { path: string; body: unknown }[] = [];
    let decided = false;
    server.use(
      http.post("*/api/v1/paper/orders/:id/:action", async ({ request }) => {
        sent.push({ path: new URL(request.url).pathname, body: await request.json() });
        decided = true;
        return HttpResponse.json({ ...PAPER_VIEW.proposed[0], status: "APPROVED", decidedBy: "Tester" });
      }),
      http.get("*/api/v1/paper/accounts/:id", () =>
        HttpResponse.json(decided ? { ...PAPER_VIEW, proposed: PAPER_VIEW.proposed.slice(1) } : PAPER_VIEW),
      ),
    );
    renderApp("/approvals");
    await user.type(await screen.findByLabelText(/Decide as/), "Tester");
    const card = await screen.findByRole("listitem", { name: "Proposed BUY 120 ACME" });
    await user.click(within(card).getByRole("button", { name: "Approve…" }));

    const confirm = within(card).getByRole("form", { name: "Confirm approve" });
    expect(confirm).toHaveTextContent("Approve BUY 120 ACME on paper, to fill at the 2026-11-27 open?");
    expect(sent).toHaveLength(0);
    await user.type(within(confirm).getByLabelText(/Why/), "fits the plan");
    await user.click(within(confirm).getByRole("button", { name: "Approve BUY 120 ACME" }));

    await waitFor(() => {
      expect(screen.queryByRole("listitem", { name: "Proposed BUY 120 ACME" })).not.toBeInTheDocument();
    });
    expect(sent).toEqual([
      { path: "/api/v1/paper/orders/11/approve", body: { by: "Tester", note: "fits the plan" } },
    ]);
    expect(localStorage.getItem("auratrade.decidedBy")).toBe("Tester");
  });

  it("shows the backend's refusal and lets you cancel", async () => {
    const user = userEvent.setup();
    localStorage.setItem("auratrade.decidedBy", "Tester");
    server.use(
      http.post("*/api/v1/paper/orders/:id/reject", () =>
        HttpResponse.json(
          { status: 409, detail: "decision window closed at 2026-11-27 09:15 IST; the order will expire" },
          { status: 409 },
        ),
      ),
    );
    renderApp("/approvals");
    const card = await screen.findByRole("listitem", { name: "Proposed SELL 40 NOVA" });
    await user.click(within(card).getByRole("button", { name: "Reject…" }));
    await user.click(within(card).getByRole("button", { name: "Reject SELL 40 NOVA" }));
    expect(await within(card).findByRole("alert")).toHaveTextContent("decision window closed");
    await user.click(within(card).getByRole("button", { name: "Cancel" }));
    expect(within(card).getByRole("button", { name: "Reject…" })).toBeInTheDocument();
  });

  it("closes the decision at 09:15 IST on the fill date", async () => {
    vi.setSystemTime(new Date("2026-11-27T03:45:00Z"));
    localStorage.setItem("auratrade.decidedBy", "Tester");
    renderApp("/approvals");
    const group = await screen.findByRole("region", { name: "Orders for 2026-11-27" });
    expect(group).toHaveTextContent("window closed at 09:15 IST · these will expire");
    for (const b of within(group).getAllByRole("button", { name: /Approve…|Reject…/ }))
      expect(b).toBeDisabled();
  });

  it("says when there is nothing to decide", async () => {
    server.use(
      http.get("*/api/v1/paper/accounts/:id", () => HttpResponse.json({ ...PAPER_VIEW, proposed: [] })),
    );
    renderApp("/approvals");
    expect(await screen.findByText("Nothing to decide")).toBeInTheDocument();
  });

  it("explains how to open an account when there is none", async () => {
    server.use(http.get("*/api/v1/paper/accounts", () => HttpResponse.json([])));
    renderApp("/approvals");
    expect(await screen.findByText("No paper account yet")).toBeInTheDocument();
  });
});

describe("paper portfolio", () => {
  it("shows equity, positions with risk to stop, the journal and the go-live criteria", async () => {
    renderApp("/portfolio");
    const numbers = await screen.findByLabelText("Account numbers");
    expect(numbers).toHaveTextContent(/\+1\.23%(?!%)/);
    expect(numbers).toHaveTextContent("at the 2026-11-26 close");
    expect(screen.getByTestId("equity-chart")).toHaveTextContent("3 closes");
    expect(screen.getByRole("link", { name: /2 proposed orders wait/ })).toHaveAttribute(
      "href",
      "/approvals?account=1",
    );

    const positions = screen.getByRole("table", { name: "Open positions" });
    expect(positions).toHaveTextContent("NOVA");
    expect(positions).toHaveTextContent("₹840");
    expect(positions).toHaveTextContent("4 / 120");

    expect(await screen.findByRole("table", { name: "Journal" })).toHaveTextContent("Holding limit");
    expect(screen.getByLabelText("Account rules")).toHaveTextContent("1.00% of equity");
    expect(screen.getByLabelText("Go-live criteria")).toHaveTextContent("50 closed trades");
  });
});
