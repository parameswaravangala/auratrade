import { Link } from "@tanstack/react-router";
import clsx from "clsx";
import { Search } from "lucide-react";
import { useState } from "react";
import { useResolve } from "@/api/queries";
import { ErrorState, Loading } from "@/components/States";
import { STAGE_HINT, trace } from "@/lib/resolution";

/** Resolves a company name and shows how the resolver decided (alias, symbol, name, fuzzy). */
export function ResolverPanel({ initial = "" }: { initial?: string }) {
  const [draft, setDraft] = useState(initial);
  const [name, setName] = useState(initial);
  const q = useResolve(name);

  return (
    <div className="flex flex-col">
      <form
        className="mx-4 mt-3 flex h-11 items-center gap-2.5 rounded-xl border border-a/40 bg-s2 px-3.5 shadow-[0_0_0_4px_color-mix(in_srgb,var(--a)_7%,transparent)]"
        onSubmit={(e) => {
          e.preventDefault();
          setName(draft);
        }}
      >
        <Search size={18} className="text-tx3" />
        <input
          aria-label="Company name or symbol"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
          }}
          placeholder="Company name or symbol, then Enter"
          className="flex-1 border-0 bg-transparent text-[15px] text-tx outline-none placeholder:text-tx3"
        />
      </form>

      {!name.trim() && (
        <p className="m-0 px-4 py-3 text-xs text-tx3">
          Try a company name as it appears in news. The resolver never guesses between close matches.
        </p>
      )}
      {q.isFetching && !q.data && <Loading label="Resolving" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}

      {q.data && (
        <>
          <ol className="m-0 grid list-none gap-2 px-4 pt-3.5 pb-1" aria-label="Resolver stages">
            {trace(q.data).map((s) => (
              <li
                key={s.kind}
                data-state={s.state}
                className={clsx(
                  "grid grid-cols-[22px_64px_1fr] items-center gap-2 rounded-[10px] border px-2.5 py-2",
                  s.state === "hit" ? "border-a/40 bg-a/5 text-tx" : "border-line text-tx3",
                  s.state === "skip" && "opacity-45",
                )}
              >
                <span
                  className={clsx(
                    "size-3 justify-self-center rounded-full border-2",
                    s.state === "hit" ? "border-a bg-a shadow-[0_0_10px_var(--a)]" : "border-line2",
                  )}
                />
                <span className="num text-[11px] font-semibold tracking-wide">{s.kind}</span>
                <span className="text-xs">
                  {s.state === "hit"
                    ? "matched"
                    : s.state === "miss"
                      ? "no match"
                      : s.state === "skip"
                        ? "not needed"
                        : STAGE_HINT[s.kind]}
                </span>
              </li>
            ))}
          </ol>
          <Verdict r={q.data} />
        </>
      )}
    </div>
  );
}

function Verdict({ r }: { r: NonNullable<ReturnType<typeof useResolve>["data"]> }) {
  const box = "mx-4 mt-2.5 mb-4 rounded-xl border border-line2 p-3.5";
  if (r.type === "RESOLVED") {
    return (
      <div
        className={clsx(
          box,
          "bg-[linear-gradient(135deg,color-mix(in_srgb,var(--a)_12%,transparent),color-mix(in_srgb,var(--b)_10%,transparent))]",
        )}
      >
        <div className="flex items-center gap-2">
          <span className="num text-[22px] font-medium">{r.share.tradingSymbol}</span>
          <span className="tag tag-a">RESOLVED</span>
          <span className="tag">{r.share.series}</span>
          <span className="tag">
            {r.kind} · {r.score.toFixed(2)}
          </span>
        </div>
        <div className="mt-0.5 text-tx2">
          {r.share.name} · instrument #{r.share.instrumentId}
        </div>
        <Link to="/dossier" search={{ symbol: r.share.tradingSymbol }} className="btn mt-3 no-underline">
          Open dossier
        </Link>
      </div>
    );
  }
  if (r.type === "AMBIGUOUS") {
    return (
      <div className={box}>
        <div className="mb-2 flex items-center gap-2">
          <span className="tag tag-w">AMBIGUOUS</span>
          <span className="text-xs text-tx2">
            {r.candidates.length} shares fit about equally; none picked.
          </span>
        </div>
        <ul className="m-0 grid list-none gap-1.5 p-0">
          {r.candidates.map((c) => (
            <li key={c.instrumentId} className="flex items-center gap-2">
              <Link to="/dossier" search={{ symbol: c.tradingSymbol }} className="num text-a no-underline">
                {c.tradingSymbol}
              </Link>
              <span className="truncate text-tx2">{c.name}</span>
              <span className="tag ml-auto">{c.series}</span>
            </li>
          ))}
        </ul>
      </div>
    );
  }
  return (
    <div className={box}>
      <span className="tag tag-r">UNRESOLVED</span>
      <div className="mt-2 text-tx2">{r.reason}</div>
    </div>
  );
}
