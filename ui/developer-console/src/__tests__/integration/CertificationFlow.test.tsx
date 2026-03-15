/**
 * Certification Flow Integration Tests
 *
 * Verifies the full certification lifecycle: running checks,
 * viewing results, and browsing certification history.
 */

describe("Certification Flow", () => {
  const CLIENT_ID = "c001-test-uuid";

  beforeEach(() => {
    global.fetch = jest.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";

      // Run certification
      if (url.includes("/certify") && method === "POST") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              certification_id: "cert-001",
              client_id: CLIENT_ID,
              status: "COMPLETED",
              result: "FAIL",
              checks_total: 7,
              checks_passed: 5,
              checks_failed: 2,
              checks: [
                { check: "active_api_key", passed: true, detail: "Client has 2 active key(s)" },
                { check: "contact_email", passed: true, detail: "Contact email configured" },
                { check: "client_active", passed: true, detail: "Client status is ACTIVE" },
                { check: "deprecation_posture_configured", passed: false, detail: "Deprecation posture not configured (NONE)" },
                { check: "sandbox_tested", passed: true, detail: "Sandbox environment used" },
                { check: "key_labelled", passed: true, detail: "API keys have labels" },
                { check: "key_freshness", passed: false, detail: "Some keys older than 90 days" },
              ],
              started_at: "2026-03-15T10:00:00Z",
              completed_at: "2026-03-15T10:00:01Z",
            },
            correlationId: "corr-cert1",
            timestamp: "2026-03-15T10:00:01Z",
          }),
          { status: 201 },
        );
      }

      // List certifications
      if (url.includes("/certifications") && !url.includes("cert-001") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [
                {
                  certification_id: "cert-001",
                  client_id: CLIENT_ID,
                  status: "COMPLETED",
                  result: "FAIL",
                  checks_total: 7,
                  checks_passed: 5,
                  checks_failed: 2,
                  triggered_by: "developer-console",
                  started_at: "2026-03-15T10:00:00Z",
                  completed_at: "2026-03-15T10:00:01Z",
                },
                {
                  certification_id: "cert-000",
                  client_id: CLIENT_ID,
                  status: "COMPLETED",
                  result: "PASS",
                  checks_total: 7,
                  checks_passed: 7,
                  checks_failed: 0,
                  triggered_by: "ci-pipeline",
                  started_at: "2026-03-10T10:00:00Z",
                  completed_at: "2026-03-10T10:00:01Z",
                },
              ],
              count: 2,
            },
            correlationId: "corr-cert2",
            timestamp: "2026-03-15T10:00:01Z",
          }),
          { status: 200 },
        );
      }

      // Get single certification with report
      if (url.includes("/certifications/cert-001") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              certification_id: "cert-001",
              client_id: CLIENT_ID,
              status: "COMPLETED",
              result: "FAIL",
              checks_total: 7,
              checks_passed: 5,
              checks_failed: 2,
              triggered_by: "developer-console",
              started_at: "2026-03-15T10:00:00Z",
              completed_at: "2026-03-15T10:00:01Z",
              report: JSON.stringify({
                checks: [
                  { check: "active_api_key", passed: true, detail: "2 active keys" },
                  { check: "deprecation_posture_configured", passed: false, detail: "NONE" },
                ],
              }),
            },
            correlationId: "corr-cert3",
            timestamp: "2026-03-15T10:00:01Z",
          }),
          { status: 200 },
        );
      }

      return new Response(
        JSON.stringify({ success: true, data: { items: [], count: 0 }, correlationId: "c", timestamp: "t" }),
        { status: 200 },
      );
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test("runs certification and returns check results", async () => {
    const { runCertification } = await import("@/lib/developerApi");

    const result = await runCertification(CLIENT_ID, "developer-console");

    expect(result.certification_id).toBe("cert-001");
    expect(result.status).toBe("COMPLETED");
    expect(result.result).toBe("FAIL");
    expect(result.checks_total).toBe(7);
    expect(result.checks_passed).toBe(5);
    expect(result.checks_failed).toBe(2);
    expect(result.checks).toHaveLength(7);
  });

  test("individual checks report pass/fail with details", async () => {
    const { runCertification } = await import("@/lib/developerApi");

    const result = await runCertification(CLIENT_ID, "test");

    const apiKeyCheck = result.checks.find((c) => c.check === "active_api_key");
    expect(apiKeyCheck?.passed).toBe(true);
    expect(apiKeyCheck?.detail).toContain("active key");

    const postureCheck = result.checks.find((c) => c.check === "deprecation_posture_configured");
    expect(postureCheck?.passed).toBe(false);
    expect(postureCheck?.detail).toContain("NONE");
  });

  test("lists certification history ordered by date", async () => {
    const { listCertifications } = await import("@/lib/developerApi");

    const result = await listCertifications(CLIENT_ID);

    expect(result.items).toHaveLength(2);
    expect(result.items[0].result).toBe("FAIL");
    expect(result.items[1].result).toBe("PASS");
  });

  test("retrieves certification report with parsed checks", async () => {
    const { getCertification } = await import("@/lib/developerApi");

    const result = await getCertification("cert-001");

    expect(result.certification_id).toBe("cert-001");
    expect(result.report).toBeTruthy();
    const report = JSON.parse(result.report!);
    expect(report.checks).toHaveLength(2);
  });

  test("certification checks cover all required categories", async () => {
    const { runCertification } = await import("@/lib/developerApi");

    const result = await runCertification(CLIENT_ID, "test");

    const checkNames = result.checks.map((c) => c.check);
    expect(checkNames).toContain("active_api_key");
    expect(checkNames).toContain("contact_email");
    expect(checkNames).toContain("client_active");
    expect(checkNames).toContain("deprecation_posture_configured");
    expect(checkNames).toContain("sandbox_tested");
    expect(checkNames).toContain("key_labelled");
    expect(checkNames).toContain("key_freshness");
  });
});
