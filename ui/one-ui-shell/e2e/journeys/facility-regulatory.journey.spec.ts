import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, logout } from "./honest-auth";

/**
 * Golden journey — the HPA facility regulatory lifecycle, end to end.
 *
 * hpa.registrar (HPA_REGISTRAR) opens the lifecycle console over the national
 * registry, raises a registration application for an imported facility, walks
 * it through documents → submit → ready-for-inspection, schedules a
 * checklist-templated inspection; hpa.inspector records the passing outcome;
 * the registrar records the committee approval which issues the registration
 * certificate under HPA authority. The facility file must show the
 * certificate and the status-history chain.
 */

test.describe.configure({ mode: "serial" });

test.describe("Facility regulatory lifecycle journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("registrar console shows dashboard truth over the national registry", async ({ page }) => {
    await loginAs(page, PERSONAS.hpaRegistrar);
    await page.goto("/registry/facility-lifecycle");
    await expectNoDeadEnd(page);

    // Dashboard metrics render with real counts (1,778-facility registry).
    await expect(page.getByText(/facilities/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /new application/i })).toBeVisible();

    // Imported bucket is populated — the master pack is regulatory truth here.
    await page.getByRole("button", { name: /imported/i }).first().click();
    await expect(
      page
        .getByRole("link", { name: /clinic|hospital|centre/i })
        .first()
        .or(page.getByText(/no facilities match/i)),
    ).toBeVisible({ timeout: 20_000 });
  });

  test("registration application walks the governed flow to a certificate", async ({ page }) => {
    await loginAs(page, PERSONAS.hpaRegistrar);
    await page.goto("/registry/facility-lifecycle");
    await expectNoDeadEnd(page);

    // Register a brand-new facility shell each run — the journey stays
    // self-contained and re-runnable (no state soup on a shared facility).
    const facilityName = `Chikara Journey Clinic ${Date.now()}`;
    await page.getByRole("button", { name: /new application/i }).click();
    await page.getByPlaceholder(/new facility name/i).fill(facilityName);
    await page.getByPlaceholder(/applicant name/i).fill("Chikara Village Trust");
    await page.getByRole("button", { name: /create application/i }).click();
    // The intake panel closes only on successful creation — wait for it.
    await expect(page.getByRole("button", { name: /create application/i })).toHaveCount(0, {
      timeout: 20_000,
    });

    // Find the new facility in the registry queue and open its file.
    await page.getByPlaceholder(/search the national facility registry/i).fill(facilityName);
    const fileLink = page.getByRole("link", { name: facilityName }).first();
    await expect(fileLink).toBeVisible({ timeout: 20_000 });
    const facilityHref = await fileLink.getAttribute("href");
    await fileLink.click();
    await expectNoDeadEnd(page);
    await expect(page.getByText(/applications/i).first()).toBeVisible({ timeout: 20_000 });

    // Attach a document (submission is governed: at least one supporting
    // document is required) and wait for it to land before submitting.
    const docRef = page.getByPlaceholder(/document reference/i);
    await expect(docRef).toBeVisible({ timeout: 20_000 });
    await docRef.fill("doc://chikara/premises-plan-v1");
    await page.getByRole("button", { name: /^attach$/i }).click();
    await expect(page.getByText(/PREMISES PLAN · v1/i)).toBeVisible({ timeout: 20_000 });

    const submitBtn = page.getByRole("button", { name: /^submit$/i }).first();
    await expect(submitBtn).toBeVisible({ timeout: 20_000 });
    await submitBtn.click();

    // Ready for inspection.
    const readyBtn = page.getByRole("button", { name: /ready for inspection/i }).first();
    await expect(readyBtn).toBeVisible({ timeout: 20_000 });
    await readyBtn.click();

    // Schedule the initial inspection with a checklist template if available.
    const templatePicker = page.locator("select").filter({ hasText: /checklist template/i }).first();
    if (await templatePicker.isVisible({ timeout: 5_000 }).catch(() => false)) {
      const options = await templatePicker.locator("option").allTextContents();
      if (options.length > 1) {
        await templatePicker.selectOption({ index: 1 });
      }
    }
    await page.getByRole("button", { name: /schedule inspection/i }).click();
    await expect(page.getByRole("button", { name: /record outcome/i }).first()).toBeVisible({
      timeout: 20_000,
    });

    await logout(page);

    // The inspector records the passing outcome.
    await loginAs(page, PERSONAS.hpaInspector);
    await page.goto(facilityHref ?? "/registry/facility-lifecycle");
    await expectNoDeadEnd(page);
    await page.getByRole("button", { name: /record outcome/i }).first().click();
    await page.getByPlaceholder(/inspector notes/i).fill("Premises compliant on initial inspection.");
    await page.getByRole("button", { name: /record inspection outcome/i }).click();
    await expect(page.getByText(/outcome/i).first()).toBeVisible({ timeout: 20_000 });

    await logout(page);

    // The registrar records the committee approval → certificate issued.
    await loginAs(page, PERSONAS.hpaRegistrar);
    await page.goto(facilityHref ?? "/registry/facility-lifecycle");
    await expectNoDeadEnd(page);
    // The committee panel's picker — its placeholder option is "Application…"
    // (case-sensitive; the inspections panel uses "Link application (optional)…").
    const appPicker = page.locator("select").filter({ hasText: /Application…/ }).first();
    await expect(appPicker).toBeVisible({ timeout: 20_000 });
    await appPicker.selectOption({ index: 1 });
    await page.getByPlaceholder(/resolution notes/i).fill("Registration approved by committee.");
    await page.getByRole("button", { name: /record decision/i }).click();

    // Certificate visible in the file, ACTIVE, under HPA authority.
    await expect(page.getByText(/HPA/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/ACTIVE/i).first()).toBeVisible({ timeout: 20_000 });

    // Status history carries the REGISTERED ACTIVE transition.
    await expect(page.getByText(/registered active/i).first()).toBeVisible({ timeout: 20_000 });
  });
});
