import { Link } from "@tanstack/react-router";
import type { ScreenResult } from "@/api/schemas";
import { Empty } from "@/components/States";
import { formatInt } from "@/lib/format";
import { label, metricLabel, metricText, SKIP_LABEL } from "@/lib/playbook";

/** Today's candidates for one screen: the funnel (evaluated, skipped, matched) and the ranked list. */
export function Shortlist({ result }: { result: ScreenResult }) {
  const skipped = Object.entries(result.skipped).sort((a, b) => b[1] - a[1]);
  return (
    <div className="grid gap-3 px-4 pt-2 pb-4">
      <div
        className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-xs text-tx2"
        aria-label="Screen funnel"
      >
        <span>
          <b className="num text-base text-tx">{formatInt(result.evaluated)}</b> evaluated
        </span>
        <span>
          <b className="num text-base text-tx">{formatInt(result.matched)}</b> matched
        </span>
        <span>
          <b className="num text-base text-a">{formatInt(result.shortlist.length)}</b> shortlisted
        </span>
        {skipped.length > 0 && (
          <span className="flex flex-wrap gap-1.5">
            {skipped.map(([k, n]) => (
              <span key={k} className="tag">
                {formatInt(n)} {label(SKIP_LABEL, k)}
              </span>
            ))}
          </span>
        )}
      </div>
      {result.shortlist.length === 0 ? (
        <Empty title="Nothing matches today">The screen ran on {result.asOf} and no share passed it.</Empty>
      ) : (
        <ol aria-label="Shortlist" className="m-0 grid list-none gap-1.5 p-0">
          {result.shortlist.map((h, i) => (
            <li
              key={h.symbol}
              className="grid gap-1 rounded-xl border border-line bg-s2/40 px-3 py-2 md:grid-cols-[28px_minmax(140px,220px)_1fr]"
            >
              <span className="num text-tx3">{i + 1}</span>
              <div className="min-w-0">
                <Link
                  to="/dossier"
                  search={{ symbol: h.symbol }}
                  className="num font-semibold text-tx hover:text-a"
                >
                  {h.symbol}
                </Link>
                <div className="truncate text-[11px] text-tx3" title={h.name}>
                  {h.name}
                </div>
              </div>
              <div className="min-w-0">
                <div className="text-xs text-tx2">{h.reason}</div>
                <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-tx3">
                  {Object.entries(h.metrics).map(([k, v]) => {
                    const m = metricLabel(k);
                    return (
                      <span key={k}>
                        {m.text} <span className="num text-tx2">{metricText(m.kind, v)}</span>
                      </span>
                    );
                  })}
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
      <p className="m-0 text-[11px] text-tx3">
        A shortlist is research, not a recommendation. Nothing here places an order; a human approves every
        paper trade.
      </p>
    </div>
  );
}
