import clsx from "clsx";
import { useState } from "react";
import { useDecideMany, type DecisionOutcome } from "@/api/queries";
import type { PaperOrder } from "@/api/schemas";
import { formatInt, formatPrice } from "@/lib/format";
import { estimate } from "@/lib/paper";

/**
 * Deciding the ticked orders together. Nothing is ticked for you; the confirmation lists every order
 * with its reference value and risk, and the totals, before one button sends them.
 */
export function BulkDecision(props: {
  selected: PaperOrder[];
  decider: string;
  onDecider: (name: string) => void;
  onClear: () => void;
  onDecided: (orderIds: number[]) => void;
}) {
  const { selected, decider, onDecider, onClear, onDecided } = props;
  const [choice, setChoice] = useState<"approve" | "reject" | null>(null);
  const [note, setNote] = useState("");
  const [outcomes, setOutcomes] = useState<{ approve: boolean; list: DecisionOutcome[] } | null>(null);
  const many = useDecideMany();
  const by = decider.trim();

  const ests = selected.map((o) => {
    return { order: o, est: estimate(o) };
  });
  const priced = ests.flatMap((e) => (e.est ? [e.est] : []));
  const totalValue = priced.reduce((s, e) => s + e.value, 0);
  const totalRisk = priced.reduce((s, e) => s + (e.riskToStop ?? 0), 0);
  const unpriced = selected.length - priced.length;
  const n = selected.length;
  const orders = n === 1 ? "order" : "orders";

  if (n === 0 && !outcomes) return null;

  return (
    <div
      className="sticky top-0 z-10 grid gap-2 rounded-xl border border-a/40 bg-s1/95 px-3 py-2.5 backdrop-blur"
      role="region"
      aria-label="Bulk decision"
    >
      {outcomes && (
        <OutcomeLine
          outcomes={outcomes}
          all={selected}
          onClose={() => {
            setOutcomes(null);
          }}
        />
      )}

      {n > 0 && choice === null && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs">
          <span className="num text-tx">
            <b>{n}</b> selected
          </span>
          <span className="num text-tx2">
            value about {formatPrice(Math.round(totalValue))} · risk to stop{" "}
            {formatPrice(Math.round(totalRisk))}
            {unpriced > 0 && ` (${String(unpriced)} without a reference price)`}
          </span>
          <span className="ml-auto flex gap-2">
            <button
              type="button"
              className="btn"
              onClick={() => {
                setChoice("approve");
              }}
            >
              Approve {n}…
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => {
                setChoice("reject");
              }}
            >
              Reject {n}…
            </button>
            <button type="button" className="btn" onClick={onClear}>
              Clear
            </button>
          </span>
        </div>
      )}

      {n > 0 && choice !== null && (
        <form
          className="grid gap-2"
          aria-label={`Confirm ${choice} of ${String(n)}`}
          onSubmit={(e) => {
            e.preventDefault();
            if (by === "" || many.isPending) return;
            const approve = choice === "approve";
            many.mutate(
              { orderIds: selected.map((o) => o.id), approve, by, note: note.trim() },
              {
                onSuccess: (list) => {
                  setOutcomes({ approve, list });
                  setChoice(null);
                  setNote("");
                  onDecided(list.filter((x) => x.ok).map((x) => x.orderId));
                },
              },
            );
          }}
        >
          <p className="m-0 text-xs text-tx">
            {choice === "approve"
              ? `Approve these ${String(n)} ${orders} on paper at the proposed quantities, with no limit price, each to fill at its next open? To change one, approve it on its own.`
              : `Reject these ${String(n)} ${orders}? They will not be traded.`}
          </p>
          <table className="w-full border-collapse text-xs" aria-label="Orders to decide">
            <thead>
              <tr className="text-left text-[11px] text-tx3">
                <th className="py-0.5 font-normal">Order</th>
                <th className="py-0.5 text-right font-normal">Ref price</th>
                <th className="py-0.5 text-right font-normal">Value</th>
                <th className="py-0.5 text-right font-normal">Risk to stop</th>
              </tr>
            </thead>
            <tbody>
              {ests.map(({ order: o, est }) => (
                <tr key={o.id} className="num border-t border-line">
                  <td className="py-1">
                    <span className={o.side === "BUY" ? "text-up" : "text-dn"}>{o.side}</span>{" "}
                    {formatInt(o.quantity)} <b className="font-semibold text-tx">{o.symbol}</b>
                    <span className="text-tx3"> for {o.fillDate}</span>
                  </td>
                  <td className="text-right text-tx2">{est ? formatPrice(est.price) : "—"}</td>
                  <td className="text-right text-tx2">{est ? formatPrice(Math.round(est.value)) : "—"}</td>
                  <td className="text-right text-tx2">
                    {est?.riskToStop != null ? formatPrice(Math.round(est.riskToStop)) : "—"}
                  </td>
                </tr>
              ))}
              <tr className="num border-t border-line2 font-medium text-tx">
                <td className="py-1">Total</td>
                <td />
                <td className="text-right">{formatPrice(Math.round(totalValue))}</td>
                <td className="text-right">{formatPrice(Math.round(totalRisk))}</td>
              </tr>
            </tbody>
          </table>
          <div className="flex flex-wrap gap-3">
            <label className="grid gap-1 text-[11px] text-tx3">
              Your name (recorded with each decision)
              <input
                className="num w-56 rounded-lg border border-line2 bg-s2 px-2 py-1.5 text-xs text-tx outline-none focus:border-a/60"
                value={decider}
                required
                maxLength={60}
                autoComplete="name"
                onChange={(e) => {
                  onDecider(e.target.value);
                }}
              />
            </label>
            <label className="grid min-w-60 flex-1 gap-1 text-[11px] text-tx3">
              Why (optional, kept with each order)
              <input
                className="rounded-lg border border-line2 bg-s2 px-2 py-1.5 text-xs text-tx outline-none focus:border-a/60"
                value={note}
                maxLength={500}
                onChange={(e) => {
                  setNote(e.target.value);
                }}
              />
            </label>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="submit"
              className={clsx("btn", choice === "approve" && "btn-pri")}
              disabled={by === "" || many.isPending}
            >
              {many.isPending
                ? "Sending…"
                : `${choice === "approve" ? "Approve" : "Reject"} ${String(n)} ${orders}`}
            </button>
            <button
              type="button"
              className="btn"
              disabled={many.isPending}
              onClick={() => {
                setChoice(null);
              }}
            >
              Cancel
            </button>
            {by === "" && <span className="text-[11px] text-warn">Enter your name to confirm.</span>}
          </div>
        </form>
      )}
    </div>
  );
}

function OutcomeLine(props: {
  outcomes: { approve: boolean; list: DecisionOutcome[] };
  all: PaperOrder[];
  onClose: () => void;
}) {
  const { approve, list } = props.outcomes;
  const ok = list.filter((x) => x.ok).length;
  const failed = list.filter((x) => !x.ok);
  return (
    <div role={failed.length > 0 ? "alert" : "status"} className="flex items-start gap-3 text-xs">
      <div className="grid gap-0.5">
        <span className={failed.length > 0 ? "text-warn" : "text-up"}>
          {ok} {approve ? "approved" : "rejected"}
          {failed.length > 0 && `, ${String(failed.length)} refused`}
        </span>
        {failed.map((f) => (
          <span key={f.orderId} className="text-dn">
            {props.all.find((o) => o.id === f.orderId)?.symbol ?? `order ${String(f.orderId)}`}: {f.message}
          </span>
        ))}
      </div>
      <button type="button" className="ml-auto text-tx3 hover:text-tx" onClick={props.onClose}>
        Dismiss
      </button>
    </div>
  );
}
