import { Link } from "@tanstack/react-router";
import clsx from "clsx";
import { useState } from "react";
import { useDecide } from "@/api/queries";
import type { PaperOrder } from "@/api/schemas";
import { formatInt, formatPrice } from "@/lib/format";
import { ORDER_REASON } from "@/lib/paper";
import { label } from "@/lib/playbook";

/**
 * One proposed order. Deciding takes two clicks: choose Approve or Reject, then confirm a sentence that
 * names the side, quantity and share. The decision is sent once; the backend records who and why.
 */
export function OrderCard({
  order: o,
  decider,
  open,
}: {
  order: PaperOrder;
  decider: string;
  open: boolean;
}) {
  const [choice, setChoice] = useState<"approve" | "reject" | null>(null);
  const [note, setNote] = useState("");
  const decide = useDecide();
  const what = `${o.side} ${formatInt(o.quantity)} ${o.symbol}`;
  const blocked = !open
    ? "The decision window has closed."
    : decider === ""
      ? "Enter your name above first."
      : null;

  return (
    <li
      className={clsx(
        "grid gap-2 rounded-xl border bg-s2/40 px-3 py-2.5",
        o.side === "BUY" ? "border-up/25" : "border-dn/30",
      )}
      aria-label={`Proposed ${what}`}
    >
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className={clsx("tag", o.side === "BUY" ? "tag-g" : "tag-r")}>{o.side}</span>
        <Link
          to="/dossier"
          search={{ symbol: o.symbol }}
          className="num text-base font-semibold text-tx hover:text-a"
        >
          {o.symbol}
        </Link>
        <span className="num text-tx">× {formatInt(o.quantity)}</span>
        <span className="text-xs text-tx2">{label(ORDER_REASON, o.reason)}</span>
        {o.stopPrice !== null && (
          <span className="num text-xs text-tx2">stop {formatPrice(o.stopPrice)}</span>
        )}
        {o.score !== null && <span className="num text-xs text-tx3">score {o.score.toFixed(2)}</span>}
        <span className="num ml-auto text-[11px] text-tx3">signal {o.signalDate}</span>
      </div>
      {o.detail && <p className="m-0 text-xs text-tx2">{o.detail}</p>}

      {choice === null ? (
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="btn"
            disabled={blocked !== null}
            onClick={() => {
              setChoice("approve");
            }}
          >
            Approve…
          </button>
          <button
            type="button"
            className="btn"
            disabled={blocked !== null}
            onClick={() => {
              setChoice("reject");
            }}
          >
            Reject…
          </button>
          {blocked && <span className="text-[11px] text-tx3">{blocked}</span>}
        </div>
      ) : (
        <form
          className="grid gap-2 rounded-lg border border-line2 bg-s1/60 p-2.5"
          aria-label={`Confirm ${choice}`}
          onSubmit={(e) => {
            e.preventDefault();
            if (blocked) return;
            decide.mutate({ orderId: o.id, approve: choice === "approve", by: decider, note: note.trim() });
          }}
        >
          <p className="m-0 text-xs text-tx">
            {choice === "approve"
              ? `Approve ${what} on paper, to fill at the ${o.fillDate} open?`
              : `Reject ${what}? It will not be traded.`}
          </p>
          <label className="grid gap-1 text-[11px] text-tx3">
            Why (optional, kept in the journal)
            <textarea
              className="min-h-12 rounded-lg border border-line2 bg-s2 px-2 py-1.5 text-xs text-tx outline-none focus:border-a/60"
              value={note}
              maxLength={500}
              onChange={(e) => {
                setNote(e.target.value);
              }}
            />
          </label>
          <div className="flex items-center gap-2">
            <button
              type="submit"
              className={clsx("btn", choice === "approve" && "btn-pri")}
              disabled={decide.isPending || blocked !== null}
            >
              {decide.isPending ? "Sending…" : choice === "approve" ? `Approve ${what}` : `Reject ${what}`}
            </button>
            <button
              type="button"
              className="btn"
              disabled={decide.isPending}
              onClick={() => {
                setChoice(null);
                decide.reset();
              }}
            >
              Cancel
            </button>
            <span className="text-[11px] text-tx3">as {decider}</span>
          </div>
          {decide.isError && (
            <div role="alert" className="text-xs text-dn">
              {decide.error.message}
            </div>
          )}
        </form>
      )}
    </li>
  );
}
