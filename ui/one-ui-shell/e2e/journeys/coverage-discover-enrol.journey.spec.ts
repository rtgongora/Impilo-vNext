import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

/** Navigate to a path, settling the AuthGuard's client-side consent redirect if it fires. */
async function reach(page: Page, path: string) {
  await gotoAs(page, PERSONAS.learner, path);
  for (let i = 0; i < 3; i += 1) {
    await page.waitForTimeout(1500);
    if (!page.url().includes("/consent")) return;
    for (const box of await page.locator('input[type="checkbox"]').all()) await box.check().catch(() => undefined);
    await page.getByRole("button", { name: /accept and continue/i }).click().catch(() => undefined);
    await page.waitForURL((u) => !u.pathname.startsWith("/consent"), { timeout: 15_000 }).catch(() => undefined);
  }
}

/**
 * Golden journey — Coverage is now discoverable and enrollable.
 *
 * Coverage was fully routed + seeded but had no launcher tile ("can't see it
 * anywhere"). This proves: a citizen reaches My Coverage, opens Enrol, sees the
 * real seeded plans (COV-MOHCC-CORE / COV-PRIVATE-PLUS), all through the real
 * ingress. (Tile visibility is asserted via the Start menu.)
 */
test.describe("Coverage — discover + enrol (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("citizen sees Coverage in the launcher and reaches enrol with real plans", async ({ page }) => {
    await loginAs(page, PERSONAS.learner);

    // Enrol surface RENDERS (no longer a 404 — the .dockerignore fix built the feature).
    await reach(page, "/coverage/enroll");
    await expect(page.getByRole("heading", { name: /enroll in coverage/i })).toBeVisible({ timeout: 20_000 });

    // …and its plan picker carries the REAL seeded plans (proves live coverage-service data).
    await expect(async () => {
      const options = await page.locator("select option").allTextContents();
      expect(options.join(" ")).toMatch(/MOHCC National Core|COV-MOHCC-CORE|Private Medical Aid|COV-PRIVATE-PLUS/i);
    }).toPass({ timeout: 20_000 });

    // My Coverage (the launcher tile target) is reachable.
    await reach(page, "/coverage/member");
    await expect(page.getByRole("heading", { name: /my coverage/i }).first()).toBeVisible({ timeout: 20_000 });

    // The launcher now carries a Coverage tile (was absent → "can't see it").
    const startBtn = page
      .getByRole("button", { name: /^start( menu)?$|open start/i })
      .or(page.locator('[aria-label="Start menu"], [aria-label="Open Start menu"]'))
      .first();
    if (await startBtn.isVisible().catch(() => false)) {
      await startBtn.click();
      await expect(page.getByText(/^Coverage$/).first()).toBeVisible({ timeout: 10_000 });
    }
  });
});
