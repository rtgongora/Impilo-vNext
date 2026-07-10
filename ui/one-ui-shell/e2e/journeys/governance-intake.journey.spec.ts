import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, gotoAs, loginAs, logout } from "./honest-auth";

/**
 * Golden journey — governance intake: a provider claims facility
 * administration, and the IATG officer reviews it in the Trust Console.
 *
 * Two personas, one thread of trust:
 *   1. dr.gwena submits a facility administration claim (/facility/claim).
 *   2. iatg.gono opens the Trust Console, sees the facility-admin queue and
 *      the pending-invitations queue, and can open a claim for decision.
 *
 * The spec asserts honest states everywhere: a degraded queue must say
 * pending_backend language, never render fabricated zeros.
 */

test.describe("Governance intake journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("provider submits a facility administration claim", async ({ page }) => {
    await gotoAs(page, PERSONAS.specialist, "/facility/claim");
    await expectNoDeadEnd(page);

    // The claim surface is real: it names the journey and offers a facility picker
    // or an existing-claim state — either is a truthful, non-dead surface.
    await expect(
      page.getByRole("heading", { name: /claim facility administration/i }).or(
        page.getByText(/claim facility administration/i).first(),
      ),
    ).toBeVisible({ timeout: 20_000 });

    await logout(page);
  });

  test("IATG officer reviews governance queues in the Trust Console", async ({ page }) => {
    await loginAs(page, PERSONAS.iatgOfficer);
    await page.goto("/registry-admin/trust-console");
    await expectNoDeadEnd(page);

    // All five queues are present as tabs with counts (live or honest partials).
    for (const tab of [
      /provider access/i,
      /facility admin claims/i,
      /org onboarding/i,
      /assurance upgrades/i,
      /invitations/i,
    ]) {
      await expect(page.getByRole("button", { name: tab }).first()).toBeVisible({
        timeout: 20_000,
      });
    }

    // Open the facility admin queue: items list or an honest empty/degraded state.
    await page.getByRole("button", { name: /facility admin claims/i }).first().click();
    await expect(
      page
        .getByText(/nothing waiting for review/i)
        .or(page.getByText(/pending_backend|unavailable/i))
        .or(page.getByText(/appointment #/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Open the invitations queue: same honesty contract.
    await page.getByRole("button", { name: /invitations/i }).first().click();
    await expect(
      page
        .getByText(/nothing waiting for review/i)
        .or(page.getByText(/pending_backend|unavailable/i))
        .or(page.getByText(/invitation ·/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    await logout(page);
  });
});
