import type { SpendReport } from "@/api/schemas";
import { budgetStage, ringFill, THRESHOLDS } from "@/lib/budget";
import { formatPct, formatUsd } from "@/lib/format";

const C = 105;
const R = 86;
const L = 2 * Math.PI * R;

const STAGE_TEXT = {
  normal: "all agents on their normal tier",
  downgraded: "non-critical agents dropped a tier",
  "at-floor": "non-critical agents at their floor",
  "non-critical-stopped": "non-critical agents stopped",
  stopped: "all agents stopped",
} as const;

/** Month-to-date LLM spend against the budget, with the ADR 0006 thresholds marked. */
export function BudgetRing({ spend }: { spend: SpendReport }) {
  const fill = ringFill(spend.usedPercent);
  const stage = budgetStage(spend.usedPercent);
  return (
    <div>
      <div className="relative grid place-items-center pt-2 pb-3.5">
        <svg
          width={210}
          height={210}
          viewBox="0 0 210 210"
          role="img"
          aria-label={`LLM budget ${formatPct(spend.usedPercent)} used`}
        >
          <defs>
            <linearGradient id="rg" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="var(--a)" />
              <stop offset="1" stopColor="var(--b)" />
            </linearGradient>
          </defs>
          <circle cx={C} cy={C} r={R} fill="none" stroke="var(--line2)" strokeWidth={12} />
          {THRESHOLDS.map(({ at }) => {
            const a = (at / 100) * 2 * Math.PI - Math.PI / 2;
            return (
              <line
                key={at}
                x1={C + Math.cos(a) * (R - 10)}
                y1={C + Math.sin(a) * (R - 10)}
                x2={C + Math.cos(a) * (R + 10)}
                y2={C + Math.sin(a) * (R + 10)}
                stroke={at < 85 ? "var(--warn)" : "var(--dn)"}
                strokeWidth={2}
                strokeLinecap="round"
              />
            );
          })}
          <circle
            cx={C}
            cy={C}
            r={R}
            fill="none"
            stroke="url(#rg)"
            strokeWidth={12}
            strokeLinecap="round"
            strokeDasharray={`${L * fill} ${L}`}
            transform={`rotate(-90 ${C} ${C})`}
            style={{
              filter: "drop-shadow(0 0 6px var(--a))",
              transition: "stroke-dasharray 1s cubic-bezier(.2,.8,.2,1)",
            }}
          />
          <circle cx={C} cy={C} r={R - 24} fill="none" stroke="var(--line)" strokeDasharray="2 5" />
        </svg>
        <div className="absolute text-center">
          <b className="num block text-[30px] font-medium">{formatUsd(spend.monthSpendUsd)}</b>
          <span className="text-xs text-tx2">
            of {formatUsd(spend.budgetUsd)} · {formatPct(spend.usedPercent)}
          </span>
        </div>
      </div>
      <div className="grid grid-cols-4 gap-1.5 px-4">
        {THRESHOLDS.map((t) => (
          <div
            key={t.at}
            className={`rounded-[10px] border px-2 py-1.5 text-[11px] text-tx3 ${spend.usedPercent >= t.at ? "border-warn/60" : "border-line"}`}
          >
            <b className="num block text-[13px] font-medium text-tx2">{t.at}%</b>
            {t.label}
          </div>
        ))}
      </div>
      <p className="m-0 px-4 pt-2.5 pb-4 text-xs text-tx2">
        Now: {STAGE_TEXT[stage]}. RISK_REVIEW is never downgraded.
      </p>
    </div>
  );
}
