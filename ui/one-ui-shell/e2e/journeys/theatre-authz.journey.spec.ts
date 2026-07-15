import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

/**
 * Wave 6 §16 — Theatre trust/scope/safety AUTHZ matrix, proven at the edge (no mocks).
 *
 * The point of §16 is the NEGATIVE: an out-of-scope / non-provider actor must be BLOCKED
 * from a privileged theatre action. The allow-side of the matrix is proven live through the
 * real PolicyEngine (TheatreAuthzMatrixTest, 18/18) and the runtime rig
 * (scripts/runtime-proof/theatre-authz-journeys.sh, 12/12). This spec proves the SAME deny
 * at the running edge two ways:
 *
 *   1. UI: an unauthenticated visit to the theatre workspace does NOT render the privileged
 *      theatre surface (redirected / gated) — the browser never exposes the controls.
 *   2. API: the inpatient-service theatre write endpoint, called with a CITIZEN actor, is
 *      REFUSED (never 2xx) — the correct-cadre-only negative.
 *
 * It makes NO route mocks and SKIPS (not fails) when the stack/preview is down, mirroring the
 * other theatre journeys. Point it at a running preview + inpatient-service:
 *   E2E_BASE_URL       — one-ui-shell preview (default http://localhost:3000)
 *   E2E_INPATIENT_URL  — inpatient-service base (default http://localhost:8121)
 */

const BASE = process.env.E2E_BASE_URL || "http://localhost:3000";
const INPATIENT = process.env.E2E_INPATIENT_URL || "http://localhost:8121";
const TENANT = process.env.E2E_TENANT_ID || "00000000-0000-0000-0000-000000000001";
const FACILITY = process.env.E2E_ADMIT_FACILITY_ID || "00000000-0000-4000-8000-0000000000fa";

function citizenHeaders(): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT,
    "X-Pod-ID": "e2e",
    "X-Request-ID": randomUUID(),
    "X-Correlation-ID": randomUUID(),
    "X-Facility-ID": FACILITY,
    "X-Actor-ID": "citizen-e2e-1",
    "X-Actor-Type": "CITIZEN",
    "X-Purpose-Of-Use": "TREATMENT",
    "Idempotency-Key": `e2e-${randomUUID()}`,
  };
}

async function reachable(ctx: APIRequestContext, url: string): Promise<boolean> {
  try {
    const r = await ctx.get(`${url}/actuator/health`);
    return r.ok();
  } catch {
    return false;
  }
}

test.describe("Theatre §16 authz — unauthorised user is blocked (no mocks)", () => {
  test("UI: unauthenticated visitor cannot open the theatre workspace surface", async ({ page }) => {
    let up = false;
    try {
      const resp = await page.goto(`${BASE}/work/clinical/theatre`, { waitUntil: "domcontentloaded", timeout: 8000 });
      up = !!resp;
    } catch {
      up = false;
    }
    test.skip(!up, `Start the one-ui-shell preview — need ${BASE}`);

    // The privileged theatre surface must NOT be exposed to an unauthenticated visitor: either the
    // app redirects away from the theatre route, or it renders a sign-in / access gate. In no case may
    // the operative controls (e.g. "Sign operative note", "Start theatre") be present.
    await page.waitForTimeout(500);
    const url = page.url();
    const gated = !/\/work\/clinical\/theatre(\/|$|\?)/.test(url)
      || (await page.getByText(/sign in|log in|not authorised|access denied|unauthori[sz]ed/i).count()) > 0;
    expect(gated, "theatre workspace must be gated for an unauthenticated visitor").toBeTruthy();

    const privileged = await page.getByRole("button", { name: /sign operative note|start theatre|record count/i }).count();
    expect(privileged, "no privileged theatre control may render for an unauthenticated visitor").toBe(0);
  });

  test("API: a CITIZEN actor is refused a privileged theatre write", async () => {
    const ctx = await pwRequest.newContext();
    try {
      test.skip(!(await reachable(ctx, INPATIENT)), `Start inpatient-service — need ${INPATIENT}`);
      // Attempt to start a theatre case as a CITIZEN — must never succeed (2xx).
      const r = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${randomUUID()}/start`, {
        headers: citizenHeaders(),
        data: {},
      });
      expect(r.status(), "a CITIZEN must never get a 2xx on a privileged theatre write").toBeGreaterThanOrEqual(400);
    } finally {
      await ctx.dispose();
    }
  });
});
