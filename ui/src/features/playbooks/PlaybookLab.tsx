import { useQueryClient } from "@tanstack/react-query";
import { getRouteApi, useNavigate } from "@tanstack/react-router";
import clsx from "clsx";
import { FlaskConical, Grid3x3 } from "lucide-react";
import { useState, type ReactNode } from "react";
import {
  lastRun,
  usePortfolio,
  useScreens,
  useShortlist,
  useSweep,
  type PortfolioParams,
  type SweepParams,
} from "@/api/queries";
import type { ScreenInfo } from "@/api/schemas";
import { Panel } from "@/components/Panel";
import { ErrorState, Loading } from "@/components/States";
import { HOLD_LIMITS, screenTitle, STOP_MULTIPLES } from "@/lib/playbook";
import { BacktestResult } from "./BacktestResult";
import { RunState } from "./RunState";
import { Shortlist } from "./Shortlist";
import { SweepResult } from "./SweepResult";

const route = getRouteApi("/playbooks");

/** Defaults from ADR 0022 (simulation) and ADR 0023 (sweep window). */
const DEFAULTS = { from: "2019-01-01", split: "2023-01-01", atrMultiple: 2, maxHold: 60, riskPct: 1 };

/** `to` is "" for the newest stored session. */
type Settings = Omit<PortfolioParams, "screen"> & { split: string };

/**
 * The Playbook lab: a screen's rules, today's shortlist, a portfolio backtest and a robustness sweep.
 * The rules and every number run in Java; this page chooses settings and shows results. Nothing here
 * can place an order (ADR 0005).
 */
export function PlaybookLab() {
  const { screen: wanted } = route.useSearch();
  const navigate = useNavigate();
  const screens = useScreens();
  const list = screens.data ?? [];
  const current = list.find((s) => s.name === wanted) ?? list[0];
  const shortlist = useShortlist(current?.name);

  const [settings, setSettings] = useState<Settings>({
    from: DEFAULTS.from,
    to: "",
    split: DEFAULTS.split,
    atrMultiple: DEFAULTS.atrMultiple,
    maxHold: DEFAULTS.maxHold,
    regime: false,
    riskPct: DEFAULTS.riskPct,
    maxPositions: 10,
    costs: true,
  });
  // Backtests end on the newest stored session (the shortlist's as-of date) unless a date is set.
  const newest = shortlist.data?.asOf;
  const to = settings.to || newest;

  // The run shown per screen: what was asked here, else the newest cached run for that screen.
  const qc = useQueryClient();
  const [asked, setAsked] = useState<{
    portfolio: Record<string, PortfolioParams>;
    sweep: Record<string, SweepParams>;
  }>({ portfolio: {}, sweep: {} });
  const name = current?.name ?? "";
  const portfolioParams = current ? (asked.portfolio[name] ?? lastRun(qc, "portfolio", name)) : null;
  const sweepParams = current ? (asked.sweep[name] ?? lastRun(qc, "sweep", name)) : null;
  const portfolio = usePortfolio(portfolioParams);
  const sweep = useSweep(sweepParams);

  const select = (name: string) => {
    void navigate({ to: "/playbooks", search: { screen: name } });
  };

  const runPortfolio = () => {
    if (!current || !to) return;
    const p: PortfolioParams = {
      screen: current.name,
      from: settings.from,
      to,
      atrMultiple: settings.atrMultiple,
      maxHold: settings.maxHold,
      regime: settings.regime,
      riskPct: settings.riskPct,
      maxPositions: settings.maxPositions,
      costs: settings.costs,
    };
    if (portfolioParams && JSON.stringify(p) === JSON.stringify(portfolioParams)) void portfolio.refetch();
    else setAsked((a) => ({ ...a, portfolio: { ...a.portfolio, [p.screen]: p } }));
  };
  const runSweep = () => {
    if (!current || !to) return;
    const p: SweepParams = { screen: current.name, from: settings.from, split: settings.split, to };
    if (sweepParams && JSON.stringify(p) === JSON.stringify(sweepParams)) void sweep.refetch();
    else setAsked((a) => ({ ...a, sweep: { ...a.sweep, [p.screen]: p } }));
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[400px_1fr]" data-testid="playbook-lab">
      <Panel
        title="Playbook"
        badge={<span className="tag">rules run in Java</span>}
        className="xl:sticky xl:top-0 xl:self-start"
      >
        {screens.isPending ? (
          <Loading />
        ) : screens.isError ? (
          <ErrorState error={screens.error} onRetry={() => void screens.refetch()} />
        ) : (
          <div className="grid gap-4 p-4">
            <ScreenPicker screens={list} current={current?.name} onSelect={select} />
            {current && <Rules screen={current} settings={settings} />}
            <SettingsForm settings={settings} newest={newest} onChange={setSettings} />
            <div className="grid gap-2">
              <button
                type="button"
                className="btn btn-pri justify-center"
                onClick={runPortfolio}
                disabled={!to || portfolio.isFetching}
              >
                <FlaskConical size={15} /> Run backtest
              </button>
              <button
                type="button"
                className="btn justify-center"
                onClick={runSweep}
                disabled={!to || sweep.isFetching}
              >
                <Grid3x3 size={15} /> Test robustness (18 settings)
              </button>
              <p className="m-0 text-xs text-tx3">
                {to ? (
                  <>
                    Tests run from {settings.from} to <span className="num">{to}</span>
                    {to === newest && ", the newest stored session"}. A backtest takes 1 to 2 minutes; the
                    robustness sweep several.
                  </>
                ) : (
                  "Waiting for the shortlist to learn the newest stored session."
                )}
              </p>
            </div>
          </div>
        )}
      </Panel>

      <div className="grid min-w-0 content-start gap-4">
        <Panel
          title="Today's shortlist"
          badge={<span className="tag tag-a">live</span>}
          aside={shortlist.data && `as of ${shortlist.data.asOf} · ${shortlist.data.source}`}
          delay={50}
        >
          {!current ? null : shortlist.isPending ? (
            <Loading label="Running the screen" />
          ) : shortlist.isError ? (
            <ErrorState error={shortlist.error} onRetry={() => void shortlist.refetch()} />
          ) : (
            <Shortlist result={shortlist.data} />
          )}
        </Panel>

        <Panel
          title="Backtest · paper account"
          aside={portfolioParams && `${portfolioParams.from} to ${portfolioParams.to}`}
          delay={100}
        >
          <RunState
            query={portfolioParams ? portfolio : null}
            idle="Choose settings and run a backtest: an account trading this screen, with stops, sizing and costs, against Nifty 50 bought and held."
            running="Simulating the account in Java"
            typical="usually 1 to 2 minutes"
          >
            {(report) => <BacktestResult report={report} />}
          </RunState>
        </Panel>

        <Panel
          title="Robustness · in-sample vs out-of-sample"
          aside={sweepParams && `split at ${sweepParams.split}`}
          delay={150}
        >
          <RunState
            query={sweepParams ? sweep : null}
            idle="Is it luck? The sweep picks the best of 18 settings on the years before the split, then shows how every setting did on the years after it."
            running="Running 18 simulations in Java"
            typical="usually a few minutes"
          >
            {(report) => <SweepResult report={report} />}
          </RunState>
        </Panel>
      </div>
    </div>
  );
}

