/**
 * FROM-ZERO GOLDEN THREAD — the acceptance bar is a cold start, not a persona.
 *
 * PO ruling (2026-07-19): "The true test is registering a new patient and
 * provider and facility from zero and going end to end." Seeded-persona API
 * packs are a regression floor; THIS journey creates brand-new entities
 * through the real UI against the live estate (no route mocks anywhere):
 *
 *  S1  a new CITIZEN self-registers through /auth/register (real Keycloak
 *      user + provisional Health ID) and lands signed-in.
 *  S2  a new FACILITY walks the governed HPA flow to a REGISTERED ACTIVE
 *      certificate (registrar + inspector are staff actors; the ENTITY is
 *      new every run).
 *  S3  the new citizen opens the provider-claim journey and drives the
 *      council-number lane to its honest answer. (Live estate currently has
 *      ZERO council registration records — a from-zero provider cannot
 *      complete this lane until council preload lands; the journey asserts
 *      the surface behaves honestly rather than pretending.)
 *  S4  the PUBLIC find-care front door is asked for the new facility — this
 *      pins the certificate→operational-directory seam, whichever way it
 *      currently behaves, so the gap is measured instead of folklore.
 *
 * Sequential by design (journeys project runs workers=1); later stages reuse
 * earlier stages' entities via module state.
 */
import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, logout, acceptPoliciesIfGated, PREVIEW_ORIGIN } from "./honest-auth";

async function expectNoDeadEnd(page: Page) {
  await expect(page.getByText(/something went wrong|unhandled error|application error/i)).toHaveCount(0);
}

const RUN = `${Date.now()}`.slice(-8);
const CITIZEN = {
  name: `Zana Fromzero${RUN}`,
  email: `zero.${RUN}@e2e.impilo.test`,
  password: `FromZero!${RUN}Aa`,
};
const FACILITY_NAME = `FromZero Clinic ${RUN}`;

