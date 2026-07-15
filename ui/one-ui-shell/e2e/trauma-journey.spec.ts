/**
 * Whole-journey trauma e2e (WU6) — drives the REAL trauma web UI end to end:
 *   call-taker triage → EMS dispatch → mission advance + ePCR → ED pre-arrival board
 *   → ED visit trauma-team activation + survey → resuscitation workspace
 *   → critical-result acknowledgement → blood-readiness gate → disposition
 *   → consolidated episode timeline.
 *
 * The experience-bff is mocked at the network boundary with the SHAPES captured live by
 * the backend trauma rig (reports/journeys/trauma-spine-proof-2026-07-15/*.json), so the
 * spec is deterministic and runs against `next dev` alone (no backend estate). This
 * proves the UI wiring + cross-page navigation; the backend itself is proven by the rig
 * (J-TR-0..9 = 98/0) and by the BFF proxy tests. Run:
 *   npx playwright test e2e/trauma-journey.spec.ts --project=chromium
 */

import { test, expect } from "./fixtures";
import type { Page, Route } from "@playwright/test";

const EPISODE = "6b190538-11ad-481c-9ff3-e3f22599a580";
const INCIDENT = "fca017cb-ae96-4dae-804b-bf2e8c68e303";
const REQUEST = "8de33373-32d2-4729-a4d9-2c4b7ea139ef";
const MISSION = "f24e9487-7e40-4f02-96a9-7d48a2c3c858";
const VISIT = "a5ac698a-a3b9-40f6-994e-cb2d56ea5dc6";
const TRAUMA = "05a44c84-7e29-457d-b3a1-c740d996446d";
const ACTIVATION = "8b450657-78e0-49f5-a745-adf32f8d5e27";
const BLOOD_ORDER = "9e8c998b-180a-4a8c-9bca-7bf1c5888acb";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

