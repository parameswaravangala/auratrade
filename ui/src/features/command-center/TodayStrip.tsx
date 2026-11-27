import { Link } from "@tanstack/react-router";
import clsx from "clsx";
import type { ReactNode } from "react";
import { usePaperAccount, usePaperAccounts, usePaperOrders } from "@/api/queries";
import type { JobRun, PaperAccount } from "@/api/schemas";
import { formatInt } from "@/lib/format";
import { timeLeft } from "@/lib/paper";
import { istDayMonth, istHm } from "@/lib/time";
import { coveragePct, lastCompleted, problems, recentlyExpired, urgency, waiting } from "@/lib/today";

/**
 * What needs you today (B-040): paper orders waiting for a decision and the time left, the last completed
 * evening candle run, and job runs that still need attention. Each tile links to where you act on it.
 * A tile whose source fails reads "unavailable", never zero or "none" (fail closed).
 */
export function TodayStrip(props: { runs: JobRun[] | undefined; runsError: Error | null; now: Date }) {
  const { runs, runsError, now } = props;
  const accounts = usePaperAccounts();
  const active = (accounts.data ?? []).filter((a) => a.active);
  return (
    <section aria-label="Today" className="grid gap-3 md:grid-cols-3 xl:col-span-3">
      <Tile title="Paper decisions">
        {accounts.isPending ? (
          <Muted>Loading…</Muted>
        ) : accounts.isError ? (
          <Unavailable error={accounts.error} />
        ) : active.length === 0 ? (
          <Muted>No active paper account.</Muted>
        ) : (
          active.map((a) => <AccountLine key={a.id} account={a} now={now} />)
        )}
      </Tile>
      <Tile title="Evening candles">
        {runsError ? (
          <Unavailable error={runsError} />
        ) : runs === undefined ? (
          <Muted>Loading…</Muted>
        ) : (
          <EodLine run={lastCompleted(runs, "eod-candles")} />
        )}
      </Tile>
      <Tile title="Job problems">
        {runsError ? (
          <Unavailable error={runsError} />
        ) : runs === undefined ? (
          <Muted>Loading…</Muted>
        ) : (
          <Problems runs={runs} now={now} />
        )}
      </Tile>
    </section>
  );
}

function AccountLine({ account, now }: { account: PaperAccount; now: Date }) {
  const view = usePaperAccount(account.id);
  const orders = usePaperOrders(account.id);
  if (view.isPending) return <Muted>Loading {account.name}…</Muted>;
  if (view.isError) return <Unavailable error={view.error} what={account.name} />;
  const w = waiting(view.data.proposed);
  const expired = orders.data ? recentlyExpired(orders.data, now) : 0;
  const expiredLine =
    expired > 0 ? (
      <span className="num text-[11px] text-dn">{expired} expired undecided in the last 24 h</span>
    ) : null;

  if (!w) {
    return (
      <Link
        to="/approvals"
        search={{ account: account.id }}
        className="grid gap-0.5 rounded-lg no-underline hover:bg-s2/60"
        aria-label={`No decisions waiting for ${account.name}`}
      >
        <span className="text-xs">
          <span className="text-up">No decisions waiting</span>
          <span className="text-tx3"> · {account.name}</span>
        </span>
        {expiredLine}
      </Link>
    );
  }
  const u = urgency(w.decideBy, now);
  const left = timeLeft(w.decideBy, now);
  return (
    <Link
      to="/approvals"
      search={{ account: account.id }}
      className="grid gap-0.5 rounded-lg no-underline hover:bg-s2/60"
      aria-label={`${String(w.count)} paper orders waiting for ${account.name}`}
    >
      <span className="flex items-baseline gap-2">
        <b
          className={clsx(
            "num text-2xl font-medium",
            u === "later" ? "text-tx" : u === "soon" ? "text-warn" : "text-dn",
          )}
        >
          {formatInt(w.count)}
        </b>
        <span className="text-xs text-tx2">
          {w.count === 1 ? "order waits" : "orders wait"} for you · {account.name}
        </span>
      </span>
      <span
        className={clsx(
          "num text-[11px]",
          u === "passed" ? "text-dn" : u === "soon" ? "text-warn" : "text-tx3",
        )}
      >
        {left
          ? `decide by ${istDayMonth(w.decideBy)} ${istHm(w.decideBy)} IST · ${left}`
          : `deadline passed at ${istHm(w.decideBy)} IST · not decided, they will expire`}
      </span>
      {expiredLine}
      <span className="text-[11px] text-a">Open the approval queue →</span>
    </Link>
  );
}

