import type { SeriesCount } from "@/api/schemas";
import { formatInt } from "@/lib/format";

/** Active instruments by series as concentric rings; the largest series fills most of its ring. */
export function Universe({ series }: { series: SeriesCount[] }) {
  const sorted = [...series].filter((s) => s.active > 0).sort((a, b) => b.active - a.active);
  const top = sorted.slice(0, 5);
  const rest = sorted.slice(5).reduce((n, s) => n + s.active, 0);
  const rows = rest > 0 ? [...top, { series: "other", active: rest, inactive: 0 }] : top;
  const max = rows[0]?.active ?? 1;
  const total = sorted.reduce((n, s) => n + s.active, 0);
  const cx = 120;
  const cy = 122;
  return (
    <div className="flex items-center gap-2 px-2 pb-3">
      <svg
        viewBox="0 0 240 240"
        className="block w-[60%]"
        role="img"
        aria-label={`${formatInt(total)} active instruments`}
      >
        {rows.map((s, i) => {
          const r = 100 - i * 15;
          const L = 2 * Math.PI * r;
          const f = 0.08 + 0.82 * Math.sqrt(s.active / max);
          return (
            <g key={s.series}>
              <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--line)" strokeWidth={8} />
              <circle
                cx={cx}
                cy={cy}
                r={r}
                fill="none"
                stroke={i % 2 ? "var(--b)" : "var(--a)"}
                strokeOpacity={1 - i * 0.11}
                strokeWidth={8}
                strokeLinecap="round"
                strokeDasharray={`${L * f} ${L}`}
                transform={`rotate(-90 ${cx} ${cy})`}
                style={{ transition: "stroke-dasharray 1.1s cubic-bezier(.2,.8,.2,1)" }}
              />
            </g>
          );
        })}
        <text
          x={cx}
          y={cy + 6}
          textAnchor="middle"
          fill="var(--tx)"
          fontSize={18}
          fontFamily="var(--font-mono)"
        >
          {formatInt(total)}
        </text>
      </svg>
      <ul className="m-0 grid flex-1 list-none gap-1.5 p-0 text-xs">
        {rows.map((s, i) => (
          <li key={s.series} className="flex items-center gap-2">
            <i
              className="size-2 rounded-sm"
              style={{ background: i % 2 ? "var(--b)" : "var(--a)", opacity: 1 - i * 0.11 }}
            />
            <span className="num text-tx2">{s.series}</span>
            <span className="num ml-auto">{formatInt(s.active)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
