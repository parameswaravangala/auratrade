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
    const group = await screen.findByRole("region", { name: "Orders for 2026-11-27" });
    expect(group).toHaveTextContent("decide by 09:15 IST · 16 h 15 min left");
    const cards = within(group).getAllByRole("listitem");
    expect(cards.map((c) => c.getAttribute("aria-label"))).toEqual([
      "Proposed SELL 40 NOVA",
      "Proposed BUY 120 ACME",
    ]);
    expect(cards[1]).toHaveTextContent("Screen entry");
    // Reference price is the signal day close; ACME 120 × 450 with the stop at 410.50.
    await waitFor(() => {
      expect(within(cards[1]!).getByLabelText("Estimate")).toHaveTextContent("ref price ₹450.00");
    });
    expect(within(cards[1]!).getByLabelText("Estimate")).toHaveTextContent("value about ₹54,000");
    expect(within(cards[1]!).getByLabelText("Estimate")).toHaveTextContent("risk to stop ₹4,740");

    const decided = await screen.findByRole("list", { name: "Recent decisions" });
    expect(decided).toHaveTextContent("filled");
    expect(decided).toHaveTextContent("Tester at 24 Nov 21:40 IST");
    expect(decided).toHaveTextContent("not decided before the open");
  });

  it("asks who decides in the confirmation, and remembers it for the next order", async () => {
    const user = userEvent.setup();
    renderApp("/approvals");
    const card = await screen.findByRole("listitem", { name: "Proposed BUY 120 ACME" });
    await user.click(within(card).getByRole("button", { name: "Approve…" }));
    const confirm = within(card).getByRole("form", { name: "Confirm approve" });
    expect(within(confirm).getByRole("button", { name: "Approve BUY 120 ACME" })).toBeDisabled();
    expect(confirm).toHaveTextContent("Enter your name to confirm.");
    await user.type(within(confirm).getByLabelText(/Your name/), "Tester");
    expect(within(confirm).getByRole("button", { name: "Approve BUY 120 ACME" })).toBeEnabled();

    const other = screen.getByRole("listitem", { name: "Proposed SELL 40 NOVA" });
    await user.click(within(other).getByRole("button", { name: "Reject…" }));
    expect(within(other).getByLabelText(/Your name/)).toHaveValue("Tester");
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
    const card = await screen.findByRole("listitem", { name: "Proposed BUY 120 ACME" });
    await user.click(within(card).getByRole("button", { name: "Approve…" }));

    const confirm = within(card).getByRole("form", { name: "Confirm approve" });
    await user.type(within(confirm).getByLabelText(/Your name/), "Tester");
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

  it("approves ticked orders together after one confirmation that lists them, reporting refusals", async () => {
    const user = userEvent.setup();
    localStorage.setItem("auratrade.decidedBy", "Tester");
    const sent: string[] = [];
    server.use(
      http.post("*/api/v1/paper/orders/:id/approve", ({ params }) => {
        sent.push(String(params.id));
        return params.id === "12"
          ? HttpResponse.json({ status: 409, detail: "order 12 is EXPIRED, not PROPOSED" }, { status: 409 })
          : HttpResponse.json({ ...PAPER_VIEW.proposed[0], status: "APPROVED" });
      }),
    );
    renderApp("/approvals");
    const all = await screen.findByRole("checkbox", { name: "Select all 2 for 2026-11-27" });
    expect(screen.queryByRole("region", { name: "Bulk decision" })).not.toBeInTheDocument();
    expect(all).not.toBeChecked();
    await user.click(all);

    const bulk = screen.getByRole("region", { name: "Bulk decision" });
    expect(bulk).toHaveTextContent("2 selected");
    await user.click(within(bulk).getByRole("button", { name: "Approve 2…" }));
    const list = within(bulk).getByRole("table", { name: "Orders to decide" });
    expect(list).toHaveTextContent("SELL 40 NOVA");
    expect(list).toHaveTextContent("BUY 120 ACME");
    await waitFor(() => {
      expect(list).toHaveTextContent("₹54,000");
    });
    expect(sent).toHaveLength(0);
    await user.click(within(bulk).getByRole("button", { name: "Approve 2 orders" }));

    expect(await within(bulk).findByRole("alert")).toHaveTextContent("1 approved, 1 refused");
    expect(within(bulk).getByRole("alert")).toHaveTextContent("NOVA: order 12 is EXPIRED, not PROPOSED");
    expect(sent.sort()).toEqual(["11", "12"]);
    expect(screen.getByRole("checkbox", { name: "Select BUY 120 ACME" })).not.toBeChecked();
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
