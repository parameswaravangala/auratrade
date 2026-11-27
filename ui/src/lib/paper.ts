/**
 * Display helpers for paper trading (ADR 0024). The backend decides what is allowed (it refuses a
 * decision after 09:15 IST on the fill date); these only label, group and count down for the screen.
 */
import type { PaperAccountView, PaperOrder, PaperPosition } from "@/api/schemas";

/** Why an order was proposed (PaperTradingService). */
export const ORDER_REASON: Record<string, string> = {
  ENTRY: "Screen entry",
  STOP: "Stop reached",
  TIME: "Holding limit",
};

export const STATUS_TONE: Record<PaperOrder["status"], "a" | "g" | "r" | "w" | "b" | ""> = {
  PROPOSED: "a",
  APPROVED: "b",
  REJECTED: "r",
  EXPIRED: "w",
  FILLED: "g",
  UNFILLED: "w",
};

/** When decisions on this order close (the backend's decideBy, 09:15 IST on the fill date). */
export const decisionDeadline = (o: Pick<PaperOrder, "decideBy">): Date => new Date(o.decideBy);

/** "11 h 40 min left", "4 min left", or null once the window has closed. */
export function timeLeft(deadline: Date, now: Date): string | null {
  const ms = deadline.getTime() - now.getTime();
  if (ms <= 0) return null;
  const min = Math.ceil(ms / 60_000);
  if (min < 60) return `${String(min)} min left`;
  const h = Math.floor(min / 60);
  return `${String(h)} h ${String(min % 60)} min left`;
}

/** Proposed orders grouped by the session they would fill at, soonest first; sells before buys. */
export function byFillDate(orders: PaperOrder[]): { fillDate: string; orders: PaperOrder[] }[] {
  const groups = new Map<string, PaperOrder[]>();
  for (const o of orders) groups.set(o.fillDate, [...(groups.get(o.fillDate) ?? []), o]);
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([fillDate, os]) => ({
      fillDate,
      orders: [...os].sort((a, b) =>
        a.side === b.side ? a.symbol.localeCompare(b.symbol) : a.side === "SELL" ? -1 : 1,
      ),
    }));
}

/** Rupees lost if the position exits exactly at its stop, before costs and gaps. */
export const riskToStop = (p: PaperPosition): number => (p.entryPrice - p.stopPrice) * p.quantity;

export type AccountSummary = {
  equity: number;
  /** Date of the newest equity point; null before the first session was processed. */
  asOf: string | null;
  returnPct: number;
  invested: number;
  openPositions: number;
};

/** Headline numbers from the newest equity point (or cash before any session was processed). */
export function summarise(view: PaperAccountView): AccountSummary {
  const a = view.account;
  const last = view.equity.at(-1);
  const equity = last?.equity ?? a.cash;
  return {
    equity,
    asOf: last?.date ?? null,
    returnPct: a.initialCapital === 0 ? 0 : ((equity - a.initialCapital) / a.initialCapital) * 100,
    invested: equity - (last?.cash ?? a.cash),
    openPositions: view.positions.length,
  };
}

const DECIDER_KEY = "auratrade.decidedBy";

/** The name decisions are recorded under, remembered in this browser only. */
export function rememberedDecider(): string {
  try {
    return localStorage.getItem(DECIDER_KEY) ?? "";
  } catch {
    return "";
  }
}

export function rememberDecider(name: string): void {
  try {
    localStorage.setItem(DECIDER_KEY, name);
  } catch {
    // Private windows may refuse storage; the name is then asked again next time.
  }
}

export type OrderEstimate = { price: number; quantity: number; value: number; riskToStop: number | null };

/** Shares the order trades: the approved quantity when the person lowered it, else the proposal. */
export const tradedQuantity = (o: PaperOrder): number => o.approvedQuantity ?? o.quantity;

/**
 * What an order is worth at its reference price (the signal-day close it was sized from), for the
 * given quantity. The fill is at the next open with slippage, so the real price will differ.
 * null when the backend sent no reference price.
 */
export function estimate(o: PaperOrder, quantity = tradedQuantity(o)): OrderEstimate | null {
  const price = o.referencePrice;
  if (price === null) return null;
  return {
    price,
    quantity,
    value: price * quantity,
    riskToStop: o.side === "BUY" && o.stopPrice !== null ? (price - o.stopPrice) * quantity : null,
  };
}

/**
 * Checks what the person typed into the approval form, as the backend will (it checks again).
 * Returns the values to send, or the reason they cannot be sent.
 */
export function approvalEdits(
  o: PaperOrder,
  quantityText: string,
  limitText: string,
): { quantity?: number; limitPrice?: number } | { error: string } {
  const out: { quantity?: number; limitPrice?: number } = {};
  const q = quantityText.trim();
  if (o.side === "BUY" && q !== "" && q !== String(o.quantity)) {
    const n = Number(q);
    if (!Number.isInteger(n) || n < 1 || n > o.quantity) {
      return { error: `Quantity must be a whole number from 1 to ${String(o.quantity)}.` };
    }
    out.quantity = n;
  }
  const l = limitText.trim();
  if (l !== "") {
    const n = Number(l);
    if (!Number.isFinite(n) || n <= 0) return { error: "Limit price must be a positive number." };
    out.limitPrice = n;
  }
  return out;
}
