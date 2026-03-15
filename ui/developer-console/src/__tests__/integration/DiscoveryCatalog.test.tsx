/**
 * Discovery & Catalog Integration Tests
 *
 * Verifies API discovery, schema catalog, and event catalog
 * functionality through the developer portal and schema registry APIs.
 */

describe("Discovery & Catalog", () => {
  beforeEach(() => {
    global.fetch = jest.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";

      // Discovery endpoint
      if (url.includes("/discovery") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              service: "developer-portal-service",
              version: "1.0.0",
              endpoints: [
                { method: "POST", path: "/internal/v1/developer/clients", description: "Register a partner client" },
                { method: "GET", path: "/internal/v1/developer/clients", description: "List registered clients" },
                { method: "POST", path: "/internal/v1/developer/clients/{id}/keys", description: "Issue API key" },
                { method: "POST", path: "/internal/v1/developer/keys/{id}/rotate", description: "Rotate API key" },
                { method: "DELETE", path: "/internal/v1/developer/keys/{id}", description: "Revoke API key" },
                { method: "POST", path: "/internal/v1/developer/clients/{id}/certify", description: "Run certification checks" },
                { method: "GET", path: "/internal/v1/developer/clients/{id}/federation-readiness", description: "Check federation readiness" },
              ],
            },
            correlationId: "corr-disc1",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 200 },
        );
      }

      // Schema catalog
      if (url.includes("/schemas/catalog") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [
                {
                  subject: "impilo.vito.patient.created.v1",
                  compatibility: "BACKWARD",
                  description: "Patient registration event",
                  version_count: 3,
                  latest_version: 3,
                  latest_fingerprint: "abc123def456",
                  latest_created_at: "2026-03-10T10:00:00Z",
                  service: "vito",
                  entity: "patient",
                  action: "created",
                },
                {
                  subject: "impilo.developer-portal.client.registered.v1",
                  compatibility: "BACKWARD",
                  description: "Developer client registered",
                  version_count: 1,
                  latest_version: 1,
                  latest_fingerprint: "xyz789",
                  latest_created_at: "2026-03-15T10:00:00Z",
                  service: "developer-portal",
                  entity: "client",
                  action: "registered",
                },
              ],
              count: 2,
              catalog_type: "event_schema",
              generated_at: "2026-03-15T10:00:00Z",
            },
            correlationId: "corr-cat1",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 200 },
        );
      }

      // Schema subjects list
      if (url.includes("/schemas/subjects") && !url.includes("/versions") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [
                { subject: "impilo.vito.patient.created.v1", compatibility: "BACKWARD", description: "Patient registration" },
              ],
              count: 1,
            },
            correlationId: "corr-sub1",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 200 },
        );
      }

      // Compatibility check
      if (url.includes("/schemas/compatibility") && method === "POST") {
        return new Response(
          JSON.stringify({
            success: true,
            data: { compatible: true, violations: [] },
            correlationId: "corr-comp1",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 200 },
        );
      }

      return new Response(
        JSON.stringify({ success: true, data: {}, correlationId: "c", timestamp: "t" }),
        { status: 200 },
      );
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test("fetches API discovery with all endpoints", async () => {
    const { getDiscovery } = await import("@/lib/developerApi");

    const result = await getDiscovery();

    expect(result.service).toBe("developer-portal-service");
    expect(result.version).toBe("1.0.0");
    expect(result.endpoints.length).toBeGreaterThanOrEqual(7);

    const methods = result.endpoints.map((e) => e.method);
    expect(methods).toContain("POST");
    expect(methods).toContain("GET");
    expect(methods).toContain("DELETE");
  });

  test("fetches schema catalog with parsed service/entity/action", async () => {
    const { getSchemaCatalog } = await import("@/lib/developerApi");

    const result = await getSchemaCatalog();

    expect(result.items).toHaveLength(2);

    const patientSchema = result.items.find((s) => s.subject.includes("patient"));
    expect(patientSchema).toBeDefined();
    expect(patientSchema?.service).toBe("vito");
    expect(patientSchema?.entity).toBe("patient");
    expect(patientSchema?.action).toBe("created");
    expect(patientSchema?.version_count).toBe(3);
    expect(patientSchema?.latest_version).toBe(3);
  });

  test("catalog includes developer-portal events", async () => {
    const { getSchemaCatalog } = await import("@/lib/developerApi");

    const result = await getSchemaCatalog();

    const devPortalSchema = result.items.find((s) => s.subject.includes("developer-portal"));
    expect(devPortalSchema).toBeDefined();
    expect(devPortalSchema?.service).toBe("developer-portal");
    expect(devPortalSchema?.entity).toBe("client");
  });

  test("lists schema subjects with compatibility mode", async () => {
    const { listSchemaSubjects } = await import("@/lib/developerApi");

    const result = await listSchemaSubjects();

    expect(result.items).toHaveLength(1);
    expect(result.items[0].compatibility).toBe("BACKWARD");
  });

  test("checks schema compatibility returns result and violations", async () => {
    const { checkSchemaCompatibility } = await import("@/lib/developerApi");

    const result = await checkSchemaCompatibility(
      "impilo.vito.patient.created.v1",
      '{"type":"object","properties":{"id":{"type":"string"},"name":{"type":"string"}}}',
    );

    expect(result.compatible).toBe(true);
    expect(result.violations).toEqual([]);
  });

  test("discovery endpoint lists certification and federation endpoints", async () => {
    const { getDiscovery } = await import("@/lib/developerApi");

    const result = await getDiscovery();

    const paths = result.endpoints.map((e) => e.path);
    expect(paths.some((p) => p.includes("certify"))).toBe(true);
    expect(paths.some((p) => p.includes("federation-readiness"))).toBe(true);
  });
});
