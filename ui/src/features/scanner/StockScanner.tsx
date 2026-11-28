import { useState } from "react";
import type { SelectionCandidate, SelectionEvaluation, SelectionView } from "@/api/schemas";
import { useSelection } from "@/api/queries";
import { Panel } from "@/components/Panel";
import { Empty, ErrorState, Loading } from "@/components/States";

/** The stock shortlist (plan P1, ADR 0044). Only strategies whose gate has passed count. */
export function StockScanner() {
  const { data, error, isPending, refetch } = useSelection(10);
  const [picked, setPicked] = useState<string | null>(null);
  return (
    <div className="grid gap-4">
      <Panel title="Stock scanner" badge={data && <span className="tag tag-b">{data.source}</span>}>
        <div className="grid gap-3 p-4">
          {isPending && <Loading label="Scanning the share list" />}
          {error != null && (
            <ErrorState
              error={error}
              onRetry={() => {
                void refetch();
              }}
            />
          )}
          {data && <Summary data={data} />}
        </div>
      </Panel>
      {data && (
        <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
          <Panel title="Shortlist" badge={<span className="tag">{data.shortlist.length} shown</span>}>
            <CandidateList
              rows={data.shortlist}
              empty={
                data.approvedStrategies.length === 0
                  ? "No strategy has passed its gate, so the shortlist is empty by design."
                  : "No approved strategy passed on this session."
              }
              picked={picked}
              onPick={setPicked}
            />
          </Panel>
          <Panel
            title="Almost qualified"
            badge={<span className="tag">{data.almostQualified.length} shown</span>}
          >
            <CandidateList
              rows={data.almostQualified}
              empty="No share missed by exactly one condition."
              picked={picked}
              onPick={setPicked}
            />
          </Panel>
          <Detail data={data} picked={picked} />
        </div>
      )}
    </div>
  );
}

function Summary({ data }: { data: SelectionView }) {
  const approved = data.approvedStrategies;
  return (
    <>
      <dl className="m-0 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <dt className="text-tx3">Session</dt>
        <dd className="m-0 num text-tx" aria-label="session">
          {data.asOf}
        </dd>
        <dt className="text-tx3">Shares screened</dt>
        <dd className="m-0 num text-tx" aria-label="evaluated">
          {data.evaluated}
        </dd>
        <dt className="text-tx3">Passing an approved strategy</dt>
        <dd className="m-0 num text-tx" aria-label="passing">
          {data.passing}
        </dd>
        <dt className="text-tx3">Market regime (NIFTY 50)</dt>
        <dd className="m-0 text-tx" aria-label="market regime">
          {data.regime ? `${data.regime.regime.toLowerCase()} · ${data.regime.evidence}` : "not available"}
        </dd>
        <dt className="text-tx3">Scoring profile</dt>
        <dd className="m-0 text-tx" aria-label="scoring profile">
          {data.scoringProfile.name} · {data.scoringProfile.policy.replace("_", " ").toLowerCase()} ·{" "}
          {Object.entries(data.scoringProfile.weights)
            .map(([strategy, weight]) => `${strategy} ${weight}`)
            .join(", ")}
        </dd>
        <dt className="text-tx3">Approved strategies</dt>
        <dd className="m-0 text-tx" aria-label="approved strategies">
          {approved.length ? approved.join(", ") : "none yet"}
        </dd>
      </dl>
      {approved.length === 0 && (
        <p role="note" className="m-0 rounded border border-line p-2 text-sm text-tx2">
          No strategy has passed its gate yet (ADR 0042). Until one does, nothing is shortlisted. The passing
          counts show what the strategies would say, not a recommendation.
        </p>
      )}
      {Object.keys(data.skipped).length > 0 && (
        <p className="m-0 text-xs text-tx3">
          Not screened:{" "}
          {Object.entries(data.skipped)
            .map(([reason, n]) => `${n} ${reason.replaceAll("_", " ").toLowerCase()}`)
            .join(", ")}
        </p>
      )}
    </>
  );
}

