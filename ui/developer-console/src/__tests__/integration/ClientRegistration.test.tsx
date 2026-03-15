/**
 * Client Registration Integration Tests
 *
 * Verifies the end-to-end client registration flow through the developer
 * portal API, including trust header injection, client creation, and
 * response validation.
 */

import { TRUST_HEADERS } from "shared-ui";

const BASE = "/internal/v1/developer";

describe("Client Registration", () => {
  let capturedHeaders: Record<string, string>;
  let capturedBody: Record<string, unknown>;

  beforeEach(() => {
    capturedHeaders = {};
    capturedBody = {};

    global.fetch = jest.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      const headers = init?.headers as Record<string, string> | undefined;
      if (headers) {
        capturedHeaders = { ...headers };
      }
      if (init?.body) {
        capturedBody = JSON.parse(init.body as string);
      }

      if (url.endsWith("/clients") && init?.method === "POST") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              client_id: "c001-test-uuid",
              client_name: capturedBody.clientName,
              status: "ACTIVE",
              sandbox_enabled: capturedBody.sandboxEnabled ?? false,
              created_at: "2026-03-15T10:00:00Z",
            },
            correlationId: "corr-001",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 201 },
        );
      }

      if (url.includes("/clients") && init?.method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [
                {
                  client_id: "c001-test-uuid",
                  client_name: "Test Partner",
                  description: "Integration test client",
                  contact_email: "test@example.org",
                  status: "ACTIVE",
                  sandbox_enabled: true,
                  deprecation_posture: "NONE",
                  created_at: "2026-03-15T10:00:00Z",
                },
              ],
              count: 1,
            },
            correlationId: "corr-002",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 200 },
        );
      }

      return new Response(
        JSON.stringify({ success: false, error: { code: "NOT_FOUND", message: "Not found", status: 404 } }),
        { status: 404 },
      );
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test("registers a new client with required fields", async () => {
    const { registerClient } = await import("@/lib/developerApi");

    const result = await registerClient({
      clientName: "DHIS2 Adapter",
      description: "National health data integration",
      contactEmail: "team@dhis2.org",
      sandboxEnabled: true,
    });

    expect(result.client_id).toBe("c001-test-uuid");
    expect(result.client_name).toBe("DHIS2 Adapter");
    expect(result.status).toBe("ACTIVE");
    expect(capturedBody.clientName).toBe("DHIS2 Adapter");
    expect(capturedBody.contactEmail).toBe("team@dhis2.org");
    expect(capturedBody.sandboxEnabled).toBe(true);
  });

  test("injects trust headers on registration request", async () => {
    const { registerClient } = await import("@/lib/developerApi");

    await registerClient({
      clientName: "Test",
      description: "test",
      contactEmail: "a@b.com",
      sandboxEnabled: false,
    });

    expect(capturedHeaders["Content-Type"]).toBe("application/json");
    expect(capturedHeaders[TRUST_HEADERS.CORRELATION_ID]).toBeDefined();
    expect(capturedHeaders[TRUST_HEADERS.PURPOSE_OF_USE]).toBe("OPERATIONS");
    expect(capturedHeaders["Idempotency-Key"]).toBeDefined();
  });

  test("lists registered clients", async () => {
    const { listClients } = await import("@/lib/developerApi");

    const result = await listClients();

    expect(result.items).toHaveLength(1);
    expect(result.items[0].client_name).toBe("Test Partner");
    expect(result.items[0].status).toBe("ACTIVE");
    expect(result.items[0].sandbox_enabled).toBe(true);
  });

  test("uses POST method with idempotency key for registration", async () => {
    const { registerClient } = await import("@/lib/developerApi");

    await registerClient({
      clientName: "Idempotent Client",
      description: "",
      contactEmail: "idem@test.com",
      sandboxEnabled: false,
    });

    const fetchCall = (global.fetch as jest.Mock).mock.calls[0];
    expect(fetchCall[1].method).toBe("POST");
    expect(capturedHeaders["Idempotency-Key"]).toBeTruthy();
  });

  test("uses GET without idempotency key for list", async () => {
    const { listClients } = await import("@/lib/developerApi");

    await listClients();

    const fetchCall = (global.fetch as jest.Mock).mock.calls[0];
    expect(fetchCall[1].method).toBe("GET");
    expect(capturedHeaders["Idempotency-Key"]).toBeUndefined();
  });
});
