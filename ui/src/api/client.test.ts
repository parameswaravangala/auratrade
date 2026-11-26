import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/test/server";
import { ApiError, getJson } from "./client";
import { SeriesCounts } from "./schemas";

describe("getJson", () => {
  it("returns validated data", async () => {
    await expect(getJson("/dev/marketdata/instruments/series", SeriesCounts)).resolves.toHaveLength(3);
  });

  it("rejects a response that does not match the schema", async () => {
    server.use(http.get("*/dev/marketdata/instruments/series", () => HttpResponse.json([{ series: "EQ" }])));
    await expect(getJson("/dev/marketdata/instruments/series", SeriesCounts)).rejects.toThrow(
      /Unexpected response/,
    );
  });

  it("surfaces the backend's problem detail", async () => {
    server.use(
      http.get("*/dev/marketdata/instruments/series", () =>
        HttpResponse.json(
          { title: "Service Unavailable", detail: "Upstox token missing or expired", status: 503 },
          { status: 503 },
        ),
      ),
    );
    const err = await getJson("/dev/marketdata/instruments/series", SeriesCounts).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(503);
    expect((err as ApiError).message).toBe("Upstox token missing or expired");
  });

  it("explains a missing dev profile", async () => {
    server.use(
      http.get("*/dev/marketdata/instruments/series", () => new HttpResponse(null, { status: 404 })),
    );
    await expect(getJson("/dev/marketdata/instruments/series", SeriesCounts)).rejects.toThrow(
      /SPRING_PROFILES_ACTIVE=dev/,
    );
  });
});