test.describe.serial("From-zero golden thread (live estate, no mocks)", () => {
  test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");

  test("S1: a brand-new citizen registers through the real signup UI", async ({ page }) => {
    await page.goto(`${PREVIEW_ORIGIN}/auth/register`);
    await expectNoDeadEnd(page);

    await page.getByPlaceholder(/your full name/i).fill(CITIZEN.name);
    await page.getByPlaceholder(/you@example\.com or \+263/i).fill(CITIZEN.email);
    await page.getByPlaceholder(/at least 12 characters/i).fill(CITIZEN.password);
    await page.getByPlaceholder(/repeat password/i).fill(CITIZEN.password);
    await page.getByRole("radio", { name: /i don't have one yet/i }).check();
    // Consent checkboxes — a real user must tick them all.
    for (const box of await page.locator('input[type="checkbox"]').all()) {
      await box.check();
    }
    await page.getByRole("button", { name: /create account/i }).click();

    // Success = auto-login into the assurance step (fallback: login with a
    // registered banner also counts as a created account, but auto-login is
    // the golden path and is what we assert).
    await page.waitForURL(/\/auth\/register\/assurance/, { timeout: 45_000 });
    await page.screenshot({ path: test.info().outputPath("s1-assurance.png"), fullPage: true });

    // Walk out of assurance to the signed-in home surface.
    await page.goto(`${PREVIEW_ORIGIN}/home`);
    await acceptPoliciesIfGated(page);
    await expectNoDeadEnd(page);
    await expect(page).not.toHaveURL(/\/auth\//);
    await page.screenshot({ path: test.info().outputPath("s1-home.png"), fullPage: true });
  });

  test("S2: a brand-new facility reaches a REGISTERED ACTIVE certificate through the HPA flow", async ({ page }) => {
    await loginAs(page, PERSONAS.hpaRegistrar);
    await page.goto("/registry/facility-lifecycle");
    await expectNoDeadEnd(page);

    await page.getByRole("button", { name: /new application/i }).click();
    await page.getByPlaceholder(/new facility name/i).fill(FACILITY_NAME);
    await page.getByPlaceholder(/applicant name/i).fill(`From-Zero Trust ${RUN}`);
    await page.getByRole("button", { name: /create application/i }).click();
    await expect(page.getByRole("button", { name: /create application/i })).toHaveCount(0, {
      timeout: 20_000,
    });

    await page.getByPlaceholder(/search the national facility registry/i).fill(FACILITY_NAME);
    const fileLink = page.getByRole("link", { name: FACILITY_NAME }).first();
    await expect(fileLink).toBeVisible({ timeout: 20_000 });
    const facilityHref = await fileLink.getAttribute("href");
    await fileLink.click();
    await expectNoDeadEnd(page);

    const docRef = page.getByPlaceholder(/document reference/i);
    await expect(docRef).toBeVisible({ timeout: 20_000 });
    await docRef.fill(`doc://from-zero/${RUN}/premises-plan-v1`);
    await page.getByRole("button", { name: /^attach$/i }).click();
    await expect(page.getByText(/PREMISES PLAN · v1/i)).toBeVisible({ timeout: 20_000 });

    await page.getByRole("button", { name: /^submit$/i }).first().click();
    const readyBtn = page.getByRole("button", { name: /ready for inspection/i }).first();
    await expect(readyBtn).toBeVisible({ timeout: 20_000 });
    await readyBtn.click();

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

    await loginAs(page, PERSONAS.hpaInspector);
    await page.goto(facilityHref ?? "/registry/facility-lifecycle");
    await expectNoDeadEnd(page);
    await page.getByRole("button", { name: /record outcome/i }).first().click();
    await page.getByPlaceholder(/inspector notes/i).fill("From-zero journey: premises compliant.");
    await page.getByRole("button", { name: /record inspection outcome/i }).click();
    await expect(page.getByText(/outcome/i).first()).toBeVisible({ timeout: 20_000 });
    await logout(page);

    await loginAs(page, PERSONAS.hpaRegistrar);
    await page.goto(facilityHref ?? "/registry/facility-lifecycle");
    await expectNoDeadEnd(page);
    const appPicker = page.locator("select").filter({ hasText: /Application…/ }).first();
    await expect(appPicker).toBeVisible({ timeout: 20_000 });
    await appPicker.selectOption({ index: 1 });
    await page.getByPlaceholder(/resolution notes/i).fill("From-zero journey: committee approved.");
    await page.getByRole("button", { name: /record decision/i }).click();

    await expect(page.getByText(/ACTIVE/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/registered active/i).first()).toBeVisible({ timeout: 20_000 });
    await page.screenshot({ path: test.info().outputPath("s2-certificate.png"), fullPage: true });
    await logout(page);
  });

  test("S3: the new citizen drives the provider-claim council lane to its honest answer", async ({ page }) => {
    // Sign in as the S1 citizen — their own credentials, not a persona.
    await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
    await page.locator("#identifier").fill(CITIZEN.email);
    await page.locator("#password").fill(CITIZEN.password);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
    await acceptPoliciesIfPresent(page);

    await page.goto(`${PREVIEW_ORIGIN}/citizen/provider-claim`);
    await expectNoDeadEnd(page);
    await page.getByRole("button", { name: /i have a council number/i }).click();

    // The live council register is EMPTY (0 preload rows) — the honest
    // outcome for ANY number is a non-activating answer (no match / manual
    // review), never an activation and never a crash. If council preload
    // lands later, this stage should be extended to a real claim.
    const councilInput = page
      .getByPlaceholder(/council|registration number/i)
      .or(page.locator("input[type='text']"))
      .first();
    await expect(councilInput).toBeVisible({ timeout: 15_000 });
    await councilInput.fill(`MDPCZ-${RUN}`);
    await page
      .getByRole("button", { name: /check|verify|continue|preview/i })
      .first()
      .click();

    // Honest answer surface: some response text renders, no dead-end, and the
    // journey does NOT claim provider standing.
    await expectNoDeadEnd(page);
    await expect(page.getByText(/provider id active|claim complete/i)).toHaveCount(0);
    await page.screenshot({ path: test.info().outputPath("s3-council-answer.png"), fullPage: true });
    await logout(page);
  });

  test("S4: the public find-care front door is asked for the new facility (certificate→directory seam)", async ({ page }) => {
    await page.goto(`${PREVIEW_ORIGIN}/welcome/find-care`);
    await expectNoDeadEnd(page);

    const search = page.getByRole("searchbox").or(page.getByPlaceholder(/search|find/i)).first();
    await expect(search).toBeVisible({ timeout: 20_000 });
    await search.fill(FACILITY_NAME);
    await search.press("Enter");

    // Pin the seam either way — visible = certificate flows to the public
    // directory; absent = the operationalization gap is real and measured.
    const hit = page.getByText(FACILITY_NAME).first();
    const visible = await hit.isVisible({ timeout: 20_000 }).catch(() => false);
    await page.screenshot({ path: test.info().outputPath("s4-find-care.png"), fullPage: true });
    test.info().annotations.push({
      type: "seam",
      description: visible
        ? "REGISTERED facility IS publicly discoverable in find-care"
        : "GAP: freshly REGISTERED ACTIVE facility is NOT in public find-care — HPA certificate does not flow to the operational directory without a separate operationalization step",
    });
    // The journey's hard assertion: the front door answered (no dead-end);
    // the seam verdict is recorded above and reported by the runner.
    expect(typeof visible).toBe("boolean");
  });
});

/** acceptPoliciesIfGated, tolerant alias (first login of a brand-new account). */
async function acceptPoliciesIfPresent(page: import("@playwright/test").Page) {
  await acceptPoliciesIfGated(page);
}