function CandidateList({
  rows,
  empty,
  picked,
  onPick,
}: {
  rows: SelectionCandidate[];
  empty: string;
  picked: string | null;
  onPick: (symbol: string) => void;
}) {
  if (rows.length === 0) return <Empty title={empty} />;
  return (
    <ol className="m-0 grid list-none gap-1 p-2" aria-label="candidates">
      {rows.map((c, i) => (
        <li key={c.symbol}>
          <button
            type="button"
            onClick={() => {
              onPick(c.symbol);
            }}
            aria-pressed={picked === c.symbol}
            className={`flex w-full items-center justify-between rounded px-2 py-1.5 text-left text-sm ${
              picked === c.symbol ? "bg-a/10 text-tx" : "text-tx2 hover:bg-white/5"
            }`}
          >
            <span>
              <span className="num text-tx3">{i + 1}.</span> <span className="num text-tx">{c.symbol}</span>{" "}
              <span className="text-tx3">{c.name}</span>
            </span>
            <span className="num" title="combined score">
              {c.passedStrategies.length ? c.combinedScore.toFixed(1) : "–"}
            </span>
          </button>
        </li>
      ))}
    </ol>
  );
}

function Detail({ data, picked }: { data: SelectionView; picked: string | null }) {
  const all = [...data.shortlist, ...data.almostQualified];
  const candidate = all.find((c) => c.symbol === picked);
  return (
    <Panel
      title={candidate ? `${candidate.symbol} · every strategy` : "Choose a share"}
      className="xl:col-span-2"
    >
      <div className="grid gap-3 p-4">
        {!candidate && (
          <p className="m-0 text-sm text-tx3">Select a share to see each strategy's conditions and values.</p>
        )}
        {candidate?.evaluations.map((e) => (
          <EvaluationView key={e.strategy} evaluation={e} />
        ))}
      </div>
    </Panel>
  );
}

function EvaluationView({ evaluation: e }: { evaluation: SelectionEvaluation }) {
  return (
    <section className="rounded border border-line p-3 text-sm" aria-label={`strategy ${e.strategy}`}>
      <div className="flex items-baseline justify-between gap-2">
        <span className="num text-tx">{e.strategy}</span>
        <span className="tag">{e.status.replaceAll("_", " ").toLowerCase()}</span>
      </div>
      <p className="m-0 mt-1 text-tx2">{e.explanation}</p>
      <ul className="m-0 mt-2 grid list-none gap-1 p-0">
        {e.conditions.map((c) => (
          <li key={c.id} className="flex justify-between gap-2 text-tx2">
            <span>
              {c.description}
              {!c.required && <span className="text-tx3"> (optional)</span>}
            </span>
            <span className="num">
              {c.outcome.toLowerCase()}
              {c.value !== null && ` · ${c.value}`} · {c.threshold}
            </span>
          </li>
        ))}
      </ul>
      {e.plan && (
        <dl className="m-0 mt-2 grid grid-cols-2 gap-1 text-tx2">
          <dt className="text-tx3">Entry</dt>
          <dd className="m-0 num">
            {e.plan.entryType.replace("_", " ").toLowerCase()} at {e.plan.trigger}, no more than{" "}
            {e.plan.maxPrice}
          </dd>
          <dt className="text-tx3">Stop</dt>
          <dd className="m-0 num">
            {e.plan.stop} ({e.plan.stopReason})
          </dd>
          <dt className="text-tx3">Risk per share</dt>
          <dd className="m-0 num">{e.plan.riskPerShare}</dd>
          <dt className="text-tx3">Targets</dt>
          <dd className="m-0 num">
            {e.plan.targets.map((t) => `${t.rMultiple}R at ${t.price} (${t.fraction * 100}%)`).join(", ") ||
              "none"}
          </dd>
          <dt className="text-tx3">Holding limit</dt>
          <dd className="m-0 num">{e.plan.maxHoldSessions} sessions</dd>
        </dl>
      )}
    </section>
  );
}
