/**
 * Key Rotation Integration Tests
 *
 * Verifies API key issuance, rotation, and revocation flows through
 * the developer portal API.
 */

describe("Key Management", () => {
  const CLIENT_ID = "c001-test-uuid";
  let callLog: { url: string; method: string; body?: Record<string, unknown> }[];

  beforeEach(() => {
    callLog = [];

    global.fetch = jest.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      callLog.push({ url, method, body });

      // Issue key
      if (url.includes("/keys") && method === "POST" && !url.includes("/rotate")) {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              key_id: "k001-test-uuid",
              api_key: "imp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef",
              key_prefix: "imp_ABCD",
              label: body?.label ?? "default",
              status: "ACTIVE",
              created_at: "2026-03-15T10:00:00Z",
            },
            correlationId: "corr-k1",
            timestamp: "2026-03-15T10:00:00Z",
          }),
          { status: 201 },
        );
      }

      // Rotate key
      if (url.includes("/rotate") && method === "POST") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              key_id: "k002-rotated-uuid",
              api_key: "imp_ROTATEDKEYVALUEabcdefghijklmnop",
              key_prefix: "imp_ROTA",
              label: body?.label ?? "rotated",
              status: "ACTIVE",
              rotated_from: "k001-test-uuid",
              created_at: "2026-03-15T11:00:00Z",
            },
            correlationId: "corr-k2",
            timestamp: "2026-03-15T11:00:00Z",
          }),
          { status: 201 },
        );
      }

      // Revoke key
      if (url.includes("/keys/") && method === "DELETE") {
        return new Response(
          JSON.stringify({
            success: true,
            data: { key_id: "k001-test-uuid", status: "REVOKED" },
            correlationId: "corr-k3",
            timestamp: "2026-03-15T12:00:00Z",
          }),
          { status: 200 },
        );
      }

      // List keys
      if (url.includes("/keys") && method === "GET") {
        return new Response(
          JSON.stringify({
            success: true,
            data: {
              items: [
                { key_id: "k001-test-uuid", key_prefix: "imp_ABCD", label: "Production", status: "ACTIVE", created_at: "2026-03-15T10:00:00Z" },
                { key_id: "k000-old-uuid", key_prefix: "imp_OLD_", label: "Legacy", status: "ROTATED", created_at: "2026-01-01T00:00:00Z" },
              ],
              count: 2,
            },
            correlationId: "corr-k4",
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

  test("issues a new API key with label and expiry", async () => {
    const { issueKey } = await import("@/lib/developerApi");

    const result = await issueKey(CLIENT_ID, {
      label: "Production Key",
      expiresInDays: 90,
    });

    expect(result.api_key).toMatch(/^imp_/);
    expect(result.api_key.length).toBe(36); // imp_ + 32 chars
    expect(result.key_prefix).toBe("imp_ABCD");
    expect(result.status).toBe("ACTIVE");

    // Verify POST with body
    const issueCall = callLog.find((c) => c.method === "POST" && !c.url.includes("/rotate"));
    expect(issueCall?.body?.label).toBe("Production Key");
    expect(issueCall?.body?.expiresInDays).toBe(90);
  });

  test("rotates an existing key, returns new key and marks old as rotated", async () => {
    const { rotateKey } = await import("@/lib/developerApi");

    const result = await rotateKey("k001-test-uuid", { expiresInDays: 90 });

    expect(result.api_key).toMatch(/^imp_/);
    expect(result.key_id).toBe("k002-rotated-uuid");
    expect(result.rotated_from).toBe("k001-test-uuid");
    expect(result.status).toBe("ACTIVE");

    const rotateCall = callLog.find((c) => c.url.includes("/rotate"));
    expect(rotateCall?.method).toBe("POST");
  });

  test("revokes an API key", async () => {
    const { revokeKey } = await import("@/lib/developerApi");

    const result = await revokeKey("k001-test-uuid");

    expect(result.status).toBe("REVOKED");
    expect(result.key_id).toBe("k001-test-uuid");

    const revokeCall = callLog.find((c) => c.method === "DELETE");
    expect(revokeCall?.url).toContain("k001-test-uuid");
  });

  test("lists all keys for a client", async () => {
    const { listKeys } = await import("@/lib/developerApi");

    const result = await listKeys(CLIENT_ID);

    expect(result.items).toHaveLength(2);
    expect(result.items[0].status).toBe("ACTIVE");
    expect(result.items[1].status).toBe("ROTATED");
  });

  test("key prefix follows imp_ convention", async () => {
    const { issueKey } = await import("@/lib/developerApi");

    const result = await issueKey(CLIENT_ID, { label: "Test" });

    expect(result.key_prefix.startsWith("imp_")).toBe(true);
    expect(result.api_key.startsWith("imp_")).toBe(true);
  });
});
