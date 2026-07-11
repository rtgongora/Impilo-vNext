import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, ensureFacilityContext } from "./honest-auth";

/**
 * Golden journey — an operationalized imported facility is genuinely workable.
 *
 * After the F1 bulk operationalization, every master-pack facility has a
 * default workspace, an OPD service point, derived capabilities, baseline
 * readiness and a materialised PCT queue. The admin refines it through the
 * setup wizard (real queue + workforce truth, honest attestations); a nurse
 * can select an imported facility in the workplace hub and reach the shift
 * step — the facility → workspace → shift chain no longer dead-ends.
 */

test.describe("Facility operations journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("admin refines an operationalized imported facility through the wizard", async ({ page }) => {
    await loginAs(page, PERSONAS.facilityAdmin);
    await ensureFacilityContext(page, PERSONAS.facilityAdmin);

    // Imported facility 6 (Hunyani Municipal Clinic) — setup wizard renders truth.
    await page.goto("/facility/6/setup");
    await expectNoDeadEnd(page);

    // Departments/service-points steps show the provisioned defaults.
    await expect(page.getByText(/service points/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/outpatient department/i).first()).toBeVisible({ timeout: 20_000 });

    // QUEUES step shows real derived queue definitions + the materialise action.
    await expect(page.getByText(/queues/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /materialise live queues/i })).toBeVisible();

    // WORKFORCE step is truth (assignments or an honest empty state), not a toggle.
    await expect(
      page
        .getByText(/no workforce assignments are bound/i)
        .or(page.getByText(/manage assignments in vashandi/i)),
    ).toBeVisible({ timeout: 20_000 });

    // Remaining toggles are honestly labelled attestations.
    await expect(page.getByText(/operational attestations/i)).toBeVisible();

    // Configuration console: services-offered curation is editable.
    await page.goto("/facility/6/configuration");
    await expectNoDeadEnd(page);
    await page.getByRole("button", { name: /services offered/i }).click();
    await expect(
      page.getByText(/outpatient department/i).first().or(page.getByText(/no capabilities recorded/i)),
    ).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /add service/i })).toBeVisible();

    // Infrastructure tab: readiness assessment form is real.
    await page.getByRole("button", { name: /infrastructure/i }).click();
    await expect(page.getByRole("button", { name: /save assessment/i })).toBeVisible({ timeout: 20_000 });
  });

  test("a nurse can start work at an imported facility (hub → workspace → shift)", async ({ page }) => {
    await loginAs(page, PERSONAS.nurse);

    // If the shell drops us on the workplace hub, search for the imported facility.
    const hubVisible = await page
      .getByText(/start work session|workplace selection hub/i)
      .first()
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!hubVisible) {
      // Already in a session — reopen the workplace hub via the context switcher.
      const switcher = page.getByRole("button", { name: /switch context/i }).first();
      if (await switcher.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await switcher.click();
      } else {
        await page.goto("/home");
      }
    }

    // Facility selection surfaces vary (search-first hub, gateway cards, or the
    // context-switcher overlay). Use search when present, then select Hunyani via
    // whichever affordance renders it (link in the overlay, button card elsewhere).
    const search = page.getByPlaceholder(/search.*facilit/i).first();
    if (await search.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await search.fill("Hunyani");
    }
    const facilityEntry = page
      .getByRole("link", { name: /hunyani/i })
      .or(page.getByRole("button", { name: /hunyani municipal clinic(?!.*opd)/i }))
      .first();
    await expect(facilityEntry).toBeVisible({ timeout: 20_000 });
    await facilityEntry.click();

    // Workspace step: the F1 default workspace card exists — clicking it advances.
    const workspaceCard = page.getByRole("button", { name: /hunyani.*opd.*continue/i }).first();
    await expect(workspaceCard).toBeVisible({ timeout: 20_000 });
    await workspaceCard.click();

    // Shift step: Start Session is reachable (the previous dead-end).
    await expect(page.getByRole("button", { name: /start session/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNoDeadEnd(page);
  });
});
