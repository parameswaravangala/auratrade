import { getRouteApi, useNavigate } from "@tanstack/react-router";
import clsx from "clsx";
import { Download, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { useCandles, useLoadCandles } from "@/api/queries";
import { Empty, ErrorState, Loading } from "@/components/States";
import {
  calendarDaysBehind,
  rangeDates,
  RANGES,
  summarise,
  toChartBars,
  type ChartBar,
  type RangeKey,
} from "@/lib/candles";
import { formatCompact, formatInt, formatPrice, formatSigned } from "@/lib/format";
import { CandleChart } from "./CandleChart";
import { Copilot } from "./Copilot";

const route = getRouteApi("/dossier");
const LENSES = ["Price", "Trend", "Volatility", "Volume profile", "Events"] as const;

export function Dossier() {
  const { symbol, range = "6M" } = route.useSearch();
  const navigate = useNavigate();
  const [now] = useState(() => new Date());
  const { from, to } = rangeDates(range, now);
  const q = useCandles(symbol ?? "", from, to);
  const bars = useMemo(() => toChartBars(q.data ?? []), [q.data]);
  const sum = summarise(bars);
  const [hover, setHover] = useState<ChartBar | null>(null);
  const shown = hover ?? bars.at(-1) ?? null;

  const setSearch = (s: { symbol?: string; range?: RangeKey }) =>
    void navigate({ to: "/dossier", search: { symbol, range, ...s } });

  return (
    <div className="grid h-[calc(100vh-56px-42px)] min-h-[560px] gap-4 xl:grid-cols-[1fr_360px]">
      <section className="glass panel-in grid min-h-0 grid-rows-[auto_1fr_auto]" aria-label="Chart">
        <div>
          <div className="flex flex-wrap items-end gap-3.5 px-[18px] pt-4 pb-1.5">
            <SymbolBox
              key={symbol ?? ""}
              value={symbol ?? ""}
              onSubmit={(s) => {
                setSearch({ symbol: s });
              }}
            />
            <span className="tag self-center">NSE · DAY</span>
            {sum && (
              <div className="ml-auto text-right">
                <b className="num block text-[26px] leading-none font-medium">{formatPrice(sum.close)}</b>
                {sum.changePct !== null && (
                  <span className={clsx("num", sum.changePct >= 0 ? "text-up" : "text-dn")}>
                    {formatSigned(sum.changePct)}
                  </span>
                )}
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-1.5 px-[18px] pt-1.5">
            {LENSES.map((l) => (
              <button
                key={l}
                type="button"
                disabled={l !== "Price"}
                title={l === "Price" ? undefined : "Needs indicators computed in Java (/api/v1/indicators)"}
                className={clsx(
                  "rounded-full border px-2.5 py-1 text-xs",
                  l === "Price" ? "border-a/45 bg-a/10 text-a" : "cursor-not-allowed border-line text-tx3",
                )}
              >
                {l}
              </button>
            ))}
            <span className="flex-1" />
            {(Object.keys(RANGES) as RangeKey[]).map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={r === range}
                onClick={() => {
                  setSearch({ range: r });
                }}
                className={clsx(
                  "num cursor-pointer rounded-full border px-2.5 py-1 text-xs",
                  r === range ? "border-a/45 bg-a/10 text-a" : "border-line text-tx2",
                )}
              >
                {r}
              </button>
            ))}
          </div>
        </div>

        <div className="relative mx-2 mt-1 min-h-0">
          {!symbol ? (
            <Empty title="Pick a share">
              Type an NSE trading symbol above, or press <kbd className="num">Ctrl K</kbd> and resolve a
              company name.
            </Empty>
          ) : q.isPending ? (
            <Loading label="Loading candles" />
          ) : q.isError ? (
            <ErrorState error={q.error} onRetry={() => void q.refetch()} />
          ) : bars.length === 0 ? (
            <NoCandles symbol={symbol} from={from} to={to} />
          ) : (
            <CandleChart bars={bars} onHover={setHover} />
          )}
        </div>

        <dl className="m-0 grid grid-cols-[110px_1fr] items-center gap-x-2.5 gap-y-1.5 border-t border-line px-[18px] pt-2.5 pb-4 text-[11.5px] text-tx2">
          <dt>Bar</dt>
          <dd className="num m-0">
            {shown
              ? `${shown.time} · O ${formatPrice(shown.open)} · H ${formatPrice(shown.high)} · L ${formatPrice(shown.low)} · C ${formatPrice(shown.close)} · V ${formatCompact(shown.volume)}`
              : "—"}
          </dd>
          <dt>Stored</dt>
          <dd className="m-0">
            {sum
              ? `${formatInt(sum.count)} day bars, ${sum.first} to ${sum.last} · validated before storing`
              : `none for ${from} to ${to}`}
          </dd>
          <dt>Freshness</dt>
          <dd className="m-0">
            {sum ? (
              <>
                newest bar {sum.last} · {daysAgo(calendarDaysBehind(sum.last, now))}{" "}
                <span className="text-tx3">(trading-day check needs /api/v1)</span>
              </>
            ) : (
              "—"
            )}
          </dd>
        </dl>
      </section>
      <Copilot symbol={symbol} />
    </div>
  );
}

function SymbolBox({ value, onSubmit }: { value: string; onSubmit: (s: string) => void }) {
  const [draft, setDraft] = useState(value);
  return (
    <form
      className="flex items-center gap-2 border-b border-line2 pb-1"
      onSubmit={(e) => {
        e.preventDefault();
        const s = draft.trim().toUpperCase();
        if (s) onSubmit(s);
      }}
    >
      <Search size={16} className="text-tx3" />
      <input
        aria-label="Trading symbol"
        value={draft}
        onChange={(e) => {
          setDraft(e.target.value.toUpperCase());
        }}
        placeholder="SYMBOL"
        className="num w-44 border-0 bg-transparent text-[26px] leading-none font-medium text-tx outline-none placeholder:text-tx3"
      />
    </form>
  );
}

function NoCandles({ symbol, from, to }: { symbol: string; from: string; to: string }) {
  const load = useLoadCandles();
  return (
    <Empty title={`No stored candles for ${symbol}`}>
      <p className="m-0 max-w-md text-xs">
        Nothing between {from} and {to}. Candles arrive from the nightly eod-candles job, or you can ask the
        provider now. The backend validates them and stores nothing if any bar fails.
      </p>
      <button
        type="button"
        className="btn btn-pri mt-2"
        disabled={load.isPending}
        onClick={() => {
          toast.promise(load.mutateAsync({ symbol, from, to }), {
            loading: `Loading ${symbol} from the provider…`,
            success: (r) =>
              `${r.symbol}: ${r.stored} of ${r.fetched} bars stored from ${r.source}${r.issues.length ? `, ${r.issues.length} issues` : ""}`,
            error: (e: unknown) => (e instanceof Error ? e.message : "Load failed"),
          });
        }}
      >
        <Download size={14} /> Load from provider{" "}
        <span className="tag ml-1 border-[#04060c33] text-[#04060c]">dev</span>
      </button>
    </Empty>
  );
}

function daysAgo(n: number): string {
  return n === 0 ? "today" : n === 1 ? "1 calendar day ago" : `${n} calendar days ago`;
}
