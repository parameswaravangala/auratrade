import type { JobRun } from "@/api/schemas";
import { formatDuration, formatInt } from "@/lib/format";
import { istHm } from "@/lib/time";
import { layoutRuns } from "@/lib/timeline";

const X0 = 150;
const W = 730;
const FILL: Record<JobRun["status"], string> = {
  SUCCEEDED: "color-mix(in srgb, var(--up) 70%, transparent)",
  PARTIAL: "color-mix(in srgb, var(--warn) 75%, transparent)",
  FAILED: "color-mix(in srgb, var(--dn) 75%, transparent)",
  RUNNING: "var(--a)",
};

/** The last 24 hours of job runs on one lane per job, with a "now" line at the right edge. */
export function JobTimeline({ runs, now, known }: { runs: JobRun[]; now: Date; known: readonly string[] }) {
  const { lanes, bars, windowStart } = layoutRuns(runs, now, known);
  const laneY = (i: number) => 50 + i * 56;
  const height = Math.max(200, laneY(lanes.length - 1) + 64);
  const ticks = Array.from({ length: 9 }, (_, i) => i * 3);
  return (
    <svg
      viewBox={`0 0 900 ${height}`}
      preserveAspectRatio="none"
      className="block w-full"
      style={{ height }}
      role="img"
      aria-label="Job runs, last 24 hours"
    >
      {ticks.map((h) => {
        const x = X0 + (h / 24) * W;
        return (
          <g key={h}>
            <line x1={x} x2={x} y1={18} y2={height - 26} stroke="var(--grid)" />
            <text
              x={x}
              y={height - 8}
              textAnchor="middle"
              fill="var(--tx3)"
              fontSize={10}
              fontFamily="var(--font-mono)"
            >
              {istHm(new Date(windowStart.getTime() + h * 3_600_000))}
            </text>
          </g>
        );
      })}
      {lanes.map((name, i) => (
        <g key={name}>
          <text x={10} y={laneY(i) + 4} fill="var(--tx2)" fontSize={12}>
            {name}
          </text>
          <line x1={X0} x2={X0 + W} y1={laneY(i)} y2={laneY(i)} stroke="var(--line)" />
        </g>
      ))}
      {bars.map((b) => (
        <rect
          key={b.id}
          x={X0 + b.x0 * W}
          y={laneY(b.lane) - 11}
          width={Math.max((b.x1 - b.x0) * W, 8)}
          height={22}
          rx={5}
          fill={FILL[b.status]}
          className={b.status === "RUNNING" ? "shimmer" : undefined}
        >
          <title>
            {`${b.run.jobName} · ${b.run.status} · ${b.run.trigger}\nstarted ${istHm(new Date(b.run.startedAt))} IST${
              b.run.finishedAt
                ? ` · took ${formatDuration(Date.parse(b.run.finishedAt) - Date.parse(b.run.startedAt))}`
                : ""
            }\n${formatInt(b.run.itemsOk)} ok · ${formatInt(b.run.itemsFailed)} failed${b.run.detail ? `\n${b.run.detail}` : ""}`}
          </title>
        </rect>
      ))}
      <line
        x1={X0 + W - 1}
        x2={X0 + W - 1}
        y1={14}
        y2={height - 26}
        stroke="var(--a)"
        strokeWidth={1.5}
        strokeDasharray="3 3"
      />
    </svg>
  );
}
