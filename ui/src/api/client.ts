import type { z } from "zod";
import { ProblemDetail } from "./schemas";

/** A failed call, with the backend's problem detail when it sent one. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type Init = { method?: "GET" | "POST"; body?: string; contentType?: string; signal?: AbortSignal };

/**
 * Fetches JSON from the backend (same origin; Vite proxies in development) and validates it with the
 * given schema. A response that does not match is an error, never silently used.
 */
export async function getJson<S extends z.ZodType>(
  path: string,
  schema: S,
  init: Init = {},
): Promise<z.output<S>> {
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  if (init.contentType) headers["Content-Type"] = init.contentType;
  const res = await fetch(url(path), {
    method: init.method ?? "GET",
    headers,
    body: init.body ?? null,
    signal: init.signal ?? null,
  });
  const text = await res.text();
  const json: unknown = text ? safeParse(text) : null;
  if (!res.ok) throw new ApiError(res.status, problemMessage(res.status, json));
  const parsed = schema.safeParse(json);
  if (!parsed.success) {
    throw new ApiError(
      res.status,
      `Unexpected response from ${path}: ${parsed.error.issues[0]?.message ?? "invalid"}`,
    );
  }
  return parsed.data;
}

/** Same origin as the page: absolute so it also works under Node's fetch in tests. */
const url = (path: string): string => new URL(path, window.location.origin).toString();

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export function problemMessage(status: number, body: unknown): string {
  const p = ProblemDetail.safeParse(body);
  if (p.success && (p.data.detail ?? p.data.title)) return p.data.detail ?? p.data.title ?? "";
  if (status === 404) return "Not found. Is the backend running with SPRING_PROFILES_ACTIVE=dev?";
  if (status === 502 || status === 503 || status === 504 || status === 500) {
    return typeof body === "string" && body.length < 200 && body
      ? body
      : `Backend unavailable (HTTP ${status}). Start it with the dev profile on port 8080.`;
  }
  return `Request failed (HTTP ${status})`;
}
