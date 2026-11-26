import { getRouteApi, Link } from "@tanstack/react-router";
import clsx from "clsx";
import { useState } from "react";
import { usePaperAccount, usePaperAccounts, usePaperOrders } from "@/api/queries";
import type { PaperOrder } from "@/api/schemas";
import { Panel } from "@/components/Panel";
import { Empty, ErrorState, Loading } from "@/components/States";
import { formatInt, formatPrice } from "@/lib/format";
import {
  byFillDate,
  decisionDeadline,
  ORDER_REASON,
  rememberDecider,
  rememberedDecider,
  STATUS_TONE,
  timeLeft,
} from "@/lib/paper";
import { label } from "@/lib/playbook";
import { istDayMonth, istHm } from "@/lib/time";
import { pickAccount, useNow } from "./account";
import { AccountPicker } from "./AccountPicker";
import { OrderCard } from "./OrderCard";

const route = getRouteApi("/approvals");

/**
 * Every paper order the evening job proposes waits here for a person (ADR 0005, 0024). Each one is
 * approved or rejected on its own, with a name and an optional reason; nothing is pre-selected and
 * there is no "approve all". Orders not decided by 09:15 IST on the fill date expire.
 */
export function ApprovalQueue() {
  const search = route.useSearch();
  const navigate = route.useNavigate();
  const accounts = usePaperAccounts();
  const account = pickAccount(accounts.data, search.account);
  const view = usePaperAccount(account?.id);
  const orders = usePaperOrders(account?.id);
  const now = useNow();
  const [decider, setDecider] = useState(rememberedDecider);

  if (accounts.isPending) return <Loading label="Loading paper accounts" />;
  if (accounts.isError) return <ErrorState error={accounts.error} onRetry={() => void accounts.refetch()} />;
  if (!account) return <NoAccount />;

  const proposed = view.data?.proposed ?? [];
  const decided = (orders.data ?? []).filter((o) => o.status !== "PROPOSED");

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
      <Panel
        title="Approval queue"
        badge={<span className="tag tag-w">paper · you decide every order</span>}
        aside={
          <AccountPicker
            accounts={accounts.data}
            current={account}
            onSelect={(id) => void navigate({ search: { account: id } })}
          />
        }
      >
        <div className="grid gap-4 px-4 pt-3 pb-4">
          <label className="flex flex-wrap items-center gap-2 text-[11px] text-tx3">
            Decide as
            <input
              className="num w-48 rounded-lg border border-line2 bg-s2 px-2 py-1.5 text-xs text-tx outline-none focus:border-a/60"
              value={decider}
              placeholder="your name"
              maxLength={60}
              autoComplete="name"
              onChange={(e) => {
                setDecider(e.target.value);
                rememberDecider(e.target.value.trim());
              }}
            />
            <span>recorded with each decision.</span>
          </label>

          {view.isPending ? (
            <Loading label="Loading proposed orders" />
          ) : view.isError ? (
            <ErrorState error={view.error} onRetry={() => void view.refetch()} />
          ) : proposed.length === 0 ? (
            <Empty title="Nothing to decide">
              <p className="m-0 max-w-md text-xs">
                The paper-trading job proposes orders after the evening candles (20:15 IST): sells for stops
                and holding limits, buys from {account.screen} for free slots. They appear here until 09:15
                IST the next session.
              </p>
            </Empty>
          ) : (
            byFillDate(proposed).map((g) => {
              const deadline = decisionDeadline(g.fillDate);
              const left = timeLeft(deadline, now);
              return (
                <section key={g.fillDate} aria-label={`Orders for ${g.fillDate}`} className="grid gap-2">
                  <h3 className="m-0 flex flex-wrap items-baseline gap-x-3 text-xs font-medium text-tx">
                    For the open on {g.fillDate}
                    <span className={clsx("num text-[11px]", left ? "text-warn" : "text-dn")}>
                      {left
                        ? `decide by 09:15 IST · ${left}`
                        : "window closed at 09:15 IST · these will expire"}
                    </span>
                  </h3>
                  <ol className="m-0 grid list-none gap-2 p-0">
                    {g.orders.map((o) => (
                      <OrderCard key={o.id} order={o} decider={decider.trim()} open={left !== null} />
                    ))}
                  </ol>
                </section>
              );
            })
          )}

          <p className="m-0 text-[11px] text-tx3">
            Approving fills the order at the next open on paper, with slippage and Zerodha&apos;s published
            delivery charges. No broker is connected. No risk-review agent checks these yet: your decision is
            the only check.
          </p>
        </div>
      </Panel>

      <Panel title="Recent decisions" aside={orders.data ? `${String(decided.length)} shown` : undefined}>
        {orders.isPending ? (
          <Loading label="Loading decisions" />
        ) : orders.isError ? (
          <ErrorState error={orders.error} onRetry={() => void orders.refetch()} />
        ) : decided.length === 0 ? (
          <Empty title="No decisions yet" />
        ) : (
          <ol className="m-0 grid list-none gap-1.5 px-4 pt-3 pb-4" aria-label="Recent decisions">
            {decided.map((o) => (
              <DecidedRow key={o.id} order={o} />
            ))}
          </ol>
        )}
      </Panel>
    </div>
  );
}

function DecidedRow({ order: o }: { order: PaperOrder }) {
  const tone = STATUS_TONE[o.status];
  return (
    <li className="grid gap-0.5 rounded-lg border border-line bg-s2/30 px-3 py-1.5 text-xs">
      <div className="flex flex-wrap items-center gap-2">
        <span className={clsx("num font-semibold", o.side === "BUY" ? "text-up" : "text-dn")}>{o.side}</span>
        <Link to="/dossier" search={{ symbol: o.symbol }} className="num font-semibold text-tx hover:text-a">
          {o.symbol}
        </Link>
        <span className="num text-tx2">× {formatInt(o.quantity)}</span>
        <span className="text-tx3">{label(ORDER_REASON, o.reason)}</span>
        <span className={clsx("tag ml-auto", tone && `tag-${tone}`)}>{o.status.toLowerCase()}</span>
      </div>
      <div className="num text-[11px] text-tx3">
        for {o.fillDate}
        {o.decidedBy && (
          <>
            {" "}
            · {o.decidedBy}
            {o.decidedAt && ` at ${istDayMonth(new Date(o.decidedAt))} ${istHm(new Date(o.decidedAt))} IST`}
          </>
        )}
        {o.fillPrice !== null && ` · filled ${formatPrice(o.fillPrice)}`}
        {o.statusNote && ` · ${o.statusNote}`}
      </div>
    </li>
  );
}

function NoAccount() {
  return (
    <div className="mx-auto max-w-3xl pt-6">
      <Panel title="Approval queue">
        <Empty title="No paper account yet">
          <p className="m-0 max-w-lg text-xs">
            An account needs its go-live criteria written up front (ADR 0005), so opening one is not a button
            yet. Open it with POST /api/v1/paper/accounts (dev profile); its proposals then appear here.
          </p>
        </Empty>
      </Panel>
    </div>
  );
}
