import clsx from "clsx";
import type { JobRun, SpendReport } from "@/api/schemas";
import { formatInt, formatUsd } from "@/lib/format";
import { istDayMonth, istHm } from "@/lib/time";
import { health, type Health } from "@/lib/timeline";

type Props = { latest: Map<string, JobRun>; spend: SpendReport | undefined };

const STROKE: Record<Health, string> = {
  ok: "color-mix(in srgb, var(--up) 55%, transparent)",
  warn: "color-mix(in srgb, var(--warn) 70%, transparent)",
  fail: "color-mix(in srgb, var(--dn) 70%, transparent)",
  running: "color-mix(in srgb, var(--a) 70%, transparent)",
  unknown: "var(--line2)",
};

function runLine(run: JobRun | undefined): string {
  if (!run) return "no runs yet";
  const when = `${istDayMonth(new Date(run.startedAt))} ${istHm(new Date(run.startedAt))}`;
  return `${run.status.toLowerCase()} · ${when}`;
}

function Node(p: {
  x: number;
  y: number;
  w?: number;
  title: string;
  sub: string;
  h: Health;
  dashed?: boolean;
  ai?: boolean;
}) {
  const w = p.w ?? 150;
  return (
    <g role="listitem" aria-label={`${p.title}: ${p.sub}`}>
      <rect
        x={p.x}
        y={p.y}
        width={w}
        height={60}
        rx={12}
        fill="var(--s2)"
        stroke={p.ai ? "color-mix(in srgb, var(--b) 60%, transparent)" : STROKE[p.h]}
        strokeDasharray={p.dashed ? "4 4" : undefined}
        className={clsx(p.h === "running" && "shimmer")}
      />
      <text
        x={p.x + 14}
        y={p.y + 26}
        fill={p.dashed ? "var(--tx3)" : "var(--tx)"}
        fontSize={12}
        fontWeight={600}
      >
        {p.title}
      </text>
      <text x={p.x + 14} y={p.y + 46} fill="var(--tx3)" fontSize={10.5} fontFamily="var(--font-mono)">
        {p.sub}
      </text>
    </g>
  );
}

/** Feeds, loaders, store, resolver and LLM gateway, coloured by the latest run of each job. */
export function PipelineMap({ latest, spend }: Props) {
  const im = latest.get("instrument-master");
  const eod = latest.get("eod-candles");
  const imH = health(im);
  const eodH = health(eod);
  const flowing = (h: Health) => (h === "ok" || h === "running" ? "edge-live" : "");
  return (
    <svg viewBox="0 0 900 230" className="block h-[230px] w-full" role="list" aria-label="Data pipeline">
      <defs>
        <filter id="glow">
          <feGaussianBlur stdDeviation="3" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <g fill="none" strokeWidth={1.5}>
        <path
          id="e-im1"
          d="M150 70 C 200 70, 200 70, 250 70"
          stroke="var(--line2)"
          className={flowing(imH)}
        />
        <path
          id="e-im2"
          d="M400 70 C 450 70, 450 70, 500 70"
          stroke="var(--line2)"
          className={flowing(imH)}
        />
        <path
          d="M150 165 C 200 165, 200 165, 250 165"
          stroke="var(--line2)"
          className={flowing(eodH)}
          strokeDasharray={eodH === "ok" ? undefined : "2 6"}
        />
        <path d="M400 165 C 450 165, 450 120, 500 100" stroke="var(--line2)" strokeDasharray="2 6" />
        <path id="e-llm" d="M650 70 C 700 70, 700 70, 745 70" stroke="var(--line2)" className="edge-live" />
        <path d="M650 85 C 700 110, 700 150, 745 160" stroke="var(--line2)" />
      </g>
      <Node x={10} y={40} w={140} title="Upstox instruments" sub="public file · 07:00" h={imH} />
      <Node
        x={250}
        y={40}
        title="Instrument master"
        sub={im ? `${formatInt(im.itemsOk)} ok · ${im.itemsFailed} rejected` : "no runs yet"}
        h={imH}
      />
      <Node
        x={500}
        y={40}
        title="Symbol resolver"
        sub="rebuilt after each load"
        h={imH === "unknown" ? "unknown" : "ok"}
      />
      <Node x={10} y={135} w={140} title="Upstox candles" sub={runLine(eod)} h={eodH} />
      <Node
        x={250}
        y={135}
        title="Validator → store"
        sub={eod ? `${eod.itemsOk} ok · ${eod.itemsFailed} owed` : "no runs yet"}
        h={eodH}
      />
      <Node
        x={745}
        y={40}
        w={145}
        title="LLM gateway"
        sub={spend ? `${formatUsd(spend.monthSpendUsd)} this month` : "—"}
        h="ok"
        ai
      />
      <Node x={745} y={135} w={145} title="Research agents" sub="later sprint" h="unknown" dashed />
      {imH === "ok" && (
        <>
          <circle r={4} fill="var(--a)" filter="url(#glow)">
            <animateMotion dur="1.6s" repeatCount="indefinite">
              <mpath href="#e-im1" />
            </animateMotion>
          </circle>
          <circle r={4} fill="var(--a)" filter="url(#glow)">
            <animateMotion dur="1.6s" begin=".8s" repeatCount="indefinite">
              <mpath href="#e-im2" />
            </animateMotion>
          </circle>
        </>
      )}
      <circle r={4} fill="var(--b)" filter="url(#glow)">
        <animateMotion dur="1.3s" repeatCount="indefinite">
          <mpath href="#e-llm" />
        </animateMotion>
      </circle>
    </svg>
  );
}
