import { describe, it, expect } from "vitest";
import { buildPublicTrustHeaders } from "../src/headerBuilder";
import { TRUST_HEADERS, HARD_REQUIRED_HEADERS } from "../src/headers";

const CTX = { tenantId: "00000000-0000-0000-0000-000000000001", podId: "national-spine" };

describe("buildPublicTrustHeaders", () => {
  it("emits the four hard-required platform headers", () => {
    const headers = buildPublicTrustHeaders(CTX);
    for (const name of HARD_REQUIRED_HEADERS) {
      expect(headers[name], `missing ${name}`).toBeTruthy();
    }
    expect(headers[TRUST_HEADERS.TENANT_ID]).toBe(CTX.tenantId);
    expect(headers[TRUST_HEADERS.POD_ID]).toBe(CTX.podId);
  });

  it("NEVER attaches an Authorization header or actor identity (anonymous)", () => {
    const headers = buildPublicTrustHeaders(CTX, { method: "POST" });
    expect(headers[TRUST_HEADERS.AUTHORIZATION]).toBeUndefined();
    expect(headers.authorization).toBeUndefined();
    expect(headers[TRUST_HEADERS.ACTOR_ID]).toBeUndefined();
    expect(headers[TRUST_HEADERS.ACTOR_TYPE]).toBeUndefined();
    expect(headers[TRUST_HEADERS.PROVIDER_ID]).toBeUndefined();
  });

  it("generates fresh request/correlation ids and honours a supplied correlationId", () => {
    const a = buildPublicTrustHeaders(CTX);
    const b = buildPublicTrustHeaders(CTX);
    expect(a[TRUST_HEADERS.REQUEST_ID]).not.toBe(b[TRUST_HEADERS.REQUEST_ID]);

    const fixed = buildPublicTrustHeaders(CTX, { correlationId: "corr-fixed" });
    expect(fixed[TRUST_HEADERS.CORRELATION_ID]).toBe("corr-fixed");
    // request id is always independent of the caller-supplied correlation id
    expect(fixed[TRUST_HEADERS.REQUEST_ID]).not.toBe("corr-fixed");
  });

  it("adds an idempotency key only on command methods", () => {
    expect(buildPublicTrustHeaders(CTX, { method: "GET" })[TRUST_HEADERS.IDEMPOTENCY_KEY]).toBeUndefined();
    expect(buildPublicTrustHeaders(CTX, { method: "POST" })[TRUST_HEADERS.IDEMPOTENCY_KEY]).toBeTruthy();
    expect(buildPublicTrustHeaders(CTX, { method: "PUT" })[TRUST_HEADERS.IDEMPOTENCY_KEY]).toBeTruthy();
  });

  it("carries purpose-of-use and client timeout only when provided", () => {
    const bare = buildPublicTrustHeaders(CTX);
    expect(bare[TRUST_HEADERS.PURPOSE_OF_USE]).toBeUndefined();
    expect(bare[TRUST_HEADERS.CLIENT_TIMEOUT_MS]).toBeUndefined();

    const rich = buildPublicTrustHeaders(
      { ...CTX, purposeOfUse: "PUBLIC_HEALTH" },
      { clientTimeoutMs: 15000 }
    );
    expect(rich[TRUST_HEADERS.PURPOSE_OF_USE]).toBe("PUBLIC_HEALTH");
    expect(rich[TRUST_HEADERS.CLIENT_TIMEOUT_MS]).toBe("15000");
  });

  it("always sets a JSON content-type", () => {
    expect(buildPublicTrustHeaders(CTX)["content-type"]).toBe("application/json");
  });
});
