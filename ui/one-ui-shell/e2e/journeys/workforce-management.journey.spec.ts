import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, ensureFacilityContext, loginAs } from "./honest-auth";

/**
 * Golden journey — workforce management as the HR officer.
 *
 * hr.dziva (HR_OFFICER, PROV-ZW-00013, ACTIVE facility assignment) works the
 * Vashandi surfaces end-to-end: hub entry, assignment lifecycle actions,
 * roster planning, leave decisions and access review. Every surface must be
 * live or honestly degraded — never a blank page or a dead button.
 */

const HR_PERSONA = {
  username: "hr.dziva",
  displayName: "Rumbidzai Dziva",
  roles: ["HR_OFFICER"],
  providerId: "PROV-ZW-00013",
  healthId: "c0000000-0000-4000-8000-000000000015",
  facilityNamePattern: /harare central/i,
};

test.describe("Workforce management journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("HR officer reaches the Vashandi hub and every workspace is live or honest", async ({ page }) => {
    await loginAs(page, HR_PERSONA);
    await ensureFacilityContext(page, HR_PERSONA);

    await page.goto("/work/vashandi");
    await expectNoDeadEnd(page);

    // Assignments: create form + lifecycle actions render (not a bare list).
    await page.goto("/work/vashandi/assignments");
    await expectNoDeadEnd(page);
    await expect(
      page
        .getByText(/no assignments returned/i)
        .or(page.getByTestId("assignment-precheck").first())
        .or(page.getByText(/service degraded|not in your vashandi workspace scope|blocked by trust policy/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Rosters: planning form present (create is real, not view-only).
    await page.goto("/work/vashandi/rosters");
    await expectNoDeadEnd(page);
    await expect(
      page
        .getByTestId("roster-create-form")
        .or(page.getByText(/service degraded|not in your vashandi workspace scope|blocked by trust policy/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Leave: request form + decisions surface (was a read-only list).
    await page.goto("/work/vashandi/leave-availability");
    await expectNoDeadEnd(page);
    await expect(
      page
        .getByTestId("leave-request-form")
        .or(page.getByText(/service degraded|not in your vashandi workspace scope|blocked by trust policy/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Access review: table with actions or an honest empty/degraded state.
    await page.goto("/work/vashandi/access-review");
    await expectNoDeadEnd(page);
    await expect(
      page
        .getByRole("button", { name: /scan now/i })
        .or(page.getByText(/service degraded|not in your vashandi workspace scope|blocked by trust policy/i).first()),
    ).toBeVisible({ timeout: 20_000 });
  });

  test("HR officer discovers Vashandi from the Start menu", async ({ page }) => {
    await loginAs(page, HR_PERSONA);
    await ensureFacilityContext(page, HR_PERSONA);

    const startButton = page
      .getByRole("button", { name: /^start( menu)?$|open start/i })
      .or(page.locator('[aria-label="Start menu"], [aria-label="Open Start menu"]'))
      .first();
    await startButton.click();
    await expect(page.getByText(/launch apps, utilities, and recent work/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/vashandi workforce/i).first()).toBeVisible();
  });
});
