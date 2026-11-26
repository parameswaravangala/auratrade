import type { JobRun } from "@/api/schemas";
import { istHm } from "@/lib/time";
import { describe, health } from "@/lib/timeline";

const COLOR = {
  ok: "var(--up)",
  warn: "var(--warn)",
  fail: "var(--dn)",
  running: "var(--a)",
  unknown: "var(--tx3)",
} as const;

/** What the system did, newest first, in plain sentences. */
export function Narrative({ runs }: { runs: JobRun[] }) {
  const items = [...runs].sort((a, b) => Date.parse(b.startedAt) - Date.parse(a.startedAt)).slice(0, 7);
  return (
    <ul className="m-0 grid list-none gap-0.5 px-4 pt-2.5 pb-3.5">
      {items.map((r) => {
        const h = health(r);
        return (
          <li key={r.id} className="grid grid-cols-[48px_14px_1fr] items-start gap-2.5 py-1.5">
            <span className="num pt-px text-[11px] text-tx3">{istHm(new Date(r.startedAt))}</span>
            <span
              className="mt-1 size-[9px] rounded-full"
              style={{ background: COLOR[h], boxShadow: h === "running" ? `0 0 8px ${COLOR[h]}` : undefined }}
            />
            <span>
              <b className="font-semibold">{r.jobName}</b> <span className="text-tx2">{describe(r)}</span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}
