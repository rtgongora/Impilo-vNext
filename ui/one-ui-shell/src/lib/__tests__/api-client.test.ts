import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { apiClient } from "../api-client";

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

  it("attaches Authorization header from the live auth store token", async () => {
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
    expect(options.headers["Authorization"]).toBe("Bearer my-jwt-token");
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

    expect(mockFetch.mock.calls[1]?.[0]).toBe("/internal/v1/auth/refresh");
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