function ScreenPicker(props: {
  screens: ScreenInfo[];
  current: string | undefined;
  onSelect: (name: string) => void;
}) {
  return (
    <div role="radiogroup" aria-label="Screen" className="grid gap-1.5">
      {props.screens.map((s) => {
        const on = s.name === props.current;
        return (
          <button
            key={s.name}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => {
              props.onSelect(s.name);
            }}
            className={clsx(
              "rounded-xl border px-3 py-2 text-left transition-colors",
              on ? "border-a/50 bg-a/10" : "border-line hover:border-line2",
            )}
          >
            <div className={clsx("font-semibold", on ? "text-tx" : "text-tx2")}>{screenTitle(s.name)}</div>
            <div className="num text-[11px] text-tx3">{s.name}</div>
          </button>
        );
      })}
    </div>
  );
}

/** The playbook as blocks, as in the mock-up; the text comes from the backend and the chosen settings. */
function Rules({ screen, settings }: { screen: ScreenInfo; settings: Settings }) {
  const blocks: { what: string; tone: string; text: ReactNode }[] = [
    {
      what: "Universe",
      tone: "border-l-b",
      text: "NSE company shares; skipped when illiquid, stale, short of history or showing a possible corporate action.",
    },
    {
      what: "Signal",
      tone: "border-l-a",
      text: (
        <>
          {screen.description} <span className="text-tx3">Needs {screen.lookback} sessions of history.</span>
        </>
      ),
    },
    {
      what: "Size",
      tone: "border-l-warn",
      text: (
        <>
          risk <code>{settings.riskPct}%</code> of equity per trade · at most{" "}
          <code>{settings.maxPositions}</code> open · entry at the next open
        </>
      ),
    },
    {
      what: "Exit",
      tone: "border-l-dn",
      text: (
        <>
          stop <code>{settings.atrMultiple} × ATR(14)</code> · holding limit <code>{settings.maxHold}</code>{" "}
          sessions · corporate-action gap
        </>
      ),
    },
    {
      what: "Guards",
      tone: "border-l-up",
      text: (
        <>
          regime filter <code>{settings.regime ? "on" : "off"}</code>
          {settings.regime && " (enter only when Nifty 50 is above its 200-session average)"} · costs{" "}
          <code>{settings.costs ? "on" : "off"}</code> · paper only; a human approves every order
        </>
      ),
    },
  ];
  return (
    <ol aria-label="Rules" className="m-0 grid list-none gap-1.5 p-0">
      {blocks.map((b) => (
        <li
          key={b.what}
          className={clsx("rounded-lg border border-l-2 border-line bg-s2/40 px-3 py-2", b.tone)}
        >
          <div className="text-[10px] font-semibold tracking-[.12em] text-tx3 uppercase">{b.what}</div>
          <div className="text-xs leading-relaxed text-tx2 [&_code]:font-mono [&_code]:text-tx">{b.text}</div>
        </li>
      ))}
    </ol>
  );
}

