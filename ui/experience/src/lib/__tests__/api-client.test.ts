import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
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
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("exports apiClient with get, post, put, patch, delete methods", () => {
    expect(apiClient).toBeDefined();
    expect(typeof apiClient.get).toBe("function");
    expect(typeof apiClient.post).toBe("function");
    expect(typeof apiClient.put).toBe("function");
    expect(typeof apiClient.patch).toBe("function");
    expect(typeof apiClient.delete).toBe("function");
  });

  it("sends GET requests to the correct baseURL", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: [] }),
    });

    await apiClient.get("/internal/v1/patients");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8160/internal/v1/patients");
  });

  it("attaches X-Tenant-ID header with default value", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: {} }),
    });

    await apiClient.get("/internal/v1/test");

    const [, options] = mockFetch.mock.calls[0];
    expect(options.headers["X-Tenant-ID"]).toBe("tenant-moh-zw");
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

  it("attaches Authorization header when auth token is in sessionStorage", async () => {
    sessionStorageMock.setItem("exp:auth_token", "my-jwt-token");

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
    expect(url).toBe("http://localhost:8160/internal/v1/test/123");
    expect(options.method).toBe("DELETE");
  });
});
