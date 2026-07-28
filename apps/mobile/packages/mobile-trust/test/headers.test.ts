import { describe, it, expect } from "vitest";
import {
  TRUST_HEADERS,
  HARD_REQUIRED_HEADERS,
  COMMAND_METHODS,
  buildTrustHeaders,
  validateRequiredHeaders,
  generateId,
  MissingHeaderError,
} from "../src/index";
import type { SessionContext } from "../src/index";

const SESSION: SessionContext = {
  tenantId: "moh-zw",
  podId: "national-spine",
  actorId: "user-123",
  actorType: "PROVIDER",
  accessToken: "tok-abc",
  expiresAt: Date.now() + 3600_000,
  purposeOfUse: "TREATMENT",
};

describe("TRUST_HEADERS", () => {
  it("defines trust header names as lowercase", () => {
    expect(TRUST_HEADERS.TENANT_ID).toBe("x-tenant-id");
    expect(TRUST_HEADERS.CORRELATION_ID).toBe("x-correlation-id");
    expect(TRUST_HEADERS.IDEMPOTENCY_KEY).toBe("idempotency-key");
    expect(TRUST_HEADERS.AUTHORIZATION).toBe("authorization");
    expect(TRUST_HEADERS.WORK_CONTEXT_TOKEN).toBe("x-work-context-token");
    // Ensure lowercase per HTTP/2
    Object.values(TRUST_HEADERS).forEach((h) => {
      expect(h).toBe(h.toLowerCase());
    });
  });

  it("HARD_REQUIRED_HEADERS has 4 entries", () => {
    expect(HARD_REQUIRED_HEADERS).toHaveLength(4);
    expect(HARD_REQUIRED_HEADERS).toContain("x-tenant-id");
    expect(HARD_REQUIRED_HEADERS).toContain("x-pod-id");
    expect(HARD_REQUIRED_HEADERS).toContain("x-request-id");
    expect(HARD_REQUIRED_HEADERS).toContain("x-correlation-id");
  });

  it("COMMAND_METHODS includes POST, PUT, PATCH", () => {
    expect(COMMAND_METHODS).toEqual(["POST", "PUT", "PATCH"]);
  });
});

describe("generateId", () => {
  it("returns a UUID-format string", () => {
    const id = generateId();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  });

  it("generates unique IDs", () => {
    const ids = Array.from({ length: 100 }, () => generateId());
    const unique = new Set(ids);
    expect(unique.size).toBe(100);
  });
});

describe("buildTrustHeaders", () => {
  it("includes all 4 hard-required headers", () => {
    const headers = buildTrustHeaders(SESSION);
    expect(headers["x-tenant-id"]).toBe("moh-zw");
    expect(headers["x-pod-id"]).toBe("national-spine");
    expect(headers["x-request-id"]).toBeTruthy();
    expect(headers["x-correlation-id"]).toBeTruthy();
  });

  it("injects Authorization bearer token", () => {
    const headers = buildTrustHeaders(SESSION);
    expect(headers["authorization"]).toBe("Bearer tok-abc");
  });

  it("injects actor headers", () => {
    const headers = buildTrustHeaders(SESSION);
    expect(headers["x-actor-id"]).toBe("user-123");
    expect(headers["x-actor-type"]).toBe("PROVIDER");
    expect(headers["x-purpose-of-use"]).toBe("TREATMENT");
  });

  it("adds Idempotency-Key for POST", () => {
    const headers = buildTrustHeaders(SESSION, { method: "POST" });
    expect(headers["idempotency-key"]).toBeTruthy();
  });

  it("adds Idempotency-Key for PUT", () => {
    const headers = buildTrustHeaders(SESSION, { method: "PUT" });
    expect(headers["idempotency-key"]).toBeTruthy();
  });

  it("adds Idempotency-Key for PATCH", () => {
    const headers = buildTrustHeaders(SESSION, { method: "PATCH" });
    expect(headers["idempotency-key"]).toBeTruthy();
  });

  it("does NOT add Idempotency-Key for GET", () => {
    const headers = buildTrustHeaders(SESSION, { method: "GET" });
    expect(headers["idempotency-key"]).toBeUndefined();
  });

  it("does NOT add Idempotency-Key for DELETE", () => {
    const headers = buildTrustHeaders(SESSION, { method: "DELETE" });
    expect(headers["idempotency-key"]).toBeUndefined();
  });

  it("includes optional context headers when present", () => {
    const session: SessionContext = {
      ...SESSION,
      facilityId: "fac-1",
      departmentId: "dep-1",
      wardId: "ward-1",
      workspaceId: "ws-1",
      programmeId: "prog-1",
      shiftId: "shift-1",
      accessMode: "ONLINE",
      deviceFingerprint: "fp-abc",
    };
    const headers = buildTrustHeaders(session);
    expect(headers["x-facility-id"]).toBe("fac-1");
    expect(headers["x-department-id"]).toBe("dep-1");
    expect(headers["x-ward-id"]).toBe("ward-1");
    expect(headers["x-workspace-id"]).toBe("ws-1");
    expect(headers["x-programme-id"]).toBe("prog-1");
    expect(headers["x-shift-id"]).toBe("shift-1");
    expect(headers["x-access-mode"]).toBe("ONLINE");
    expect(headers["x-device-fingerprint"]).toBe("fp-abc");
  });

  it("injects x-work-context-token when a minted work-context token is present", () => {
    const session: SessionContext = { ...SESSION, workContextToken: "wct-jwt" };
    const headers = buildTrustHeaders(session);
    expect(headers["x-work-context-token"]).toBe("wct-jwt");
  });

  it("omits x-work-context-token when no work-context token has been minted", () => {
    const headers = buildTrustHeaders(SESSION);
    expect(headers["x-work-context-token"]).toBeUndefined();
  });

  it("accepts custom correlationId", () => {
    const headers = buildTrustHeaders(SESSION, { correlationId: "custom-corr" });
    expect(headers["x-correlation-id"]).toBe("custom-corr");
  });

  it("includes client timeout when specified", () => {
    const headers = buildTrustHeaders(SESSION, { clientTimeoutMs: 5000 });
    expect(headers["x-client-timeout-ms"]).toBe("5000");
  });
});

describe("validateRequiredHeaders", () => {
  it("passes with valid headers", () => {
    const headers = buildTrustHeaders(SESSION);
    expect(() => validateRequiredHeaders(headers)).not.toThrow();
  });

  it("throws MissingHeaderError when x-tenant-id is blank", () => {
    const headers = buildTrustHeaders(SESSION);
    headers["x-tenant-id"] = "   ";
    expect(() => validateRequiredHeaders(headers)).toThrow(MissingHeaderError);
  });

  it("throws MissingHeaderError when x-correlation-id is missing", () => {
    const headers = buildTrustHeaders(SESSION);
    delete headers["x-correlation-id"];
    expect(() => validateRequiredHeaders(headers)).toThrow(MissingHeaderError);
  });
});
