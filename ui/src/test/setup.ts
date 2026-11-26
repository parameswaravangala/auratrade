import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./server";

// findBy* waits up to 5 s (default 1 s): the first render in a test file also loads its modules, which
// can take longer than a second when all files run in parallel.
configure({ asyncUtilTimeout: 5000 });

// jsdom lacks these browser APIs; the UI only needs them to exist.
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  }),
});
class NoopResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = NoopResizeObserver;
HTMLCanvasElement.prototype.getContext = () => null;
Element.prototype.scrollIntoView = () => undefined;
window.scrollTo = () => undefined;

beforeAll(() => {
  server.listen({ onUnhandledFrame: "error" });
});
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => {
  server.close();
});
