import clsx from "clsx";
import type { ReactNode } from "react";
import type { PortfolioReport, Trade } from "@/api/schemas";
import { formatCompact, formatInt, formatPct, formatPrice, formatSigned } from "@/lib/format";
import { EXIT_LABEL, exitRows, label, yearRows } from "@/lib/playbook";
import { EquityChart } from "./EquityChart";

const signed = (n: number | null, digits = 1) => (n === null ? "—" : formatSigned(n, digits));
const plain = (n: number | null, digits = 2) => (n === null ? "—" : n.toFixed(digits));
const tone = (n: number | null) => (n === null ? "" : n >= 0 ? "text-up" : "text-dn");

/** One portfolio simulation: headline numbers against Nifty, equity curve, years, exits and trades. */
export function BacktestResult({ report }: { report: PortfolioReport }) {
  const s = report.summary;
  const r = report.request;
  const years = yearRows(s);
  const exits = exitRows(s);
  const maxExit = Math.max(1, ...exits.map((e) => e.count));

  return (
    <div className="grid gap-4 px-4 pt-2 pb-4">
      <p className="m-0 text-[11px] text-tx3" aria-label="Simulation settings">
        {formatPrice(r.initialCapital)} paper capital · {r.maxPositions} positions · {r.riskPerTradePct}% risk
        per trade · max {r.maxPositionPct}% per position · stop {r.atrMultiple} × ATR(14) · hold ≤{" "}
        {r.maxHoldSessions} sessions · {r.slippageBps} bps slippage · costs {r.costs ? "on" : "off"} · regime
        filter {r.regimeFilter ? "on" : "off"} · {formatInt(report.signalDays)} signal days,{" "}
        {formatInt(report.candidateShares)} shares · {report.source} data · computed in {report.seconds} s
      </p>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6" aria-label="Headline numbers">
        <Stat name="CAGR" value={signed(s.cagrPct)} cls={tone(s.cagrPct)} vs={signed(s.benchmarkCagrPct)} />
        <Stat
          name="Total return"
          value={signed(s.totalReturnPct, 0)}
          cls={tone(s.totalReturnPct)}
          vs={signed(s.benchmarkTotalReturnPct, 0)}
        />
        <Stat
          name="Max drawdown"
          value={signed(s.maxDrawdownPct)}
          cls="text-dn"
          vs={signed(s.benchmarkMaxDrawdownPct)}
        />
        <Stat
          name="Sharpe"
          value={plain(s.sharpe)}
          sub={`volatility ${s.annualVolatilityPct === null ? "—" : formatPct(s.annualVolatilityPct)}`}
        />
        <Stat
          name="Trades"
          value={formatInt(s.trades)}
          sub={`win rate ${s.winRatePct === null ? "—" : `${s.winRatePct.toFixed(0)}%`}`}
        />
        <Stat
          name="Profit factor"
          value={plain(s.profitFactor)}
          sub={`costs ${s.totalCosts === null ? "—" : `₹${formatCompact(s.totalCosts)}`}`}
        />
      </div>

      <figure className="m-0">
        <figcaption className="mb-1 flex gap-4 text-[11px] text-tx3">
          <span>
            <span className="text-a">━</span> paper account
          </span>
          <span>
            <span className="text-tx3">━</span> {report.benchmark} bought and held, same capital
          </span>
          <span className="ml-auto">weekly · log scale</span>
        </figcaption>
        <div className="h-[280px]">
          <EquityChart days={report.equityCurveWeekly} />
        </div>
      </figure>

      <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        <Section title="Calendar years">
          <table className="w-full border-collapse text-xs" aria-label="Returns by year">
            <thead>
              <tr className="text-left text-[11px] text-tx3">
                <th className="py-1 font-normal">Year</th>
                <th className="py-1 text-right font-normal">Account</th>
                <th className="py-1 text-right font-normal">{report.benchmark}</th>
                <th className="py-1 text-right font-normal">Ahead</th>
              </tr>
            </thead>
            <tbody>
              {years.map((y) => {
                const ahead = y.strategy !== null && y.benchmark !== null ? y.strategy > y.benchmark : null;
                return (
                  <tr key={y.year} className="border-t border-line">
                    <td className="num py-1">{y.year}</td>
                    <td className={clsx("num py-1 text-right", tone(y.strategy))}>{signed(y.strategy)}</td>
                    <td className={clsx("num py-1 text-right", tone(y.benchmark))}>{signed(y.benchmark)}</td>
                    <td className="py-1 text-right">
                      {ahead === null ? "—" : ahead ? <span className="text-up">yes</span> : "no"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Section>

        <Section title="Why positions closed">
          <ul className="m-0 grid list-none gap-1.5 p-0" aria-label="Exits by reason">
            {exits.map((e) => (
              <li key={e.reason} className="grid grid-cols-[130px_1fr_48px] items-center gap-2 text-xs">
                <span className="text-tx2">{label(EXIT_LABEL, e.reason)}</span>
                <span className="h-2 rounded-full bg-line">
                  <span
                    className="block h-2 rounded-full bg-b/70"
                    style={{ width: `${String((e.count / maxExit) * 100)}%` }}
                  />
                </span>
                <span className="num text-right text-tx2">{formatInt(e.count)}</span>
              </li>
            ))}
          </ul>
          <p className="mt-2 mb-0 text-[11px] text-tx3">
            Average win {signed(s.avgWinPct)}, average loss {signed(s.avgLossPct)}, held{" "}
            {plain(s.avgSessionsHeld, 0)} sessions on average.
          </p>
        </Section>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Trades title="Best trades" trades={report.bestTrades} />
        <Trades title="Worst trades" trades={report.worstTrades} />
      </div>

      {report.caveats.length > 0 && <Caveats items={report.caveats} />}
    </div>
  );
}

function Stat(props: { name: string; value: string; cls?: string; vs?: string; sub?: string }) {
  return (
    <div className="rounded-xl border border-line bg-s2/40 px-3 py-2">
      <div className="text-[10px] tracking-[.1em] text-tx3 uppercase">{props.name}</div>
      <div className={clsx("num text-lg font-medium", props.cls)}>{props.value}</div>
      <div className="num text-[11px] text-tx3">
        {props.vs !== undefined ? `Nifty ${props.vs}` : props.sub}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title}>
      <h3 className="mt-0 mb-2 text-[10px] font-semibold tracking-[.12em] text-tx3 uppercase">{title}</h3>
      {children}
    </section>
  );
}

function Trades({ title, trades }: { title: string; trades: Trade[] }) {
  return (
    <Section title={title}>
      <table className="w-full border-collapse text-xs" aria-label={title}>
        <thead>
          <tr className="text-left text-[11px] text-tx3">
            <th className="py-1 font-normal">Share</th>
            <th className="py-1 font-normal">Held</th>
            <th className="py-1 font-normal">Exit</th>
            <th className="py-1 text-right font-normal">Net P&amp;L</th>
          </tr>
        </thead>
        <tbody>
          {trades.map((t) => (
            <tr key={`${t.symbol}-${t.entryDate}`} className="border-t border-line">
              <td className="num py-1">{t.symbol}</td>
              <td className="num py-1 text-tx2" title={`${t.entryDate} to ${t.exitDate}`}>
                {t.entryDate.slice(0, 7)} · {t.sessionsHeld}d
              </td>
              <td className="py-1 text-tx2">{label(EXIT_LABEL, t.exitReason)}</td>
              <td className={clsx("num py-1 text-right", tone(t.netPnl))}>
                {t.netPnl < 0 ? "−" : "+"}₹{formatCompact(Math.abs(t.netPnl))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Section>
  );
}

export function Caveats({ items }: { items: string[] }) {
  return (
    <details className="rounded-xl border border-warn/30 bg-warn/5 px-3 py-2 text-xs text-tx2">
      <summary className="cursor-pointer text-warn">
        Read before trusting these numbers ({items.length} caveats from the backend)
      </summary>
      <ul className="mt-2 mb-0 grid gap-1 pl-4">
        {items.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
    </details>
  );
}
