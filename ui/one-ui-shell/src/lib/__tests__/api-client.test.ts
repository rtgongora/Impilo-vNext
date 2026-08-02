import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { apiClient } from "../api-client";
import { isStepUpRequired } from "../stepUp";
import { TRUST_CONTRACT_VERSION_V1 } from "../../../../../contracts/trust-decision/v1";

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
    get length() { return Object.keys(store).length; },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });

let cookieStore = "exp_has_session=1";
Object.defineProperty(document, "cookie", {
  configurable: true,
  get: () => cookieStore,
  set: (value: string) => {
    cookieStore = value;
  },
});

// Mock crypto.randomUUID
const MOCK_UUID = "test-uuid-1234-5678-abcd";
vi.stubGlobal("crypto", {
  randomUUID: vi.fn(() => MOCK_UUID),
});

// Mock fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("apiClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorageMock.clear();
    useFacilityStore.getState().clearFacility();
    useWorkspaceStore.getState().clearWorkspace();
    useShiftStore.getState().endShift();
    useWorkModeStore.getState().reset();
    useAuthStore.getState().clearAuth();
    cookieStore = "exp_has_session=1";
    window.history.pushState({}, "", "/");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("exports apiClient with get, post, put, patch, delete methods", () => {
    expect(apiClient).toBeDefined();
    expect(typeof apiClient.get).toBe("function");
    expect(typeof apiClient.getText).toBe("function");
    expect(typeof apiClient.post).toBe("function");
    expect(typeof apiClient.put).toBe("function");
    expect(typeof apiClient.patch).toBe("function");
    expect(typeof apiClient.delete).toBe("function");
  });

  it("sends GET requests through the browser-relative BFF path", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: [] }),
    });

    await apiClient.get("/internal/v1/patients");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, options] = mockFetch.mock.calls[0];
    expect(url).toBe("/internal/v1/patients");
    expect(options.credentials).toBe("same-origin");
  });

  it("attaches X-Tenant-ID header with default value", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Tenant-ID"]).toBe("00000000-0000-4000-8000-000000000001");
  });

  it("attaches X-Pod-ID header with default value", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Pod-ID"]).toBe("national-spine");
  });

  it("attaches X-Request-ID header", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Request-ID"]).toBe(MOCK_UUID);
  });

  it("attaches X-Correlation-ID header", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Correlation-ID"]).toBe(MOCK_UUID);
  });

  it("never attaches a browser Authorization header even if legacy state contains a token", async () => {
    useAuthStore.setState({
      user: null,
      token: "my-jwt-token",
      refreshToken: null,
      expiresAt: null,
      isAuthenticated: true,
    });

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Authorization"]).toBeUndefined();
  });

  it("does not attach Authorization header when no token exists", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Authorization"]).toBeUndefined();
  });

  it("attaches Content-Type application/json header", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Content-Type"]).toBe("application/json");
  });

  it("sends POST requests with JSON body", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve({ data: { id: "1" } }),
    });

    const body = { name: "Test Patient" };
    await apiClient.post("/internal/v1/patients", body);

    const [, options] = mockFetch.mock.calls[0];
    expect(options.method).toBe("POST");
    expect(options.body).toBe(JSON.stringify(body));
  });

  it("attaches Idempotency-Key header for POST requests", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.post("/internal/v1/test", { data: "test" });

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Idempotency-Key"]).toBe(MOCK_UUID);
  });

  it("attaches Idempotency-Key header for PUT requests", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.put("/internal/v1/test", { data: "test" });

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Idempotency-Key"]).toBe(MOCK_UUID);
  });

  it("does not attach Idempotency-Key header for GET requests", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["Idempotency-Key"]).toBeUndefined();
  });

  it("uses tenant ID from sessionStorage when available", async () => {
    sessionStorageMock.setItem("exp:tenant_id", "tenant-custom");

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Tenant-ID"]).toBe("tenant-custom");
  });

  it("uses pod ID from sessionStorage when available", async () => {
    sessionStorageMock.setItem("exp:pod_id", "pod-custom");

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Pod-ID"]).toBe("pod-custom");
  });

  it("attaches actor and purpose headers from session state", async () => {
    sessionStorageMock.setItem(
      "exp:auth_user",
      JSON.stringify({ id: "user-123", actorType: "PROVIDER" }),
    );
    sessionStorageMock.setItem("exp:purpose_of_use", "OPERATIONS");

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Actor-ID"]).toBe("user-123");
    expect(options.headers["X-Actor-Type"]).toBe("PROVIDER");
    expect(options.headers["X-Purpose-Of-Use"]).toBe("OPERATIONS");
  });

  it("derives purpose of use from work mode when no explicit purpose is stored", async () => {
    sessionStorageMock.setItem("exp:work_mode", "finance");

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Purpose-Of-Use"]).toBe("PAYMENT");
  });

  it("attaches facility, workspace, and shift headers from stored experience context", async () => {
    sessionStorageMock.setItem("exp:facility", JSON.stringify({ id: "facility-1" }));
    sessionStorageMock.setItem("exp:workspace", JSON.stringify({ id: "workspace-1" }));
    sessionStorageMock.setItem("exp:shift", JSON.stringify({ id: "shift-1" }));

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Facility-ID"]).toBe("facility-1");
    expect(options.headers["X-Workspace-ID"]).toBe("workspace-1");
    expect(options.headers["X-Shift-ID"]).toBe("shift-1");
  });

  it("throws on non-OK responses", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: () => Promise.resolve({ error: { code: "INTERNAL", message: "Server error" } }),
    });

    await expect(apiClient.get("/internal/v1/test")).rejects.toMatchObject({
      status: 500,
      error: { code: "INTERNAL", message: "Server error" },
    });
  });

  it("sends DELETE requests with correct method", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.delete("/internal/v1/test/123");

    const [url, options] = mockFetch.mock.calls[0];
    expect(url).toBe("/internal/v1/test/123");
    expect(options.method).toBe("DELETE");
  });

  it("returns plain text bodies when getText is used", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: () => Promise.resolve("{\"resourceType\":\"Bundle\"}"),
    });

    await expect(apiClient.getText("/internal/v1/summary/ips/CPID-123")).resolves.toBe(
      "{\"resourceType\":\"Bundle\"}",
    );
  });

  it("clears persisted experience continuity on auth failure after refresh fails", async () => {
    sessionStorageMock.setItem("exp:facility", JSON.stringify({ id: "facility-1" }));
    sessionStorageMock.setItem("exp:workspace", JSON.stringify({ id: "workspace-1" }));
    sessionStorageMock.setItem("exp:shift", JSON.stringify({ id: "shift-1" }));
    sessionStorageMock.setItem("exp:work_mode", "clinical");
    sessionStorageMock.setItem("exp:work_mode_context", JSON.stringify({ licenseNumber: "LIC-123" }));
    sessionStorageMock.setItem("exp:purpose_of_use", "TREATMENT");
    window.history.pushState({}, "", "/clinical");

    useFacilityStore.getState().setFacility({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    });
    useWorkspaceStore.getState().setWorkspace({
      id: "workspace-1",
      name: "OPD",
      workspaceType: "CONSULT",
      facilityId: "facility-1",
    });
    useShiftStore.getState().startShift({
      id: "shift-1",
      startedAt: "2026-04-09T08:00:00Z",
      workspaceId: "workspace-1",
      facilityId: "facility-1",
    });
    useWorkModeStore.getState().setMode("clinical", { licenseNumber: "LIC-123" });
    useAuthStore.setState({
      user: null,
      token: "expired-token",
      refreshToken: null,
      expiresAt: "2000-01-01T00:00:00.000Z",
      isAuthenticated: true,
    });

    mockFetch
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ error: { code: "UNAUTHORIZED", message: "Expired" } }),
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ error: { code: "REFRESH_FAILED", message: "Expired" } }),
      });

    await expect(apiClient.get("/internal/v1/test")).rejects.toMatchObject({
      status: 401,
      error: { code: "SESSION_EXPIRED", message: "Session expired" },
    });

    expect(mockFetch.mock.calls[1]?.[0]).toBe("/internal/v1/auth/oidc/session");
    expect(mockFetch.mock.calls[1]?.[1]?.body).toBeUndefined();
    expect(mockFetch.mock.calls[1]?.[1]?.credentials).toBe("same-origin");
    expect(sessionStorageMock.getItem("exp:auth_token")).toBeNull();
    expect(sessionStorageMock.getItem("exp:facility")).toBeNull();
    expect(sessionStorageMock.getItem("exp:workspace")).toBeNull();
    expect(sessionStorageMock.getItem("exp:shift")).toBeNull();
    expect(sessionStorageMock.getItem("exp:work_mode")).toBeNull();
    expect(sessionStorageMock.getItem("exp:purpose_of_use")).toBeNull();
    expect(useFacilityStore.getState().facility).toBeNull();
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(useWorkModeStore.getState().mode).toBe("general");
  });
});