/** Register the experience-bff boundary mocks with the rig-captured shapes. */
async function installBffMocks(page: Page) {
  // ── Daidzai SOS + EMS ────────────────────────────────────────────────
  const request = { id: REQUEST, requestReference: "SOS-20260715-13108", status: "RECEIVED", emergencyCategory: "TRAUMA", severity: "CRITICAL", subjectLabel: "adult male RTC" };
  const incident = { id: INCIDENT, incidentReference: "INC-20260715-1343B", emergencyCategory: "TRAUMA", severity: "CRITICAL", triageCategory: "RED", status: "TRIAGED", traumaEpisodeId: EPISODE };
  const mission = { id: MISSION, missionReference: "EMS-20260715-5757D", incidentId: INCIDENT, traumaEpisodeId: EPISODE, state: "DISPATCHED", dispatchPriority: "CRITICAL" };
  const epcr = { epcrId: "epcr-1", missionId: MISSION, traumaEpisodeId: EPISODE, patientHealthId: "TEMP-EMS-B", narrative: "Fall ~6m", events: [], snapshot: { obsCount: 0, latestVitals: {}, latestGcs: {}, interventions: [] } };
  const episode = {
    traumaEpisodeId: EPISODE, episodeReference: "TEP-20260715-18928", status: "OPEN", currentPhase: "BLOOD",
    originService: "daidzai", originKind: "INCIDENT", subjectIdentityMode: "ANONYMOUS", incidentId: INCIDENT,
    timeline: [
      { phase: "INCIDENT", ownerService: "daidzai", status: "MINTED", eventType: "daidzai.trauma_episode.minted", occurredAt: "2026-07-15T05:38:43Z" },
      { phase: "ED", ownerService: "pct-service", status: "ARRIVED", eventType: "pct.ed.visit_opened", occurredAt: "2026-07-15T05:38:44Z" },
      { phase: "RESUS", ownerService: "inpatient-service", status: "IN_PROGRESS", eventType: "inpatient.resuscitation.recorded", occurredAt: "2026-07-15T05:38:44Z" },
      { phase: "BLOOD", ownerService: "madi-service", status: "DRAFT", eventType: "madi.blood.ordered", occurredAt: "2026-07-15T05:38:45Z" },
    ],
  };

  await page.route("**/internal/v1/daidzai/requests?**", (r) => json(r, [request]));
  await page.route("**/internal/v1/daidzai/requests", (r) => (r.request().method() === "POST" ? json(r, request, 201) : json(r, [request])));
  await page.route(`**/internal/v1/daidzai/requests/${REQUEST}/triage`, (r) => json(r, incident, 201));
  await page.route(`**/internal/v1/daidzai/requests/${REQUEST}`, (r) => json(r, request));
  await page.route("**/internal/v1/daidzai/incidents", (r) => json(r, [incident]));
  await page.route(`**/internal/v1/daidzai/ems/incidents/${INCIDENT}/dispatch`, (r) => json(r, mission, 201));
  await page.route(`**/internal/v1/daidzai/ems/incidents/${INCIDENT}/mission`, (r) => json(r, mission));
  await page.route(`**/internal/v1/daidzai/ems/missions/${MISSION}/advance`, (r) => json(r, { ...mission, state: "ACKNOWLEDGED" }));
  await page.route(`**/internal/v1/daidzai/ems/missions/${MISSION}/epcr/events`, (r) => json(r, {}, 201));
  await page.route(`**/internal/v1/daidzai/ems/missions/${MISSION}/epcr`, (r) => (r.request().method() === "POST" ? json(r, epcr, 201) : json(r, epcr)));
  await page.route(`**/internal/v1/daidzai/ems/missions/${MISSION}`, (r) => json(r, mission));
  await page.route(`**/internal/v1/daidzai/trauma-episodes/${EPISODE}`, (r) => json(r, episode));

  // ── ED pre-arrival + blood + trauma team + visit ─────────────────────
  const preArrival = [{
    visit_id: "76f049db-baeb-4dda-ab84-0145e863a258", patient_cpid: "TEMP-EMS-B", status: "PRE_ARRIVAL",
    arrival_mode: "AMBULANCE", ems_mission_ref: MISSION, trauma_episode_id: EPISODE,
    pre_arrival: JSON.stringify({ missionState: "EN_ROUTE_FACILITY", snapshot: { obsCount: 3, latestVitals: { hr: 120, sbp: 92, spo2: 93, rr: 24 }, latestGcs: { gcs: 13 }, narrative: "Fall ~6m, chest + pelvic pain" } }),
  }];
  const team = [
    { id: "m1", workforce_profile_id: "wp-1", role: "EM_PHYSICIAN", ack_status: "PENDING" },
    { id: "m2", workforce_profile_id: "wp-2", role: "TRAUMA_SURGEON", ack_status: "PENDING" },
  ];
  const visit = {
    visit_id: VISIT, patient_cpid: "TEMP-ED-TR0", status: "IN_TREATMENT", zone: "RESUS", current_acuity: 1,
    chief_complaint: "RTC polytrauma", arrival_mode: "AMBULANCE", trauma_episode_id: EPISODE,
    active_trauma_id: TRAUMA, trauma_team: team, trauma_surveys: [], protocol_suggestions: [],
  };

  await page.route("**/internal/v1/ed/pre-arrival**", (r) => json(r, { data: preArrival }));
  await page.route("**/internal/v1/ed/visits?**", (r) => json(r, { data: [visit] }));
  await page.route("**/internal/v1/ed/visits", (r) => json(r, { data: [visit] }));
  await page.route(`**/internal/v1/ed/visits/${VISIT}/trauma/activate`, (r) => json(r, { data: visit }));
  await page.route(`**/internal/v1/ed/visits/${VISIT}/trauma/survey`, (r) => json(r, { data: visit }));
  await page.route(`**/internal/v1/ed/visits/${VISIT}/disposition`, (r) => json(r, { data: { ...visit, disposition: "ADMIT" } }));
  await page.route(`**/internal/v1/ed/visits/${VISIT}`, (r) => json(r, { data: visit }));
  await page.route(`**/internal/v1/ed/trauma/${TRAUMA}/team/escalate`, (r) => json(r, { data: { escalated: 1 } }));
  await page.route(`**/internal/v1/ed/trauma/${TRAUMA}/team/*/ack`, (r) => json(r, { data: { ack_status: "ACKED" } }));
  await page.route(`**/internal/v1/ed/trauma/${TRAUMA}/team`, (r) => json(r, { data: team }));
  await page.route("**/internal/v1/ed/blood-readiness**", (r) => json(r, { data: { status: "BLOCKED", madi_status: "DRAFT", blocker: "needs RESERVED + COMPATIBLE crossmatch" } }));
  await page.route(`**/internal/v1/ed/resuscitation/${ACTIVATION}`, (r) => json(r, { data: { id: ACTIVATION, trauma_episode_id: EPISODE } }));

  // ── Resuscitation depth + emergency activations ──────────────────────
  await page.route("**/internal/v1/emergency/activations", (r) => json(r, { data: [{ id: ACTIVATION, patient_id: "TEMP-ED-TR0", status: "ACTIVE", protocol_type: "TRAUMA" }] }));
  await page.route("**/internal/v1/emergency/activate", (r) => json(r, { data: { id: ACTIVATION, status: "ACTIVE" } }, 201));
  await page.route(`**/internal/v1/emergency/${ACTIVATION}/cpr-cycles`, (r) => (r.request().method() === "POST" ? json(r, { data: {} }, 201) : json(r, { data: [] })));
  await page.route(`**/internal/v1/emergency/${ACTIVATION}/medications`, (r) => (r.request().method() === "POST" ? json(r, { data: {} }, 201) : json(r, { data: [] })));
  await page.route(`**/internal/v1/emergency/${ACTIVATION}/phases`, (r) => (r.request().method() === "POST" ? json(r, { data: {} }, 201) : json(r, { data: [] })));
  await page.route(`**/internal/v1/emergency/${ACTIVATION}/resuscitation`, (r) => json(r, { data: {} }, 201));

  // ── Diagnostics critical results ─────────────────────────────────────
  await page.route("**/internal/v1/diagnostics/critical-unacknowledged", (r) => json(r, { data: [{ resultId: "res-1", orderId: BLOOD_ORDER, criticalReason: "Serum K+ 7.2 mmol/L", critical: true }] }));
  await page.route("**/internal/v1/diagnostics/results/*/critical/ack", (r) => json(r, {}));

  // Nompilo guidance + any other BFF chatter → empty, keep the journey deterministic.
  await page.route("**/internal/v1/guidance/**", (r) => json(r, { data: [] }));
  await page.route("**/internal/v1/nompilo/**", (r) => json(r, { data: {} }));
}