function SettingsForm(props: {
  settings: Settings;
  newest: string | undefined;
  onChange: (s: Settings) => void;
}) {
  const { settings, newest, onChange } = props;
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => {
    onChange({ ...settings, [k]: v });
  };
  const field = "grid gap-1 text-[11px] text-tx3";
  const input =
    "num rounded-lg border border-line2 bg-s2 px-2 py-1.5 text-xs text-tx outline-none focus:border-a/60";
  return (
    <fieldset className="m-0 grid grid-cols-2 gap-2.5 border-0 p-0">
      <legend className="mb-1.5 text-[10px] font-semibold tracking-[.12em] text-tx3 uppercase">
        Settings
      </legend>
      <label className={field}>
        From
        <input
          type="date"
          className={input}
          value={settings.from}
          min="2018-01-01"
          onChange={(e) => {
            set("from", e.target.value);
          }}
        />
      </label>
      <label className={field}>
        To {settings.to === "" && <span>(newest)</span>}
        <input
          type="date"
          className={input}
          value={settings.to || (newest ?? "")}
          max={newest}
          onChange={(e) => {
            set("to", e.target.value === newest ? "" : e.target.value);
          }}
        />
      </label>
      <label className={field}>
        Out-of-sample from
        <input
          type="date"
          className={input}
          value={settings.split}
          onChange={(e) => {
            set("split", e.target.value);
          }}
        />
      </label>
      <label className={field}>
        Stop (× ATR 14)
        <select
          className={input}
          value={settings.atrMultiple}
          onChange={(e) => {
            set("atrMultiple", Number(e.target.value));
          }}
        >
          {STOP_MULTIPLES.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </label>
      <label className={field}>
        Holding limit (sessions)
        <select
          className={input}
          value={settings.maxHold}
          onChange={(e) => {
            set("maxHold", Number(e.target.value));
          }}
        >
          {HOLD_LIMITS.map((h) => (
            <option key={h} value={h}>
              {h}
            </option>
          ))}
        </select>
      </label>
      <label className={field}>
        Risk per trade (%)
        <select
          className={input}
          value={settings.riskPct}
          onChange={(e) => {
            set("riskPct", Number(e.target.value));
          }}
        >
          {[0.5, 1, 2].map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
      </label>
      <label className={field}>
        Max positions
        <select
          className={input}
          value={settings.maxPositions}
          onChange={(e) => {
            set("maxPositions", Number(e.target.value));
          }}
        >
          {[5, 10, 15, 20].map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <label className="col-span-2 flex items-center gap-2 text-xs text-tx2">
        <input
          type="checkbox"
          checked={settings.regime}
          onChange={(e) => {
            set("regime", e.target.checked);
          }}
        />
        Regime filter: enter only when Nifty 50 is above its 200-session average
      </label>
      <label className="col-span-2 flex items-center gap-2 text-xs text-tx2">
        <input
          type="checkbox"
          checked={settings.costs}
          onChange={(e) => {
            set("costs", e.target.checked);
          }}
        />
        Charges and slippage (Zerodha delivery charges, 5 bps)
      </label>
      <p className="col-span-2 m-0 text-[11px] text-tx3">
        The robustness sweep uses only the dates: it tries all 18 stop, holding-limit and regime combinations
        on its own fixed account settings, shown with its result.
      </p>
    </fieldset>
  );
}
