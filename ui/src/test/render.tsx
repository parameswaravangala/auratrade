import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { StrictMode } from "react";
import { testRouter } from "@/app/router";

/** Renders the whole app at `path` against the fake backend in ./server, in StrictMode like main.tsx. */
export function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = testRouter(path);
  const utils = render(
    <StrictMode>
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
  return { ...utils, router, client };
}