const STATUS_TONE: Record<JobRun["status"], string> = {
  SUCCEEDED: "tag-g",
  PARTIAL: "tag-w",
  FAILED: "tag-r",
  RUNNING: "tag-b",
};

function EodLine({ run }: { run: JobRun | undefined }) {
  if (!run) return <Muted>No completed eod-candles run yet.</Muted>;
  const at = new Date(run.finishedAt ?? run.startedAt);
  const pct = coveragePct(run);
  return (
    <a
      href="#job-timeline"
      className="grid gap-1 rounded-lg no-underline hover:bg-s2/60"
      aria-label="Last evening candle run"
    >
      <span className="flex flex-wrap items-baseline gap-2">
        <b className="num text-lg font-medium text-tx">{run.windowTo ?? "—"}</b>
        <span className={clsx("tag", STATUS_TONE[run.status])}>{run.status.toLowerCase()}</span>
        <span className="num text-xs text-tx2">
          {pct === null ? "no shares attempted" : `${pct.toFixed(pct === 100 ? 0 : 1)}% loaded`}
        </span>
      </span>
      <span className="num text-[11px] text-tx3">
        session · finished {istDayMonth(at)} {istHm(at)} IST · {formatInt(run.itemsOk)} ok
        {run.itemsFailed > 0 && `, ${formatInt(run.itemsFailed)} failed`}
      </span>
      {run.status !== "SUCCEEDED" && run.detail && (
        <span className="line-clamp-2 text-[11px] text-tx2" title={run.detail}>
          {run.detail}
        </span>
      )}
    </a>
  );
}

const KIND_LABEL = { failed: "failed", partial: "partial", abandoned: "stuck" } as const;
const KIND_TONE = { failed: "tag-r", partial: "tag-w", abandoned: "tag-w" } as const;

function Problems({ runs, now }: { runs: JobRun[]; now: Date }) {
  const list = problems(runs, now);
  if (list.length === 0) return <div className="text-xs text-up">No problems</div>;
  return (
    <ul className="m-0 grid list-none gap-1 p-0" aria-label="Job problems">
      {list.slice(0, 4).map(({ run, kind }) => (
        <li key={run.id}>
          <a
            href="#job-timeline"
            className="grid rounded-lg text-xs no-underline hover:bg-s2/60"
            aria-label={`${run.jobName} run ${String(run.id)} ${KIND_LABEL[kind]}`}
          >
            <span className="flex items-center gap-2">
              <span className={clsx("tag", KIND_TONE[kind])}>{KIND_LABEL[kind]}</span>
              <span className="num text-tx">{run.jobName}</span>
              <span className="num ml-auto text-[11px] text-tx3">
                {istDayMonth(new Date(run.startedAt))} {istHm(new Date(run.startedAt))}
              </span>
            </span>
            <span className="truncate text-[11px] text-tx3" title={run.detail ?? undefined}>
              {kind === "abandoned"
                ? "still RUNNING after 3 h: probably abandoned by a restart"
                : (run.detail ?? "no detail")}
            </span>
          </a>
        </li>
      ))}
      {list.length > 4 && (
        <li className="text-[11px] text-tx3">and {list.length - 4} more in the job timeline</li>
      )}
    </ul>
  );
}

function Tile({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="glass panel-in grid content-start gap-2 px-4 py-3">
      <h2 className="m-0 text-[11px] font-semibold tracking-[.1em] text-tx2 uppercase">{title}</h2>
      {children}
    </section>
  );
}

function Unavailable({ error, what }: { error: Error; what?: string }) {
  return (
    <div className="grid gap-0.5 text-xs" role="alert">
      <span className="font-medium text-warn">{what ? `${what}: unavailable` : "Unavailable"}</span>
      <span className="text-[11px] text-tx3">{error.message}</span>
    </div>
  );
}

const Muted = ({ children }: { children: ReactNode }) => <div className="text-xs text-tx3">{children}</div>;
