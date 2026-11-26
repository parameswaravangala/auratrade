import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
} from "@tanstack/react-router";
import { z } from "zod";
import { Shell } from "@/components/Shell";
import { ErrorState } from "@/components/States";
import { About } from "@/features/about/About";
import { CommandCenter } from "@/features/command-center/CommandCenter";
import { ComingSoon } from "@/features/coming/ComingSoon";
import { Dossier } from "@/features/dossier/Dossier";
import { PlaybookLab } from "@/features/playbooks/PlaybookLab";
import { RANGES, type RangeKey } from "@/lib/candles";

const rootRoute = createRootRoute({ component: Shell });

const CommandCenterSearch = z.object({ q: z.string().optional().catch(undefined) });
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  validateSearch: (s) => CommandCenterSearch.parse(s),
  component: CommandCenter,
});

const rangeKeys = Object.keys(RANGES) as [RangeKey, ...RangeKey[]];
const DossierSearch = z.object({
  symbol: z
    .string()
    .trim()
    .toUpperCase()
    .regex(/^[A-Z0-9&_.-]{1,30}$/)
    .optional()
    .catch(undefined),
  range: z.enum(rangeKeys).optional().catch(undefined),
});
export type DossierSearch = z.infer<typeof DossierSearch>;
const dossierRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/dossier",
  validateSearch: (s) => DossierSearch.parse(s),
  component: Dossier,
});

const PlaybookSearch = z.object({
  screen: z
    .string()
    .regex(/^[a-z0-9-]{1,60}$/)
    .optional()
    .catch(undefined),
});
const playbooksRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/playbooks",
  validateSearch: (s) => PlaybookSearch.parse(s),
  component: PlaybookLab,
});

const coming = (path: "/research" | "/approvals" | "/portfolio" | "/agents" | "/calendar") =>
  createRoute({ getParentRoute: () => rootRoute, path, component: () => <ComingSoon path={path} /> });

const aboutRoute = createRoute({ getParentRoute: () => rootRoute, path: "/about", component: About });

const routeTree = rootRoute.addChildren([
  indexRoute,
  dossierRoute,
  coming("/research"),
  playbooksRoute,
  coming("/approvals"),
  coming("/portfolio"),
  coming("/agents"),
  coming("/calendar"),
  aboutRoute,
]);

export function makeRouter(history?: RouterHistory) {
  return createRouter({
    routeTree,
    defaultPreload: "intent",
    // A screen that throws shows an error inside the shell instead of blanking the app.
    defaultErrorComponent: ({ error, reset }) => <ErrorState error={error} onRetry={reset} />,
    defaultNotFoundComponent: () => <ErrorState error={new Error("No such screen.")} />,
    ...(history ? { history } : {}),
  });
}

/** For tests: a router that starts at `path` without touching the browser URL. */
export const testRouter = (path: string) => makeRouter(createMemoryHistory({ initialEntries: [path] }));

export const router = makeRouter();

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
