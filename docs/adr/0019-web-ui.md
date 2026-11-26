# ADR 0019: Web UI stack, location, look and backend contract

- Status: accepted
- Date: 2026-11-26

## Context
AuraTrade needs a web UI: first for operations (jobs, data health, LLM spend) and charts, later for
research, playbooks and paper-trade approval. The owner wants an advanced, rich, futuristic product,
built as an enterprise application rather than a demo, with an original look that does not copy any
broker's screens. The owner is a Java/Spring developer, new to frontend tooling. The backend today
exposes only dev-profile endpoints under `/dev`.

## Options considered

| Option | For | Against |
| --- | --- | --- |
| **React + TypeScript + Vite** | Largest ecosystem for data-dense and visual UIs: dockable workspaces, command palettes, GPU and SVG visualisation, streaming AI chat; first-class support in Lightweight Charts, Tailwind, shadcn/ui. | Unopinionated: we choose router, data and state libraries ourselves. JSX and hooks are new concepts. |
| Angular | Opinionated and Spring-like (DI, services); one vendor for router, forms, HTTP. | Most of the advanced libraries we want (dockview's React API, cmdk, shadcn/ui, Motion) are React-first. |
| Vaadin Flow (Java only) | No JavaScript; UI in Java on the server. | Server round trips for every interaction; a bespoke animated look fights the framework; some components are commercial. |
| Hilla (Vaadin, React + Java endpoints) | Typed TS clients generated from Java endpoints. | Ties build and endpoints to Vaadin; we get typed clients from OpenAPI anyway. |

Location: a `ui/` folder in this repo versus a separate repo. One repo keeps API and UI changes in one PR
and one CI run, which suits a solo project.

## Decision

1. **React 19 + TypeScript (strict) + Vite 8**, in **`ui/`** in this repo, with **npm** (ships with Node 24
   LTS). CI gets a separate `ui` job: install, lint, type-check, test, build.
   - TypeScript 6.0, not 7.0: typescript-eslint 8.72 supports TypeScript below 6.1 only (checked
     2026-11-26). Move to 7 when it does.
   - React Compiler (automatic memoisation) is deferred: on Vite 8 it needs Babel back through
     `@rolldown/plugin-babel`. It is an optimisation; add it when a screen needs it.
2. **Libraries.** Licences checked at the npm registry and upstream repos on 2026-11-26; all free and
   permissive. We pin versions at least two weeks old.

   | Concern | Library | Licence | Java analogy |
   | --- | --- | --- | --- |
   | Styling, tokens | Tailwind CSS 4 | MIT | |
   | Components | shadcn/ui (copied in) on Radix primitives | MIT | |
   | Icons | lucide-react | ISC | |
   | Server state | TanStack Query | MIT | a cache plus RestClient with retries and polling |
   | Routing | TanStack Router (type-safe) | MIT | request mappings, checked at compile time |
   | Response validation | Zod | MIT | Bean Validation on everything that arrives |
   | Tables | TanStack Table + TanStack Virtual (10k rows) | MIT | |
   | Workspace | dockview (dockable, resizable, saved layouts) | MIT | |
   | Command palette | cmdk | MIT | |
   | Motion | Motion (springs, layout animation), View Transitions API | MIT | |
   | Toasts | sonner | MIT | |
   | Price charts | TradingView Lightweight Charts 5 | Apache-2.0 | |
   | Other visuals | hand-built SVG/Canvas with d3 scales; ECharts 6 where a stock chart type fits | ISC; Apache-2.0 | |
   | Fonts | Inter, JetBrains Mono, self-hosted via Fontsource | OFL-1.1 | |
   | Tests | Vitest, Testing Library, MSW (fake backend) | MIT | JUnit, AssertJ, WireMock |
   | Lint, format | ESLint + typescript-eslint + react-hooks (compiler rules), Prettier | MIT | Spotless, Error Prone |
   | Workbench | Storybook 10 (later) | MIT | |
   | End-to-end, visual regression | Playwright (later) | Apache-2.0 | Selenium |

   Slice 1 installs only what its screens use; dockview, Motion, ECharts and TanStack Table/Virtual
   come in with the first screen that needs them.

   Lightweight Charts' licence requires its NOTICE attribution and a link to tradingview.com on a page
   users can see: we keep the chart's `attributionLogo` on and list notices on an About page.
   Three.js / React Three Fiber (MIT) stay out until a view truly needs 3D.

3. **Look and feel: an AI research cockpit, original by rule.** Mock-up: `docs/ui/mockup.html`. We do not
   copy broker or charting-platform layouts: no watchlist-chart-order-ticket screen, no market depth,
   no buy or sell buttons (agents are read-only and a human approves every trade, ADR 0005). Signature
   elements:
   - **Command center**: a live data-pipeline map (feeds, loaders, validator, store, resolver, LLM
     gateway, with animated flow and per-node status); a 24-hour IST job timeline with a "now" line and
     live progress; an LLM budget "energy ring" marking the 60/75/85/100 rules of ADR 0006; a resolver
     that shows how it decided (alias, symbol, name, fuzzy); a plain-language system narrative; the
     instrument universe by series.
   - **Instrument dossier** in place of a trading screen: chart with analysis lenses, a data-provenance
     strip (source, per-bar validation quality, freshness against the trading calendar) and a read-only
     copilot panel. Model text is labelled "model output, unverified"; numbers it cites come from Java
     and are labelled "computed".
   - **Command palette** (Ctrl K) to jump, run dev jobs, resolve names and ask the copilot; dockable
     panels with saved layouts; toasts for job outcomes.
   - Visual system: dark-first with light mode from one set of tokens; near-black navy with a slow
     cyan/violet/pink aura (canvas), glass panels, hairline borders; Inter for text, JetBrains Mono with
     tabular figures for every number; motion 150 to 600 ms and off under `prefers-reduced-motion`.
     Synthetic data and fictional names are always labelled.
   - Considered and not chosen: an immersive concept reel (3D "market galaxy" in WebGL, an AI that
     composes the screen, an agent debate room, a replay time machine). The owner preferred the
     cockpit. It stays in git history (commit 63d7dbf, `docs/ui/concepts.html`) for later ideas.
4. **Backend contract**
   - REST and JSON over **same origin**. In development Vite proxies `/api` and `/dev` to
     `localhost:8080`, so no CORS. In production Spring Boot (or a reverse proxy) serves the built
     files from the same origin; packaging is a later ADR.
   - **Server-Sent Events** for streaming: copilot tokens, job progress, alerts, later prices.
     WebSockets only if we need two-way traffic.
   - Every response is parsed with Zod at the edge. Model output is shown as text only and can never
     trigger an action (CLAUDE.md, eval case n21).
   - Auth deferred, planned as a server session cookie with CSRF protection (Spring Security), not
     tokens in browser storage. Until then the UI is for localhost only.
   - Types are hand-written in `ui/src/api` now; later generated from the backend's OpenAPI document.
5. **The UI calls `/dev` endpoints for now** (dev profile only) and switches to `/api/v1` in one place
   (`ui/src/api`) once the backend has it.

### `/api/v1` endpoints the backend should add (not built here)
- `GET /api/v1/system/status`: profile, version, market status from `TradingCalendar` (open, closed,
  holiday, next session), calendar coverage.
- `GET /api/v1/pipeline`: each stage of the data pipeline with status, last run and counts (feeds the map).
- `GET /api/v1/jobs` (schedule, last run, next run, health), `GET /api/v1/jobs/runs?job=&from=&to=` and
  `POST /api/v1/jobs/{name}/run` behind auth; `GET /api/v1/jobs/events` (SSE progress).
- `GET /api/v1/instruments?query=&series=&page=`, `GET /api/v1/instruments/{id}`,
  `GET /api/v1/instruments/series-counts`.
- `GET /api/v1/instruments/resolve?name=` returning a tagged result (`"type": "RESOLVED" | "AMBIGUOUS" | "UNRESOLVED"`)
  plus the decision trace. Today's `Resolution` JSON has no type field, so the UI tells cases apart by
  which fields are present.
- `GET /api/v1/candles?instrumentId=&interval=&from=&to=` with prices as strings (BigDecimal precision
  kept), `source`, per-bar validation warnings, and freshness (last bar versus last trading day).
- `GET /api/v1/indicators?instrumentId=&kind=` computed in Java (SMA, volatility) for chart lenses.
- `GET /api/v1/llm/spend`, `GET /api/v1/llm/budget` (tiers, thresholds).
- `POST /api/v1/copilot/ask` streaming tokens over SSE through `LlmGateway`, read-only, with the
  computed facts it was given returned separately.
- `GET /api/v1/events` (SSE): alerts and the system narrative.
- OpenAPI document at `/api/v1/openapi.json`.

## Consequences
- One PR can change an endpoint and its screen together. CI gains a Node job.
- More libraries than a minimal UI, each with a clear job; the owner learns TypeScript, React hooks
  and npm. CLAUDE.md lists the commands.
- Prices arrive from Java as JSON numbers today; the UI only displays them and never computes money.
  String decimals in `/api/v1` remove the float risk.
- Several signature panels (pipeline map, copilot, narrative) need `/api/v1` endpoints; until then
  they show what the dev endpoints allow, or a clearly marked "coming" state.
- Transitive licences (checked from `ui/package-lock.json` on 2026-11-26): all permissive (MIT, ISC,
  Apache-2.0, BSD, 0BSD, BlueOak, CC0, Unlicense) except build-time tools that never ship in the
  bundle: lightningcss (MPL-2.0, Tailwind's CSS compiler) and caniuse-lite (CC-BY-4.0 data).
- jsdom is pinned to 29.x because 30 and later need Node 24.15+; move up with Node.
- Revisit: production packaging, auth, OpenAPI type generation, Storybook, Playwright, 3D.
