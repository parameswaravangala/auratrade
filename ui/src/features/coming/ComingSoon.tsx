import { NAV } from "@/app/nav";
import { Panel } from "@/components/Panel";

type Path = "/research" | "/agents" | "/calendar";

const PLANS: Record<Path, { what: string; shows: string[]; needs: string[] }> = {
  "/research": {
    what: "News triage, a theme radar and thesis drafts that you review. Injection attempts are quarantined, never acted on.",
    shows: [
      "news stream with triage score and resolved symbols",
      "theme radar (attention × sentiment)",
      "thesis draft with evidence and confidence",
    ],
    needs: ["news ingestion", "NEWS_TRIAGE and STOCK_ANALYSIS agents", "GET /api/v1/research/*"],
  },
  "/agents": {
    what: "Each agent's tier, floor and daily token cap, token flow by tier, call traces and the eval golden set.",
    shows: [
      "agents table from AgentType",
      "token flow agent → tier → model",
      "trace waterfall and 21-case eval grid",
    ],
    needs: ["GET /api/v1/llm/budget and /api/v1/llm/runs", "eval results stored per run"],
  },
  "/calendar": {
    what: "Trading-calendar coverage per year (fail closed outside it) and validation findings from candle loads.",
    shows: [
      "year heatmap: trading, holiday, weekend, not covered",
      "coverage summary and the 1 December alert",
      "validation warnings and errors",
    ],
    needs: ["GET /api/v1/calendar/coverage", "GET /api/v1/marketdata/freshness"],
  },
};

/** A designed screen (see docs/ui/mockup.html) that waits for backend endpoints. */
export function ComingSoon({ path }: { path: Path }) {
  const nav = NAV.find((n) => n.to === path);
  const plan = PLANS[path];
  return (
    <div className="mx-auto grid max-w-4xl gap-4 pt-6">
      <Panel title={nav?.label ?? path} badge={<span className="tag tag-b">designed · not built yet</span>}>
        <div className="grid gap-5 p-5 md:grid-cols-2">
          <div>
            <p className="m-0 text-[15px] leading-relaxed">{plan.what}</p>
            <p className="mt-4 mb-0 text-xs text-tx3">
              The full design is in docs/ui/mockup.html (open it in a browser and pick this screen in the
              rail).
            </p>
          </div>
          <div className="grid gap-4">
            <List title="It will show" items={plan.shows} tone="a" />
            <List title="It needs first" items={plan.needs} tone="w" />
          </div>
        </div>
      </Panel>
    </div>
  );
}

function List({ title, items, tone }: { title: string; items: string[]; tone: "a" | "w" }) {
  return (
    <div>
      <div className="mb-1.5 text-[11px] font-semibold tracking-[.08em] text-tx3 uppercase">{title}</div>
      <ul className="m-0 grid list-none gap-1.5 p-0">
        {items.map((i) => (
          <li key={i} className="flex gap-2 text-tx2">
            <span className={tone === "a" ? "text-a" : "text-warn"}>▸</span>
            {i}
          </li>
        ))}
      </ul>
    </div>
  );
}
