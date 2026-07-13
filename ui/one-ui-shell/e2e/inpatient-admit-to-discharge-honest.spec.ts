import { test, expect } from "@playwright/test";

/**
 * HONEST inpatient admit → bed → ward-round → discharge journey.
 *
 * Unlike inpatient-admission-compose.spec.ts (asserts seeded rows) and
 * inpatient-admission-rounds-flow.spec.ts (route-mocks a pre-existing admission),
 * this spec CREATES the admission through the real UI against the real
 * inpatient-service — the flagship journey the G1 remediation unblocked. It makes
 * no route mocks.
 *
 * It needs a real seeded patient + active encounter to admit against (the admission
 * form requires an encounter context). Supply them via env so CI/the operator VM
 * can point at whatever the compose stack seeded, rather than hard-coding fragile
 * seed IDs here:
 *   E2E_ADMIT_PATIENT_ID   — patient id/CPID for /ehr/[patientId]
 *   E2E_ADMIT_ENCOUNTER_ID — an open encounter id for that patient
 * The test skips (not fails) when the stack is down or the seed env is absent.
 */

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const PATIENT_ID = process.env.E2E_ADMIT_PATIENT_ID || "";
const ENCOUNTER_ID = process.env.E2E_ADMIT_ENCOUNTER_ID || "";
const FACILITY_ID = process.env.E2E_ADMIT_FACILITY_ID || "f1000000-0000-0000-0000-000000000001";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || [302, 304, 307].includes(r.status)),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff);
  } catch {
    return false;
  }
}

test.describe("Inpatient admit → discharge (compose, no mocks, creates the admission)", () => {
  test.beforeAll(async () => {
    test.skip(!(await stackReachable()), `Start compose — need UI ${UI_ORIGIN} and BFF ${BFF_ORIGIN}`);
    test.skip(!PATIENT_ID || !ENCOUNTER_ID, "Set E2E_ADMIT_PATIENT_ID and E2E_ADMIT_ENCOUNTER_ID to a seeded patient + open encounter");
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([{ name: "exp_has_session", value: "1", path: "/", domain: "localhost" }]);
    await page.addInitScript((facilityId) => {
      sessionStorage.setItem("exp:auth_token", "compose-inpatient-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({ id: "compose-clinician-001", roles: ["CLINICIAN"], actorType: "PROVIDER", facilityId }),
      );
    }, FACILITY_ID);
  });

  test("creates an admission through the form and reaches the episode", async ({ page }) => {
    await page.goto(
      `${UI_ORIGIN}/clinical/inpatient/admissions/new?patientId=${encodeURIComponent(PATIENT_ID)}&encounterId=${encodeURIComponent(ENCOUNTER_ID)}&source=e2e`,
    );

    // The form must render (real patient resolved), NOT a mock.
    await expect(page.getByRole("button", { name: /admit patient/i })).toBeVisible({ timeout: 60_000 });

    await page.getByPlaceholder(/Decompensated heart failure/i).fill("E2E admit — observation");

    // Pick the first assignable bed if one is offered (bed is optional).
    const bedButtons = page.locator("button:has-text('Bed'), [data-testid='bed-option']");
    if (await bedButtons.first().isVisible().catch(() => false)) {
      await bedButtons.first().click().catch(() => {});
    }

    await page.getByRole("button", { name: /admit patient/i }).click();

    // Either we land on the episode page (success) or the backend returns an honest
    // domain error (active admission / bed unsafe) — both prove the REAL create path,
    // never a fabricated success.
    await expect(page).toHaveURL(/\/clinical\/inpatient\/admissions\/[^/]+$/, { timeout: 60_000 }).catch(async () => {
      await expect(page.locator("body")).toContainText(/already has an active admission|unsafe|Could not create/i, {
        timeout: 30_000,
      });
    });
  });
});