/**
 * Checkpoint 6 — a trust decision must reach the caller as a decision.
 *
 * Before this, the client handled 401 only: a 403 fell through as a generic error, so
 * "you may not", "here is what to do first" and "the trust plane is briefly down" were
 * indistinguishable. These tests fence both directions: a challenge is recognised, and an
 * ordinary error is NOT promoted into one.
 */
type Rejection = Record<string, any>;

/** Await a call that must reject, and hand back the rejection value. */
async function rejection(promise: Promise<unknown>): Promise<Rejection> {
  return promise.then(
    () => {
      throw new Error("expected the request to reject");
    },
    (e) => e as Rejection,
  );
}

describe("apiClient — trust challenges (401 / 403)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorageMock.clear();
    useAuthStore.getState().clearAuth();
    cookieStore = "exp_has_session=1";
    window.history.pushState({}, "", "/");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("surfaces a canonical 403 challenge as a typed error the caller can branch on", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: { get: () => null },
      json: () =>
        Promise.resolve({
          contract_version: TRUST_CONTRACT_VERSION_V1,
          decision: "CONSENT_REQUIRED",
          reason_code: "CONSENT_REQUIRED",
          user_message_key: "trust.consent.required",
          support_reference: "SUP-9",
        }),
    });

    const err = await rejection(apiClient.get("/internal/v1/patients/p-1/records"));

    expect(err.status).toBe(403);
    expect(err.isTrustChallenge).toBe(true);
    expect(err.trustChallenge.kind).toBe("actionable");
    expect(err.trustChallenge.decision).toBe("CONSENT_REQUIRED");
    expect(err.trustChallenge.outcome.support_reference).toBe("SUP-9");
    // Additive: the original body is still spread onto the rejection.
    expect(err.reason_code).toBe("CONSENT_REQUIRED");
  });

  it("recognises the legacy ext_authz 403 that tshepo-authz emits today", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: { get: () => null },
      json: () =>
        Promise.resolve({
          error: "CONSENT_REQUIRED",
          message: "no consent covering this access has been granted yet",
        }),
    });

    const err = await rejection(apiClient.get("/internal/v1/patients/p-1/records"));

    expect(err.trustChallenge.decision).toBe("CONSENT_REQUIRED");
    expect(err.trustChallenge.kind).toBe("actionable");
  });

  it("classifies an unrecognised ext_authz deny as a refusal, not an actionable challenge", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: { get: () => null },
      json: () => Promise.resolve({ error: "RBAC_NO_MATCHING_RULE", message: "denied" }),
    });

    const err = await rejection(apiClient.get("/internal/v1/orders"));

    expect(err.trustChallenge.kind).toBe("refusal");
    expect(err.trustChallenge.decision).toBe("DENY");
  });

  it("does NOT turn an ordinary 403 into a trust challenge", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: { get: () => null },
      json: () =>
        Promise.resolve({
          error: { code: "VALIDATION_FAILED", message: "bad request", request_id: "r-1" },
        }),
    });

    const err = await rejection(apiClient.get("/internal/v1/orders"));

    expect(err.status).toBe(403);
    expect(err.isTrustChallenge).toBeUndefined();
    expect(err.trustChallenge).toBeUndefined();
    expect(err.error.code).toBe("VALIDATION_FAILED");
  });

  it("reads the BFF challenge envelope (error.details.trust_challenge) verbatim", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: { get: () => null },
      json: () =>
        Promise.resolve({
          error: {
            code: "NO_ACTIVE_CONSENT",
            message: "trust.consent.required",
            details: {
              trust_challenge: {
                contract_version: TRUST_CONTRACT_VERSION_V1,
                decision: "CONSENT_REQUIRED",
                reason_code: "NO_ACTIVE_CONSENT",
                user_message_key: "trust.consent.required",
                required_action: "OBTAIN_CONSENT",
                continuation_reference: "cont-42",
              },
            },
            request_id: "req-1",
            correlation_id: "cor-1",
          },
        }),
    });

    const err = await rejection(apiClient.get("/internal/v1/records/x"));

    expect(err.trustChallenge.kind).toBe("actionable");
    expect(err.trustChallenge.outcome.continuation_reference).toBe("cont-42");
  });

  it("recognises a TEMPORARILY_UNAVAILABLE challenge on 503, which is not a permissions failure", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 503,
      headers: { get: () => null },
      json: () =>
        Promise.resolve({
          error: {
            code: "PDP_UNAVAILABLE",
            message: "trust.unavailable",
            details: {
              trust_challenge: {
                contract_version: TRUST_CONTRACT_VERSION_V1,
                decision: "TEMPORARILY_UNAVAILABLE",
                reason_code: "PDP_UNAVAILABLE",
              },
            },
          },
        }),
    });

    const err = await rejection(apiClient.get("/internal/v1/records/x"));

    expect(err.status).toBe(503);
    expect(err.trustChallenge.kind).toBe("unavailable");
  });

  it("leaves an ordinary 503 alone — an outage without a challenge body is not a decision", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 503,
      headers: { get: () => null },
      json: () => Promise.resolve({ error: { code: "UPSTREAM_UNAVAILABLE" } }),
    });

    const err = await rejection(apiClient.get("/internal/v1/records/x"));

    expect(err.status).toBe(503);
    expect(err.trustChallenge).toBeUndefined();
  });

  it("leaves 401 session expiry exactly as before — refresh is still attempted", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: { get: () => null },
        json: () => Promise.resolve({ error: { code: "UNAUTHORIZED", message: "Expired" } }),
      })
      .mockResolvedValueOnce({ ok: false, status: 401, json: () => Promise.resolve({}) });

    const err = await rejection(apiClient.get("/internal/v1/test"));

    expect(mockFetch.mock.calls[1]?.[0]).toBe("/internal/v1/auth/oidc/session");
    expect(err).toMatchObject({
      status: 401,
      error: { code: "SESSION_EXPIRED", message: "Session expired" },
    });
    expect(err.trustChallenge).toBeUndefined();
  });

  it("keeps the legacy 401 step-up short-circuit (no refresh) and adds the typed challenge", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: { get: () => null },
      json: () => Promise.resolve({ decision: "STEP_UP_REQUIRED", stepUpMethods: ["MFA"] }),
    });

    const err = await rejection(apiClient.post("/internal/v1/records/share", {}));

    // No session refresh was attempted — a challenge is not a session expiry.
    expect(mockFetch).toHaveBeenCalledTimes(1);
    // The pre-existing consumer contract still holds.
    expect(isStepUpRequired(err)).toBe(true);
    expect(err.stepUpMethods).toEqual(["MFA"]);
    expect(err.trustChallenge.decision).toBe("STEP_UP_REQUIRED");
    expect(err.trustChallenge.outcome.allowed_authentication_methods).toEqual(["MFA"]);
  });

  it("recognises the ext_authz 401 step-up wire, which the old check missed entirely", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: { get: () => null },
      json: () => Promise.resolve({ error: "STEP_UP_REQUIRED", methods: ["SMS_OTP"] }),
    });

    const err = await rejection(apiClient.get("/internal/v1/records/sensitive"));

    // Old behaviour: isStepUpRequired only reads decision/verdict/errorCode, so this body
    // fell through to session refresh and a login redirect.
    expect(isStepUpRequired(err)).toBe(false);
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(err.trustChallenge.decision).toBe("STEP_UP_REQUIRED");
    expect(err.trustChallenge.outcome.allowed_authentication_methods).toEqual(["SMS_OTP"]);
  });
});