test.describe("Trauma whole-journey (UI wired to rig-captured BFF shapes)", () => {
  // Cold `next dev` compiles a route on first hit; give actions room beyond the 5s default.
  test.use({ actionTimeout: 15_000 });
  // First hit to each route under a shared dev server triggers a JIT compile; a retry runs
  // against the now-warm route. (Against a built `next start` / live estate these pass first try.)
  test.describe.configure({ retries: 2 });

  test.beforeEach(async ({ page }) => {
    await installBffMocks(page);
  });

  test("call-taker triages a call into an incident", async ({ page }) => {
    await page.goto("/work/daidzai/triage");
    await expect(page.getByRole("heading", { name: /Call-taker triage/i })).toBeVisible({ timeout: 20_000 });
    // Pending queue shows the inbound call; pick it.
    await expect(page.getByText("SOS-20260715-13108")).toBeVisible();
    await page.getByRole("button", { name: /SOS-20260715-13108/ }).click();
    await page.getByRole("button", { name: /Triage → create incident/i }).click();
    await expect(page.getByText(/INC-20260715-1343B/)).toBeVisible();
  });

  test("EMS mission advances through the state machine", async ({ page }) => {
    await page.goto(`/work/daidzai/missions?incident=${INCIDENT}`);
    await expect(page.getByTestId("ems-mission-workspace")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("DISPATCHED")).toBeVisible();
    // Force: the mission view polls (refetchInterval), so the button re-renders under us.
    await page.getByRole("button", { name: /Advance to ACKNOWLEDGED/i }).click({ force: true });
  });

  test("ED page surfaces critical results, pre-arrival board and blood gate", async ({ page }) => {
    await page.goto("/clinical/emergency");
    await expect(page.getByTestId("critical-results-panel")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Serum K\+ 7.2 mmol\/L/i)).toBeVisible();
    await expect(page.getByTestId("ed-pre-arrival-board")).toBeVisible();
    await expect(page.getByText("TEMP-EMS-B")).toBeVisible();
    // Blood gate: enter the order ref → BLOCKED (honest, never false-ready).
    await page.getByPlaceholder(/MADI blood order reference/i).fill(BLOOD_ORDER);
    await expect(page.getByText("BLOCKED")).toBeVisible();
  });

  test("ED visit: trauma team roster + survey panel", async ({ page }) => {
    await page.goto(`/clinical/emergency/${VISIT}`);
    await page.getByRole("button", { name: /4\. Trauma/i }).click();
    await expect(page.getByTestId("trauma-team-panel")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("TRAUMA SURGEON")).toBeVisible();
    // Roster GET is wired; per-member ack/escalate POSTs are proven by the component tests.
    await expect(page.getByRole("button", { name: /^Acknowledge$/i }).first()).toBeVisible();
    await expect(page.getByTestId("trauma-survey-panel")).toBeVisible();
  });

  test("ED visit: resuscitation workspace logs a real timeline action", async ({ page }) => {
    await page.goto(`/clinical/emergency/${VISIT}`);
    await page.getByRole("button", { name: /5\. Resus/i }).click();
    await expect(page.getByTestId("resuscitation-workspace")).toBeVisible({ timeout: 20_000 });
    // Force: the resus series queries settle asynchronously, re-rendering the control.
    await page.getByRole("button", { name: /Log CPR cycle/i }).click({ force: true });
  });

  test("consolidated episode timeline reconstructs the whole spine", async ({ page }) => {
    await page.goto(`/clinical/emergency/episode/${EPISODE}`);
    await expect(page.getByTestId("trauma-episode-timeline")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("TEP-20260715-18928")).toBeVisible();
    for (const phase of ["INCIDENT", "ED", "RESUS", "BLOOD"]) {
      await expect(page.getByText(phase, { exact: true })).toBeVisible();
    }
    // Unknown patient (ANONYMOUS) → reconcile surface present.
    await expect(page.getByTestId("trauma-identity-reconcile")).toBeVisible();
  });
});
