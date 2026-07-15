import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { publicApiClient } from "../src/publicClient";
import { configureApiClient } from "../src/config";
import { ApiError } from "../src/errors";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("publicApiClient (anonymous seam)", () => {
  beforeEach(() => {
    configureApiClient({
      baseUrl: "https://bff.test",
      publicTenantId: "00000000-0000-0000-0000-000000000001",
      publicPodId: "national-spine",
      maxRetries: 0,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sends tenant/pod/request/correlation and NO authorization header", async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ data: { ok: true } }));
    vi.stubGlobal("fetch", fetchMock);

    await publicApiClient.post("/internal/v1/auth/contact/otp/request", { channel: "PHONE", value: "0771" });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://bff.test/internal/v1/auth/contact/otp/request");
    const headers = init.headers as Record<string, string>;

    expect(headers["x-tenant-id"]).toBe("00000000-0000-0000-0000-000000000001");
    expect(headers["x-pod-id"]).toBe("national-spine");
    expect(headers["x-request-id"]).toBeTruthy();
    expect(headers["x-correlation-id"]).toBeTruthy();
    // Command method → idempotency key present
    expect(headers["idempotency-key"]).toBeTruthy();

    // The whole point of the seam: no bearer, no actor identity.
    expect(headers["authorization"]).toBeUndefined();
    expect(headers["x-actor-id"]).toBeUndefined();
    expect(headers["x-actor-type"]).toBeUndefined();
  });

  it("GET carries the four headers and no idempotency key", async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ data: [] }));
    vi.stubGlobal("fetch", fetchMock);

    await publicApiClient.get("/internal/v1/public/gateway/guidance/education");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers["x-tenant-id"]).toBeTruthy();
    expect(headers["x-correlation-id"]).toBeTruthy();
    expect(headers["idempotency-key"]).toBeUndefined();
    expect(headers["authorization"]).toBeUndefined();
  });

  it("unwraps a JSON body without requiring the success envelope", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse({ data: { requestReference: "SOS-9" } })));
    const res = await publicApiClient.post<{ data: { requestReference: string } }>(
      "/internal/v1/public/gateway/sos",
      { callbackNumber: "0771" }
    );
    expect(res.data.data.requestReference).toBe("SOS-9");
    expect(res.status).toBe(200);
  });

  it("maps an error status to a typed ApiError (no retry when configured off)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse({ error: { code: "RATE_LIMITED", message: "slow down" } }, 429))
    );
    await expect(
      publicApiClient.post("/internal/v1/auth/contact/otp/request", {})
    ).rejects.toMatchObject({ code: "RATE_LIMITED", status: 429 });
    await expect(
      publicApiClient.post("/internal/v1/auth/contact/otp/request", {})
    ).rejects.toBeInstanceOf(ApiError);
  });
});
