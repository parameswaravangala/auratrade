import { Panel } from "@/components/Panel";

const LIBS: [string, string][] = [
  ["React, React DOM", "MIT"],
  ["TanStack Query, TanStack Router", "MIT"],
  ["Zod", "MIT"],
  ["cmdk", "MIT"],
  ["sonner", "MIT"],
  ["clsx", "MIT"],
  ["lucide-react", "ISC"],
  ["Tailwind CSS", "MIT"],
  ["Inter, JetBrains Mono (via Fontsource)", "OFL-1.1"],
  ["TradingView Lightweight Charts™", "Apache-2.0"],
];

/** Licences and the attribution notice that TradingView Lightweight Charts requires. */
export function About() {
  return (
    <div className="mx-auto grid max-w-3xl gap-4 pt-6">
      <Panel title="About AuraTrade">
        <p className="m-0 px-4 pt-3 pb-4 leading-relaxed text-tx2">
          A research and paper-trading cockpit for NSE equities. Agents are read-only and never place orders;
          a human approves every paper trade. Phase 1 has no live trading.
        </p>
      </Panel>
      <Panel title="Charting attribution">
        <div className="px-4 pt-3 pb-4 leading-relaxed text-tx2">
          <p className="m-0">
            Charts use TradingView Lightweight Charts™, Copyright (c) 2025 TradingView, Inc.{" "}
            <a href="https://www.tradingview.com/" target="_blank" rel="noreferrer" className="text-a">
              https://www.tradingview.com/
            </a>
          </p>
          <p className="mt-2 mb-0 text-xs text-tx3">Licensed under the Apache License 2.0.</p>
        </div>
      </Panel>
      <Panel title="Open-source licences">
        <ul className="m-0 grid list-none gap-1.5 px-4 pt-3 pb-4">
          {LIBS.map(([n, l]) => (
            <li key={n} className="flex items-center">
              <span>{n}</span>
              <span className="tag ml-auto">{l}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </div>
  );
}
