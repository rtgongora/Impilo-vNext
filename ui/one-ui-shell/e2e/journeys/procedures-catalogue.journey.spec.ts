import { test, expect } from "@playwright/test";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { PERSONAS } from "./personas";
import { AcceptanceChecklist } from "./acceptance";

/**
 * Golden journey — a clinician searches the procedure catalogue and runs an appropriateness
 * pre-check, through the real /work/clinical/procedures surface backed by
 * /internal/v1/procedures/** (experience-bff -> procedures-service).
 *
 * This is the browser-level proof that Wave P-R actually closed the reachability gap: before
 * this wave, procedures-service had no BFF proxy, no envoy route reachable through it, no authz
 * rows and no UI, so six waves of catalogue/appropriateness/competence capability existed and
 * nothing could reach any of it. The headless proof for the platform layer itself is
 * scripts/runtime-proof/procedures-catalogue-journeys.sh (19/19 on real Postgres) and
 * procedures-authz-journeys.sh (7/7, including the two PDP defects this route shape was
 * designed around); this spec proves the browser path on top of that, the same relationship
 * the theatre programme's own journeys have to its runtime-proof rigs.
 *
 * Runs against a live preview with the default catalogue seed (66 definitions, V003). Skip-
 * guarded so it no-ops when no estate is up — RUN_PREVIEW is unset in ordinary CI/dev, matching
 * every other journey spec in this directory.
 */

const CLINICIAN = PERSONAS.doctor;

test.describe.serial("Procedure catalogue and appropriateness check (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("clinician searches the catalogue, opens a lateralised procedure, and is blocked without a side", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("procedures-catalogue");

    await checklist.point("A1_access", "clinician reaches the procedure catalogue", async () => {
      await loginAs(page, CLINICIAN);
      await gotoAs(page, CLINICIAN, "/work/clinical/procedures");
      await expect(page.getByTestId("procedures-catalogue-search-input")).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "the seeded catalogue loads real, actionable entries — not the unavailable banner", async () => {
      // The distinction this whole wave was built to protect: an unreachable catalogue must
      // render as an error banner, never silently as "no procedures found". Asserting the
      // negative first is the point — if this ever regresses to the unavailable banner, the
      // journey should fail loudly here rather than at a later, less legible step.
      await expect(page.getByTestId("procedures-catalogue-unavailable")).not.toBeVisible();
      await page.getByTestId("procedures-catalogue-search-input").fill("hernia");
      await expect(page.getByTestId("procedures-catalogue-item").first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "a lateralised procedure is opened and its requirements name an owner", async () => {
      await page.getByTestId("procedures-catalogue-item").first().click();
      await expect(page.getByTestId("procedures-catalogue-detail")).toBeVisible({ timeout: 20_000 });
      await expect(page.getByTestId("procedures-requirements-list")).toBeVisible();
      // Pipeline §8: every unresolved item has an owner. Checked here, not deferred to a
      // separate point — a requirement with no owner is a defect in what A3 just opened.
      await expect(page.getByTestId("procedures-requirements-list").getByText(/^Owner: /).first())
        .toBeVisible();
    });

    await checklist.point("A4_saves", "the appropriateness check runs and flags the missing side with a named action", async () => {
      // No side selected — the seeded inguinal hernia repair is LATERALISED, so the engine
      // must BLOCK, not warn, per pipeline §5 and the P3 engine's own governing rule.
      await page.getByTestId("procedures-run-appropriateness-check").click();
      await expect(page.getByTestId("procedures-appropriateness-outcome")).toHaveText("BLOCKED", { timeout: 20_000 });
      await expect(page.getByText(/side/i)).toBeVisible();
    });

    await checklist.point("A5_stateChange", "selecting a side changes the verdict away from BLOCKED", async () => {
      await page.getByTestId("procedures-side-select").selectOption("LEFT");
      await page.getByTestId("procedures-run-appropriateness-check").click();
      await expect(page.getByTestId("procedures-appropriateness-outcome")).not.toHaveText("BLOCKED", { timeout: 20_000 });
    });

    await checklist.notApplicable("A6_crossUserVisibility",
      "read/evaluate-only surface — no record is created for a second user to observe");
    await checklist.notApplicable("A7_notification",
      "engine-not-store — nothing is persisted, so no notification is triggered");
    await checklist.notApplicable("A8_audit",
      "no write occurs; the audit trail for a catalogue read is the trust-header access log, not a domain event");
    await checklist.notApplicable("A9_resume",
      "no in-progress record exists to resume — every load is a fresh read");
    await checklist.point("A10_logicalEnd", "the resolved check reaches a definite APPROPRIATE verdict, not merely a non-BLOCKED one", async () => {
      // A5 already proved the outcome moves off BLOCKED. This closes the journey on the
      // stronger, more specific claim: with identity confirmed and a side now recorded, the
      // seeded hernia repair (a core-only catalogue entry — identity, consent, site/side and
      // privilege requirements) has nothing left to detect, so the verdict is exactly
      // APPROPRIATE, not merely "something other than BLOCKED" (which PROCEED_WITH_CLARIFICATION
      // would also satisfy and would be a weaker claim to end on).
      await expect(page.getByTestId("procedures-appropriateness-outcome")).toHaveText("APPROPRIATE", { timeout: 20_000 });
    });

    await checklist.finalize(testInfo);
  });
});
