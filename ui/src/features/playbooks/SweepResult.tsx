import clsx from "clsx";
import type { SweepReport, SweepRow, SweepWindow } from "@/api/schemas";
import { formatSigned } from "@/lib/format";
import { beats, isChosen, sweepGrid } from "@/lib/playbook";
import { Caveats } from "./BacktestResult";

const signed = (n: number | null) => (n === null ? "—" : formatSigned(n, 1));
const plain = (n: number | null) => (n === null ? "—" : n.toFixed(2));

/**
 * The sweep as two grids (regime filter off and on), stop multiple by holding limit. Each cell shows
 * out-of-sample CAGR, tinted by whether it beat the benchmark; the in-sample pick is outlined.
 */
export function SweepResult({ report }: { report: SweepReport }) {
  const grid = sweepGrid(report);
  const c = report.chosen;
  const benchOut = c.outOfSample.benchmarkCagrPct;
  const benchIn = c.inSample.benchmarkCagrPct;

  return (
    <div className="grid gap-4 px-4 pt-2 pb-4">
      <div className="flex flex-wrap items-baseline gap-x-6 gap-y-2">
        <div aria-label="Settings beating Nifty out of sample">
          <b className="num text-3xl font-medium text-tx">{report.combinationsBeatingBenchmarkOutOfSample}</b>
          <span className="num text-tx3"> / {report.combinations}</span>
          <div className="text-xs text-tx2">settings beat Nifty 50 after the split</div>
        </div>
        <p className="m-0 max-w-xl flex-1 text-xs text-tx2">
          In-sample {report.from} up to {report.split} (Nifty CAGR {signed(benchIn)}), out-of-sample{" "}
          {report.split} to {report.to} (Nifty CAGR {signed(benchOut)}). Most settings beating Nifty after the
          split suggests the edge is not one lucky parameter choice; few suggests overfitting.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {[false, true].map((regime) => (
          <div key={String(regime)}>
            <h3 className="mt-0 mb-2 text-[10px] font-semibold tracking-[.12em] text-tx3 uppercase">
              Regime filter {regime ? "on" : "off"}
            </h3>
            <table
              className="w-full border-separate border-spacing-1 text-xs"
              aria-label={`Regime filter ${regime ? "on" : "off"}`}
            >
              <thead>
                <tr className="text-[11px] text-tx3">
                  <th className="text-left font-normal">stop \ hold</th>
                  {grid.holds.map((h) => (
                    <th key={h} className="num font-normal">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {grid.stops.map((stop) => (
                  <tr key={stop}>
                    <th className="num text-left font-normal text-tx3">{stop}×</th>
                    {grid.holds.map((hold) => {
                      const row = grid.cell(stop, hold, regime);
                      return (
                        <td key={hold} className="p-0">
                          {row ? <Cell row={row} chosen={isChosen(report, row)} /> : "—"}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      </div>

      <div
        className="grid gap-2 rounded-xl border border-a/40 bg-a/5 px-3 py-2 text-xs"
        aria-label="Chosen setting"
      >
        <div className="text-tx2">
          <span className="tag tag-a mr-2">chosen in-sample</span>
          stop {c.atrMultiple} × ATR, hold {c.maxHold} sessions, regime filter {c.regimeFilter ? "on" : "off"}
        </div>
        <div className="grid grid-cols-2 gap-2">
          <WindowLine name="Before the split" w={c.inSample} />
          <WindowLine name="After the split" w={c.outOfSample} />
        </div>
        <div className="text-[11px] text-tx3">
          Fixed settings: {report.fixedSettings} · computed in {report.seconds} s
        </div>
      </div>

      {report.caveats.length > 0 && <Caveats items={report.caveats} />}
    </div>
  );
}

function Cell({ row, chosen }: { row: SweepRow; chosen: boolean }) {
  const won = beats(row.outOfSample);
  const out = row.outOfSample;
  return (
    <div
      data-beats={won === null ? "unknown" : String(won)}
      title={`In-sample CAGR ${signed(row.inSample.cagrPct)}, Sharpe ${plain(row.inSample.sharpe)}; out-of-sample drawdown ${signed(out.maxDrawdownPct)}, Sharpe ${plain(out.sharpe)}, ${String(out.trades)} trades`}
      className={clsx(
        "rounded-lg border px-2 py-1.5 text-center",
        won === true && "border-up/30 bg-up/10",
        won === false && "border-dn/25 bg-dn/5",
        won === null && "border-line",
        chosen && "outline-2 outline-offset-1 outline-a",
      )}
    >
      <div className={clsx("num font-medium", won ? "text-up" : "text-tx2")}>{signed(out.cagrPct)}</div>
      <div className="num text-[10px] text-tx3">in {signed(row.inSample.cagrPct)}</div>
    </div>
  );
}

function WindowLine({ name, w }: { name: string; w: SweepWindow }) {
  return (
    <div>
      <div className="text-[10px] tracking-[.1em] text-tx3 uppercase">{name}</div>
      <div className="num text-tx">
        CAGR {signed(w.cagrPct)} · DD {signed(w.maxDrawdownPct)} · Sharpe {plain(w.sharpe)} · {w.trades}{" "}
        trades
      </div>
      <div className="num text-[11px] text-tx3">
        Nifty CAGR {signed(w.benchmarkCagrPct)} · DD {signed(w.benchmarkMaxDrawdownPct)}
      </div>
    </div>
  );
}
