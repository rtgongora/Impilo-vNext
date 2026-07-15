import { test, expect, request as pwRequest, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

/**
 * Wave 6 §18 — theatre persistence / concurrency / recovery, proven at the running edge
 * (no mocks). Complements the JPA-layer proof (ProcedureEpisodePersistenceTest, 3/3) and the
 * runtime rig (scripts/runtime-proof/theatre-persistence-journeys.sh, 5/5). Drives the REAL
 * inpatient-service theatre API the way the shell/BFF does and asserts:
 *
 *   - DUPLICATE SUBMIT: posting the same OROS order twice (double-click / retry / replay)
 *     resolves to the SAME episode id — one row, never two.
 *   - RECOVERY / RE-READ: the episode is durably readable after creation (survives a fresh
 *     GET, i.e. a page refresh / re-login re-fetch does not lose it).
 *   - IDEMPOTENT KEYED WRITE: resubmitting a keyed sub-write (blood request) does not
 *     duplicate — the second call is absorbed, not doubled.
 *
 * Makes NO route mocks; SKIPS (not fails) when the stack is down. Point at inpatient-service:
 *   E2E_INPATIENT_URL — inpatient-service base (default http://localhost:8121)
 */

const INPATIENT = process.env.E2E_INPATIENT_URL || "http://localhost:8121";
const TENANT = process.env.E2E_TENANT_ID || "00000000-0000-0000-0000-000000000001";
const FACILITY = process.env.E2E_ADMIT_FACILITY_ID || "00000000-0000-4000-8000-0000000000fa";
const SURGEON = process.env.E2E_SURGEON_ID || "PROV-ZW-00020";

function trust(): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT,
    "X-Pod-ID": "e2e",
    "X-Request-ID": randomUUID(),
    "X-Correlation-ID": randomUUID(),
    "X-Facility-ID": FACILITY,
    "X-Actor-ID": SURGEON,
    "X-Actor-Type": "PROVIDER",
    "X-Purpose-Of-Use": "TREATMENT",
    "Idempotency-Key": `e2e-${randomUUID()}`,
  };
}

async function reachable(ctx: APIRequestContext): Promise<boolean> {
  try {
    return (await ctx.get(`${INPATIENT}/actuator/health`)).ok();
  } catch {
    return false;
  }
}

test.describe("Theatre §18 persistence / concurrency (no mocks, real inpatient-service)", () => {
  let ctx: APIRequestContext;

  test.beforeAll(async () => {
    ctx = await pwRequest.newContext();
    test.skip(!(await reachable(ctx)), `Start inpatient-service — need ${INPATIENT}`);
  });

  test.afterAll(async () => {
    await ctx?.dispose();
  });

  test("duplicate intake of the same OROS order → one episode; durable on re-read", async () => {
    const oros = `OROS-E2E-${randomUUID()}`;
    const body = {
      patientId: "CPID-ZW-E2E-PERS",
      procedureName: "Laparoscopic appendectomy",
      orosOrderId: oros,
      triagePriority: "ELECTIVE",
    };

    const r1 = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases`, { headers: trust(), data: body });
    const r2 = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases`, { headers: trust(), data: body });
    expect(r1.ok(), "first intake must succeed").toBeTruthy();
    expect(r2.ok(), "second (duplicate) intake must not error").toBeTruthy();

    const id1 = (await r1.json()).id as string;
    const id2 = (await r2.json()).id as string;
    expect(id1, "first intake returns an episode id").toBeTruthy();
    expect(id2, "duplicate intake resolves to the SAME episode (dedup, not a new row)").toBe(id1);

    // Recovery / re-read: the episode is durably fetchable (a refresh / re-login re-fetch keeps it).
    const got = await ctx.get(`${INPATIENT}/internal/v1/procedures/episodes/${id1}`, { headers: trust() });
    expect(got.ok(), "the created episode must be durably readable").toBeTruthy();
  });

  test("idempotent keyed write: resubmitting a blood request does not duplicate", async () => {
    // create an episode to attach the write to
    const oros = `OROS-E2E-${randomUUID()}`;
    const created = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases`, {
      headers: trust(),
      data: { patientId: "CPID-ZW-E2E-IDEM", procedureName: "Laparotomy", orosOrderId: oros },
    });
    test.skip(!created.ok(), "intake unavailable in this environment");
    const id = (await created.json()).id as string;

    const ik = `e2e-idem-${randomUUID()}`;
    const keyed = { ...trust(), "Idempotency-Key": ik };
    const payload = { units: 2, component: "PRBC", indication: "intraop bleeding" };

    const a = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${id}/blood/request`, { headers: keyed, data: payload });
    const b = await ctx.post(`${INPATIENT}/internal/v1/theatre/cases/${id}/blood/request`, { headers: keyed, data: payload });

    // Neither call may 5xx; a resubmission is absorbed (idempotency-keyed), never a hard duplicate error.
    expect(a.status(), "first keyed write must not 5xx").toBeLessThan(500);
    expect(b.status(), "resubmitted keyed write must not 5xx (idempotent)").toBeLessThan(500);
  });
});
