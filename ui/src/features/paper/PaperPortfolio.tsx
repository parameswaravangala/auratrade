import { getRouteApi, Link } from "@tanstack/react-router";
import clsx from "clsx";
import type { ReactNode } from "react";
import { usePaperAccount, usePaperAccounts, usePaperTrades } from "@/api/queries";
import type { PaperAccount, PaperAccountView, PaperTrade } from "@/api/schemas";
import { Panel } from "@/components/Panel";
import { Empty, ErrorState, Loading } from "@/components/States";
import { EquityChart } from "@/features/playbooks/EquityChart";
import { formatInt, formatPrice, formatSigned } from "@/lib/format";
import { ORDER_REASON, riskToStop, summarise } from "@/lib/paper";
import { label, screenTitle } from "@/lib/playbook";
import { pickAccount } from "./account";
import { AccountPicker } from "./AccountPicker";

const route = getRouteApi("/portfolio");
const tone = (n: number) => (n > 0 ? "text-up" : n < 0 ? "text-dn" : "");

/** A paper account: equity, open positions, the closed-trade journal and the rules it trades by. */
export function PaperPortfolio() {
  const search = route.useSearch();
  const navigate = route.useNavigate();
  const accounts = usePaperAccounts();
  const account = pickAccount(accounts.data, search.account);
  const view = usePaperAccount(account?.id);
  const trades = usePaperTrades(account?.id);

  if (accounts.isPending) return <Loading label="Loading paper accounts" />;
  if (accounts.isError) return <ErrorState error={accounts.error} onRetry={() => void accounts.refetch()} />;
  if (!account) {
    return (
      <div className="mx-auto max-w-3xl pt-6">
        <Panel title="Paper portfolio">
          <Empty title="No paper account yet">
            <p className="m-0 text-xs">Open one with POST /api/v1/paper/accounts (dev profile).</p>
          </Empty>
        </Panel>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-4">
      <Panel
        title="Paper portfolio"
        badge={<span className="tag tag-w">paper · no broker</span>}
        aside={
          <AccountPicker
            accounts={accounts.data}
            current={account}
            onSelect={(id) => void navigate({ search: { account: id } })}
          />
        }
      >
        {view.isPending ? (
          <Loading label="Loading the account" />
        ) : view.isError ? (
          <ErrorState error={view.error} onRetry={() => void view.refetch()} />
        ) : (
          <Overview view={view.data} />
        )}
      </Panel>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-4 xl:grid-cols-2">
        <Panel
          title="Open positions"
          aside={
            view.data ? `${String(view.data.positions.length)} of ${String(account.maxPositions)}` : undefined
          }
        >
          {view.data && <Positions view={view.data} />}
        </Panel>
        <Panel
          title="Journal"
          aside={trades.data ? `${String(trades.data.length)} closed trades` : undefined}
        >
          {trades.isPending ? (
            <Loading label="Loading the journal" />
          ) : trades.isError ? (
            <ErrorState error={trades.error} onRetry={() => void trades.refetch()} />
          ) : (
            <Journal trades={trades.data} />
          )}
        </Panel>
      </div>

      <Panel title="Rules and go-live criteria">
        <Rules account={account} />
      </Panel>
    </div>
  );
}

function Overview({ view }: { view: PaperAccountView }) {
  const a = view.account;
  const s = summarise(view);
  const days = view.equity.map((e) => ({ ...e, benchmark: null }));
  return (
    <div className="grid gap-4 px-4 pt-3 pb-4">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5" aria-label="Account numbers">
        <Stat
          name="Equity"
          value={formatPrice(s.equity)}
          sub={s.asOf ? `at the ${s.asOf} close` : "no session processed yet"}
        />
        <Stat
          name="Since start"
          value={formatSigned(s.returnPct, 2)}
          cls={tone(s.returnPct)}
          sub={`from ${formatPrice(a.initialCapital)} on ${a.startedOn}`}
        />
        <Stat name="Cash" value={formatPrice(a.cash)} sub={`${formatPrice(s.invested)} invested`} />
        <Stat
          name="Positions"
          value={`${String(s.openPositions)} / ${String(a.maxPositions)}`}
          sub={`${String(view.proposed.length)} orders awaiting you`}
        />
        <Stat
          name="Sessions recorded"
          value={formatInt(view.equity.length)}
          sub="equity marked at each close"
        />
      </div>
      {view.proposed.length > 0 && (
        <Link to="/approvals" search={{ account: a.id }} className="text-xs text-a hover:underline">
          {view.proposed.length} proposed {view.proposed.length === 1 ? "order waits" : "orders wait"} for
          your decision →
        </Link>
      )}
      {days.length >= 2 ? (
        <figure className="m-0">
          <figcaption className="mb-1 text-[11px] text-tx3">
            <span className="text-a">━</span> paper equity at each close · log scale
          </figcaption>
          <div className="h-[240px]">
            <EquityChart days={days} />
          </div>
        </figure>
      ) : (
        <p className="m-0 text-xs text-tx3">
          The equity curve appears once two sessions have been processed (the paper-trading job runs at 20:15
          IST).
        </p>
      )}
    </div>
  );
}

function Positions({ view }: { view: PaperAccountView }) {
  if (view.positions.length === 0) return <Empty title="No open positions" />;
  const maxHold = view.account.maxHoldSessions;
  return (
    <div className="overflow-x-auto px-4 pt-2 pb-4">
      <table className="w-full border-collapse text-xs" aria-label="Open positions">
        <thead>
          <tr className="text-left text-[11px] text-tx3">
            <Th>Share</Th>
            <Th right>Qty</Th>
            <Th>Entered</Th>
            <Th right>Entry</Th>
            <Th right>Stop</Th>
            <Th right>Risk to stop</Th>
            <Th right>Held</Th>
          </tr>
        </thead>
        <tbody>
          {view.positions.map((p) => (
            <tr key={p.instrumentId} className="border-t border-line">
              <td className="py-1.5">
                <Link
                  to="/dossier"
                  search={{ symbol: p.symbol }}
                  className="num font-semibold text-tx hover:text-a"
                >
                  {p.symbol}
                </Link>
              </td>
              <Td>{formatInt(p.quantity)}</Td>
              <td className="num text-tx2">{p.entryDate}</td>
              <Td>{formatPrice(p.entryPrice)}</Td>
              <Td>{formatPrice(p.stopPrice)}</Td>
              <Td>{formatPrice(riskToStop(p))}</Td>
              <Td>
                {p.sessionsHeld} / {maxHold}
              </Td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-2 mb-0 text-[11px] text-tx3">
        Stops are checked at the close: a low at or below the stop proposes a sell for the next open, so the
        exit is usually worse than the stop (ADR 0024).
      </p>
    </div>
  );
}

function Journal({ trades }: { trades: PaperTrade[] }) {
  if (trades.length === 0) return <Empty title="No closed trades yet" />;
  return (
    <div className="overflow-x-auto px-4 pt-2 pb-4">
      <table className="w-full border-collapse text-xs" aria-label="Journal">
        <thead>
          <tr className="text-left text-[11px] text-tx3">
            <Th>Share</Th>
            <Th>Held</Th>
            <Th right>Entry → exit</Th>
            <Th>Why</Th>
            <Th right>Costs</Th>
            <Th right>Net P&amp;L</Th>
          </tr>
        </thead>
        <tbody>
          {trades.map((t) => (
            <tr key={t.id} className="border-t border-line">
              <td className="num py-1.5 font-semibold text-tx">
                {t.symbol} <span className="font-normal text-tx3">× {formatInt(t.quantity)}</span>
              </td>
              <td className="num text-tx2">
                {t.entryDate} → {t.exitDate} ({t.sessionsHeld})
              </td>
              <Td>
                {formatPrice(t.entryPrice)} → {formatPrice(t.exitPrice)}
              </Td>
              <td className="text-tx2">{label(ORDER_REASON, t.exitReason)}</td>
              <Td>{formatPrice(t.costs)}</Td>
              <Td cls={tone(t.netPnl)}>{formatPrice(t.netPnl)}</Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Rules({ account: a }: { account: PaperAccount }) {
  return (
    <div className="grid gap-4 px-4 pt-3 pb-4 lg:grid-cols-[1fr_1.4fr]">
      <dl className="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs" aria-label="Account rules">
        <Rule name="Screen">{screenTitle(a.screen)}</Rule>
        <Rule name="Capital">{formatPrice(a.initialCapital)}</Rule>
        <Rule name="Positions">up to {a.maxPositions}</Rule>
        <Rule name="Risk per trade">{(a.riskPerTrade * 100).toFixed(2)}% of equity</Rule>
        <Rule name="Max position">{(a.maxPosition * 100).toFixed(0)}% of equity</Rule>
        <Rule name="Stop">{a.atrMultiple} × ATR(14)</Rule>
        <Rule name="Holding limit">{a.maxHoldSessions} sessions</Rule>
        <Rule name="Regime filter">
          {a.regimeFilter ? "on: buys only while Nifty 50 is above its 200-session average" : "off"}
        </Rule>
        <Rule name="Slippage">{a.slippageBps} bps</Rule>
        <Rule name="Started">{a.startedOn}</Rule>
      </dl>
      <section aria-label="Go-live criteria">
        <h3 className="mt-0 mb-1.5 text-[10px] font-semibold tracking-[.12em] text-tx3 uppercase">
          Go-live criteria
        </h3>
        <p className="m-0 text-xs leading-relaxed whitespace-pre-line text-tx2">
          {a.goLiveCriteria ?? "None recorded."}
        </p>
      </section>
    </div>
  );
}

function Stat(props: { name: string; value: string; sub: string; cls?: string }) {
  return (
    <div className="rounded-xl border border-line bg-s2/40 px-3 py-2">
      <div className="text-[10px] tracking-[.1em] text-tx3 uppercase">{props.name}</div>
      <div className={clsx("num text-lg font-medium", props.cls)}>{props.value}</div>
      <div className="num text-[11px] text-tx3">{props.sub}</div>
    </div>
  );
}

const Th = ({ children, right }: { children: ReactNode; right?: boolean }) => (
  <th className={clsx("py-1 font-normal", right && "text-right")}>{children}</th>
);
const Td = ({ children, cls }: { children: ReactNode; cls?: string }) => (
  <td className={clsx("num text-right text-tx2", cls)}>{children}</td>
);
const Rule = ({ name, children }: { name: string; children: ReactNode }) => (
  <>
    <dt className="text-tx3">{name}</dt>
    <dd className="m-0 text-tx">{children}</dd>
  </>
);
