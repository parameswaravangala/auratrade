import type { CalendarCoverage, Freshness, FreshnessLag } from "@/api/schemas";
import { useCalendarCoverage, useFreshness } from "@/api/queries";
import { Panel } from "@/components/Panel";
import { Empty, ErrorState, Loading } from "@/components/States";

/** How far behind a share or index is, in trading sessions. Null means the data needed to say is missing. */
function behindLabel(n: number | null): string {
  if (n === null) return "unknown";
  if (n === 0) return "current";
  return n === 1 ? "1 session behind" : `${n} sessions behind`;
}

/** Data freshness and the trading calendar's coverage, from the Java API (ADR 0026). */
export function DataCalendar() {
  const fresh = useFreshness();
  const cov = useCalendarCoverage();
  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <FreshnessPanel query={fresh} />
      <CoveragePanel query={cov} />
    </div>
  );
}

type Query<T> = { data: T | undefined; error: unknown; isPending: boolean; refetch: () => void };

function FreshnessPanel({ query }: { query: Query<Freshness> }) {
  const { data, error, isPending, refetch } = query;
  return (
    <Panel title="Data freshness" badge={data && <span className="tag tag-b">{data.source}</span>}>
      <div className="grid gap-4 p-4">
        {isPending && <Loading label="Checking data freshness" />}
        {error != null && <ErrorState error={error} onRetry={refetch} />}
        {data && (
          <>
            <dl className="m-0 grid grid-cols-2 gap-3 text-sm">
              <dt className="text-tx3">Expected session</dt>
              <dd className="m-0 num text-tx" aria-label="expected session">
                {data.expectedSession ?? "unknown"}
              </dd>
              <dt className="text-tx3">Latest session</dt>
              <dd className="m-0 num text-tx" aria-label="latest session">
                {data.latestSession ?? "none"}
                {data.sessionsBehind !== null && ` · ${behindLabel(data.sessionsBehind)}`}
              </dd>
              <dt className="text-tx3">EOD cutoff</dt>
              <dd className="m-0 num text-tx">{data.eodCutoff} IST</dd>
            </dl>

            <div>
              <h3 className="m-0 mb-2 text-xs tracking-wide text-tx3 uppercase">
                Shares ({data.shares.total})
              </h3>
              <p className="m-0 text-sm text-tx2" aria-label="share freshness">
                {data.shares.current} current · {data.shares.behind["1"] ?? 0} one behind ·{" "}
                {data.shares.behind["2-5"] ?? 0} two to five · {data.shares.behind["6+"] ?? 0} six or more ·{" "}
                {data.shares.noData} with no data
              </p>
            </div>

            {data.shares.mostBehind.length > 0 && (
              <LagTable title="Most behind" rows={data.shares.mostBehind} />
            )}
            {data.benchmarks.length > 0 && <LagTable title="Benchmarks" rows={data.benchmarks} />}
            {data.otherSources.length > 0 && (
              <div>
                <h3 className="m-0 mb-2 text-xs tracking-wide text-tx3 uppercase">Other sources</h3>
                <ul className="m-0 grid list-none gap-1 p-0 text-sm text-tx2">
                  {data.otherSources.map((s) => (
                    <li key={s.source}>
                      {s.source}: {s.latestSession ?? "no data"}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </>
        )}
      </div>
    </Panel>
  );
}

function LagTable({ title, rows }: { title: string; rows: FreshnessLag[] }) {
  return (
    <div>
      <h3 className="m-0 mb-2 text-xs tracking-wide text-tx3 uppercase">{title}</h3>
      <table className="w-full text-sm">
        <thead className="text-left text-tx3">
          <tr>
            <th className="pb-1 font-normal">Symbol</th>
            <th className="pb-1 font-normal">Latest</th>
            <th className="pb-1 font-normal">Lag</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.symbol} className="text-tx2">
              <td className="py-0.5 num">{r.symbol}</td>
              <td className="py-0.5 num">{r.latestSession ?? "none"}</td>
              <td className="py-0.5">{behindLabel(r.sessionsBehind)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CoveragePanel({ query }: { query: Query<CalendarCoverage> }) {
  const { data, error, isPending, refetch } = query;
  return (
    <Panel
      title="Trading calendar"
      badge={
        data && (
          <span className="tag tag-b">
            {data.exchange} · {data.timezone}
          </span>
        )
      }
    >
      <div className="grid gap-4 p-4">
        {isPending && <Loading label="Reading the calendar" />}
        {error != null && <ErrorState error={error} onRetry={refetch} />}
        {data && (
          <>
            {data.coverageWarning && (
              <p role="alert" className="m-0 rounded border border-warn/40 bg-warn/10 p-2 text-sm text-tx">
                {data.coverageWarning}
              </p>
            )}
            <dl className="m-0 grid grid-cols-2 gap-3 text-sm">
              <dt className="text-tx3">Covered years</dt>
              <dd className="m-0 num text-tx">
                {data.coveredYears.length ? data.coveredYears.join(", ") : "none"}
              </dd>
              <dt className="text-tx3">Covered dates</dt>
              <dd className="m-0 num text-tx">
                {data.firstDate ?? "n/a"} to {data.lastDate ?? "n/a"}
              </dd>
              <dt className="text-tx3">Today</dt>
              <dd className="m-0 num text-tx">
                {data.today}
                {!data.todayCovered && " · not covered"}
              </dd>
              <dt className="text-tx3">Last closed session</dt>
              <dd className="m-0 num text-tx">{data.lastClosedSession ?? "unknown"}</dd>
              <dt className="text-tx3">Next session</dt>
              <dd className="m-0 num text-tx">{data.nextSession ?? "unknown"}</dd>
              <dt className="text-tx3">Next year covered</dt>
              <dd className="m-0 text-tx">{data.nextYearCovered ? "yes" : "no"}</dd>
            </dl>

            {data.years.length === 0 ? (
              <Empty title="No covered years" />
            ) : (
              <ul className="m-0 grid list-none gap-2 p-0">
                {data.years.map((y) => (
                  <li key={y.year} className="rounded border border-line p-2 text-sm">
                    <div className="flex items-baseline justify-between">
                      <span className="num text-tx">{y.year}</span>
                      <span className="text-tx2">{y.tradingDays} trading days</span>
                    </div>
                    <details className="mt-1">
                      <summary className="cursor-pointer text-tx3">
                        {y.holidays.length} holidays, {y.specialSessions.length} special sessions
                      </summary>
                      <ul className="m-0 mt-1 grid list-none gap-0.5 p-0 text-tx2">
                        {[...y.holidays, ...y.specialSessions]
                          .sort((a, b) => a.date.localeCompare(b.date))
                          .map((d) => (
                            <li key={`${d.date}-${d.name}`}>
                              <span className="num">{d.date}</span> {d.name}
                            </li>
                          ))}
                      </ul>
                    </details>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </Panel>
  );
}
