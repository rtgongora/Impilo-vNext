import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

/**
 * HONEST operating-theatre clinical-safety (Wave 1) journey — API-driven, no mocks.
 *
 * Wave 1 is a backend clinical-safety wiring wave (blood/transport/specimen/count/anaesthesia
 * orchestration-by-reference over MADI/NHUME/OROS/RITO). There is no bespoke UI surface yet, so this
 * spec drives the REAL inpatient-service theatre API the same way the shell/BFF does, and asserts the
 * safety guarantees that must never regress:
 *   - a theatre case can be created;
 *   - the readiness BLOOD gate is honestly BLOCKED (never fabricated READY) when no MADI reservation exists;
 *   - a surgical-count discrepancy BLOCKS WHO Sign-Out with a 409 and raises the RITO reference;
 *   - the multi-channel anaesthesia chart returns an ordered series.
 *
 * It makes NO route mocks. It skips (not fails) when the stack is down, mirroring
 * inpatient-admit-to-discharge-honest.spec.ts. Point it at a running inpatient-service:
 *   E2E_INPATIENT_URL   — inpatient-service base (default http://localhost:8121)
 */

const INPATIENT = process.env.E2E_INPATIENT_URL || "http://localhost:8121";
const TENANT = process.env.E2E_TENANT_ID || "00000000-0000-0000-0000-000000000001";
const FACILITY = process.env.E2E_ADMIT_FACILITY_ID || "00000000-0000-4000-8000-0000000000fa";
const SURGEON = process.env.E2E_SURGEON_ID || "PROV-ZW-00020";

function trustHeaders(actor = SURGEON, actorType = "PROVIDER"): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT,
    "X-Pod-ID": "e2e",
    "X-Request-ID": randomUUID(),
    "X-Correlation-ID": randomUUID(),
    "X-Facility-ID": FACILITY,
    "X-Actor-ID": actor,
    "X-Actor-Type": actorType,
    "X-Purpose-Of-Use": "TREATMENT",
    "Idempotency-Key": `e2e-${randomUUID()}`,
  };
}

async function reachable(ctx: APIRequestContext): Promise<boolean> {
  try {
    const r = await ctx.get(`${INPATIENT}/actuator/health`);
    return r.ok();
  } catch {
    return false;
  }
}

test.describe("Theatre clinical-safety (Wave 1, no mocks, real inpatient-service)", () => {
  let ctx: APIRequestContext;

  test.beforeAll(async () => {
    ctx = await pwRequest.newContext();
    test.skip(!(await reachable(ctx)), `Start inpatient-service — need ${INPATIENT}`);
  });

  test.afterAll(async () => {
    await ctx?.dispose();
  });

  async function createCase(): Promise<string> {
    const res = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases`, {
      headers: trustHeaders(),
      data: {
        patientId: `CPID-E2E-${randomUUID().slice(0, 8)}`,
        procedureName: "E2E laparotomy",
        procedureCode: "0DTJ4ZZ",
        triagePriority: "URGENT",
        surgeonId: SURGEON,
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.id).toBeTruthy();
    return body.id as string;
  }

  test("readiness BLOOD gate is honestly BLOCKED without a MADI reservation", async () => {
    const caseId = await createCase();
    const res = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${caseId}/readiness`, {
      headers: trustHeaders(),
      data: { theatreRoomId: randomUUID(), bloodRequired: true },
    });
    expect(res.ok()).toBeTruthy();
    const readiness = await res.json();
    const blood = (readiness.checks as Array<Record<string, unknown>>).find((c) => c.domain === "BLOOD");
    expect(blood, "a BLOOD readiness check must be recorded").toBeTruthy();
    // Never fabricated READY: with no MADI reservation the gate is BLOCKED.
    expect(blood?.status).toBe("BLOCKED");
    expect(readiness.bookable).toBe(false);
  });

  test("blood not required → gate skipped (NOT_REQUIRED)", async () => {
    const caseId = await createCase();
    const res = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${caseId}/readiness`, {
      headers: trustHeaders(),
      data: { theatreRoomId: randomUUID(), bloodRequired: false },
    });
    const readiness = await res.json();
    const blood = (readiness.checks as Array<Record<string, unknown>>).find((c) => c.domain === "BLOOD");
    expect(blood?.status).toBe("NOT_REQUIRED");
  });

  test("surgical-count discrepancy blocks WHO Sign-Out (409) and stores a RITO reference field", async () => {
    const caseId = await createCase();
    const countRes = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${caseId}/counts`, {
      headers: trustHeaders(),
      data: { kind: "SWAB", baselineCount: 10, closingCount: 9 },
    });
    expect(countRes.status()).toBe(201);
    const count = await countRes.json();
    expect(count.discrepancy).toBe(1);
    expect(count.reconciled).toBe(false);
    // rito_case_ref is present as a field (populated when RITO is booted; honest null when down).
    expect(count).toHaveProperty("rito_case_ref");

    // Sign-Out must be blocked while the count is unreconciled.
    const signout = await ctx.post(`${INPATIENT}/internal/v1/procedures/episodes/${caseId}/checklist/SIGN_OUT`, {
      headers: trustHeaders(),
      data: { itemIds: [] },
    });
    expect(signout.status()).toBe(409);

    // An audited override reason lets it proceed.
    const override = await ctx.post(`${INPATIENT}/internal/v1/procedures/episodes/${caseId}/checklist/SIGN_OUT`, {
      headers: trustHeaders(),
      data: { itemIds: [], countOverrideReason: "Imaging confirmed no retained swab" },
    });
    expect(override.ok()).toBeTruthy();
  });

  test("anaesthesia chart returns an ordered multi-channel series", async () => {
    const caseId = await createCase();
    for (const entry of [
      { channel: "AGENT", technique: "GA", label: "Sevoflurane", valueNumeric: 2.0, unit: "%" },
      { channel: "FLUID", label: "Ringers", valueNumeric: 500, unit: "ml" },
    ]) {
      const r = await ctx.post(`${INPATIENT}/internal/v1/procedures/${caseId}/anaesthesia/chart`, {
        headers: trustHeaders(),
        data: entry,
      });
      expect(r.status()).toBe(201);
    }
    const chartRes = await ctx.get(`${INPATIENT}/internal/v1/procedures/${caseId}/anaesthesia/chart`, {
      headers: trustHeaders(),
    });
    expect(chartRes.ok()).toBeTruthy();
    const chart = await chartRes.json();
    const entries = chart.entries as Array<Record<string, unknown>>;
    expect(entries.length).toBeGreaterThanOrEqual(2);
    const times = entries.map((e) => String(e.recorded_at ?? ""));
    expect(times).toEqual([...times].sort());
  });
});
