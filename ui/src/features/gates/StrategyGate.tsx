import { useState } from "react";
import { useGate } from "@/api/queries";
import { Panel } from "@/components/Panel";
import { ErrorState, Loading } from "@/components/States";

/** The strategies with a gate grid on the backend (ADR 0045, 0046). */
const GATE_STRATEGIES: { id: string; name: string }[] = [
  { id: "momentum-breakout", name: "Momentum breakout" },
  { id: "pullback-20ema", name: "20 EMA pullback" },
  { id: "relative-strength", name: "Relative strength" },
  { id: "high-52w-momentum", name: "52-week high momentum" },
];

/** The pre-registered gate (ADR 0042). Each run replays the history, so it runs only on request. */
export function StrategyGate() {
  const [strategy, setStrategy] = useState<string | null>(null);
  const gate = useGate(strategy);
  const { data, error, isFetching, refetch } = gate;
  return (
    <div className="grid gap-4">
      <Panel title="Strategy gate" badge={<span className="tag">pre-registered</span>}>
        <div className="grid gap-3 p-4 text-sm text-tx2">
          <p className="m-0">
            Each strategy is judged once on the history before 2023, then out of sample. A strategy passes
            only if every check passes. A run replays the history and takes several minutes.
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2">
              <span className="text-tx3">Strategy</span>
              <select
                aria-label="strategy"
                value={strategy ?? ""}
                onChange={(e) => {
                  setStrategy(e.target.value || null);
                }}
                className="rounded border border-line bg-transparent px-2 py-1 text-tx"
              >
                <option value="">Choose a strategy</option>
                {GATE_STRATEGIES.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              disabled={!strategy || isFetching}
              onClick={() => {
                void refetch();
              }}
              className="rounded border border-line px-3 py-1 text-tx hover:bg-white/5 disabled:opacity-50"
            >
              {isFetching ? "Running the gate…" : "Run the gate"}
            </button>
          </div>
          {isFetching && <Loading label="Replaying the history for every setting" />}
          {error != null && (
            <ErrorState
              error={error}
              onRetry={() => {
                void refetch();
              }}
            />
          )}
        </div>
      </Panel>

      {data && (
        <Panel
          title={`${data.strategy} · ${data.passed ? "passed" : "not passed"}`}
          badge={
            <span className={`tag ${data.passed ? "tag-g" : "tag-r"}`}>
              {data.passed ? "approved" : "not approved"}
            </span>
          }
        >
          <div className="grid gap-4 p-4 text-sm">
            <p className="m-0 text-tx2">
              {data.candidateShares} candidate shares, {data.settingCount} settings, out of sample from{" "}
              {data.oosStart} to {data.to}, against {data.benchmark}. Run time {Math.round(data.seconds)} s.
            </p>
            <table className="w-full">
              <thead className="text-left text-tx3">
                <tr>
                  <th className="pb-1 font-normal">Check</th>
                  <th className="pb-1 font-normal">Value</th>
                  <th className="pb-1 font-normal">Needed</th>
                  <th className="pb-1 font-normal">Result</th>
                </tr>
              </thead>
              <tbody>
                {data.checks.map((c) => (
                  <tr key={c.name} className="border-t border-line text-tx2">
                    <td className="py-1">{c.name}</td>
                    <td className="num">{c.value}</td>
                    <td className="num">{c.threshold}</td>
                    <td className={c.passed ? "text-up" : "text-tx3"}>{c.passed ? "pass" : "fail"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <dl className="m-0 grid grid-cols-2 gap-2 sm:grid-cols-4">
              <dt className="text-tx3">Chosen setting</dt>
              <dd className="m-0 num text-tx sm:col-span-3">{data.chosenSetting}</dd>
              <dt className="text-tx3">Beating Nifty</dt>
              <dd className="m-0 num text-tx">
                {data.settingsBeatingNifty} of {data.settingCount}
              </dd>
              <dt className="text-tx3">Out-of-sample trades</dt>
              <dd className="m-0 num text-tx">{data.outOfSample.trades}</dd>
              <dt className="text-tx3">Profit factor</dt>
              <dd className="m-0 num text-tx">{data.outOfSample.profitFactor ?? "no losses"}</dd>
            </dl>
            <ul className="m-0 grid list-disc gap-1 pl-5 text-xs text-tx3">
              {data.caveats.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </ul>
          </div>
        </Panel>
      )}
    </div>
  );
}
