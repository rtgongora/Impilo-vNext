/**
 * Diagnostics Linkage Tests — Trust headers, correlation IDs, and idempotency.
 *
 * Validates that the API client injects the correct trust headers from
 * the session store on every outbound request, including correlation IDs
 * on all requests and idempotency keys on mutations.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock crypto.randomUUID to return predictable values
const mockUUIDs = ["corr-id-1111", "idemp-key-2222", "corr-id-3333", "idemp-key-4444"];
let uuidIndex = 0;
vi.stubGlobal("crypto", {
  randomUUID: () => mockUUIDs[uuidIndex++ % mockUUIDs.length],
});

// Mock fetch globally
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Mock the session store with controllable state
const mockSessionState = {
  session: null as {
    accessToken: string;
    actorId: string;
    actorType: string;
  } | null,
  workContext: null as {
    tenantId: string;
    facilityId?: string;
  } | null,
  purposeOfUse: "OPERATIONS",
};

vi.mock("@/stores/sessionStore", () => ({
  useSupportSession: {
    getState: () => mockSessionState,
  },
}));

vi.mock("shared-ui", () => ({
  TRUST_HEADERS: {
    TENANT_ID: "x-tenant-id",
    ACTOR_ID: "x-actor-id",
    ACTOR_TYPE: "x-actor-type",
    PURPOSE_OF_USE: "x-purpose-of-use",
    DEVICE_FINGERPRINT: "x-device-fingerprint",
    CORRELATION_ID: "x-correlation-id",
    FACILITY_ID: "x-facility-id",
    WORKSPACE_ID: "x-workspace-id",
    SHIFT_ID: "x-shift-id",
    DECISION: "x-decision",
    OBLIGATIONS: "x-obligations",
    MAX_SCOPE: "x-max-scope",
    MASK_FIELDS: "x-mask-fields",
    LOGGING_LEVEL: "x-logging-level",
  },
}));

import { apiClient } from "../../lib/apiClient";

function mockSuccessResponse(data: unknown = {}) {
  mockFetch.mockResolvedValue({
    json: () => Promise.resolve({ success: true, data }),
    status: 200,
  });
}

describe("Diagnostics Linkage — Trust Header Injection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    uuidIndex = 0;
    mockSessionState.session = null;
    mockSessionState.workContext = null;
    mockSessionState.purposeOfUse = "OPERATIONS";
  });

  describe("Correlation ID", () => {
    it("sets x-correlation-id header on GET requests", async () => {
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-correlation-id"]).toBe("corr-id-1111");
    });

    it("sets x-correlation-id header on POST requests", async () => {
      mockSuccessResponse({ ticketId: "tkt-1" });

      await apiClient.post("/internal/v1/support/tickets", { title: "Test" });

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-correlation-id"]).toBe("corr-id-1111");
    });

    it("sets x-correlation-id header on PATCH requests", async () => {
      mockSuccessResponse({ ticketId: "tkt-1" });

      await apiClient.patch("/internal/v1/support/tickets/tkt-1", { status: "CLOSED" });

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-correlation-id"]).toBe("corr-id-1111");
    });
  });

  describe("Idempotency key", () => {
    it("sets Idempotency-Key header on POST mutations", async () => {
      mockSuccessResponse({ ticketId: "tkt-1" });

      await apiClient.post("/internal/v1/support/tickets", { title: "Test" });

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["Idempotency-Key"]).toBe("idemp-key-2222");
    });

    it("sets Idempotency-Key header on PATCH mutations", async () => {
      mockSuccessResponse({ ticketId: "tkt-1" });

      await apiClient.patch("/internal/v1/support/tickets/tkt-1", { status: "RESOLVED" });

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["Idempotency-Key"]).toBe("idemp-key-2222");
    });

    it("does NOT set Idempotency-Key on GET requests", async () => {
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["Idempotency-Key"]).toBeUndefined();
    });
  });

  describe("Trust headers from session store", () => {
    it("injects Authorization, actorId, and actorType when session exists", async () => {
      mockSessionState.session = {
        accessToken: "jwt-token-abc",
        actorId: "agent-42",
        actorType: "OPERATOR",
      };
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["Authorization"]).toBe("Bearer jwt-token-abc");
      expect(options.headers["x-actor-id"]).toBe("agent-42");
      expect(options.headers["x-actor-type"]).toBe("OPERATOR");
    });

    it("injects x-tenant-id and x-facility-id from work context", async () => {
      mockSessionState.workContext = {
        tenantId: "tenant-zw",
        facilityId: "fac-harare-01",
      };
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-tenant-id"]).toBe("tenant-zw");
      expect(options.headers["x-facility-id"]).toBe("fac-harare-01");
    });

    it("injects x-purpose-of-use from store", async () => {
      mockSessionState.purposeOfUse = "OPERATIONS";
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-purpose-of-use"]).toBe("OPERATIONS");
    });

    it("omits session headers when no session is set", async () => {
      mockSessionState.session = null;
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["Authorization"]).toBeUndefined();
      expect(options.headers["x-actor-id"]).toBeUndefined();
      expect(options.headers["x-actor-type"]).toBeUndefined();
    });

    it("omits facility ID when work context has no facilityId", async () => {
      mockSessionState.workContext = {
        tenantId: "tenant-zw",
      };
      mockSuccessResponse({ items: [] });

      await apiClient.get("/internal/v1/support/tickets");

      const [, options] = mockFetch.mock.calls[0];
      expect(options.headers["x-tenant-id"]).toBe("tenant-zw");
      expect(options.headers["x-facility-id"]).toBeUndefined();
    });
  });
});
