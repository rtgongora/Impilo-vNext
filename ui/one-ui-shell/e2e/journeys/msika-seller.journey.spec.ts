import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, ensureFacilityContext, loginAs } from "./honest-auth";

/**
 * Golden journey — the Msika Seller Centre.
 *
 * msika.seller (MARKETPLACE_SELLER, PROV-ZW-00014) works the seller surfaces:
 * the Seller Centre hub with the regulatory demand signal (Market demand) and
 * the wholesaler-licence verification card, the listings workspace with a real
 * create affordance, and the moderation queue. Every surface must be live or
 * honestly degraded — never a blank page or a dead button.
 */

test.describe("Msika seller journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("seller hub shows the demand signal and wholesaler-licence verification", async ({ page }) => {
    await loginAs(page, PERSONAS.msikaSeller);
    await ensureFacilityContext(page, PERSONAS.msikaSeller);

    await page.goto("/marketplace/seller");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/seller centre/i).first()).toBeVisible({ timeout: 20_000 });

    // Market demand: aggregated regulatory demand signal (rows, the honest
    // reporting-floor empty state, or an honest load failure — never blank).
    await expect(page.getByText(/market demand/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByText(/no demand signals above the reporting floor yet/i)
        .first()
        .or(page.getByText(/licensed suppliers only/i).first())
        .or(page.getByText(/could not load market demand right now/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Wholesaler licence verification card is present with its real action.
    await expect(page.getByText(/verify wholesaler licence/i).first()).toBeVisible({ timeout: 20_000 });
  });

  test("listings workspace renders with a create affordance; moderation queue is live", async ({ page }) => {
    await loginAs(page, PERSONAS.msikaSeller);
    await ensureFacilityContext(page, PERSONAS.msikaSeller);

    // My Listings: the storefront list (or honest empty/resolving state) plus
    // the New-listing affordance that opens the governed draft flow.
    await page.goto("/marketplace/seller/listings");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/my listings/i).first()).toBeVisible({ timeout: 20_000 });
    const createLink = page.getByRole("link", { name: /^new$/i }).first();
    await expect(createLink).toBeVisible({ timeout: 20_000 });
    await expect(createLink).toHaveAttribute("href", "/marketplace/seller/listings/new");
    await expect(
      page
        .getByText(/no listings yet for this seller/i)
        .first()
        .or(page.getByText(/resolving your seller identity/i).first())
        .or(page.getByText(/could not load your listings/i).first())
        .or(page.getByText(/draft|pending review|published/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Moderation: the audited decision queue renders (queue rows with decision
    // actions, or the honest empty/degraded state).
    await page.goto("/marketplace/seller/moderation");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/listing moderation/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByText(/no listings awaiting moderation/i)
        .first()
        .or(page.getByRole("button", { name: /^approve$/i }).first())
        .or(page.getByText(/could not load the moderation queue/i).first()),
    ).toBeVisible({ timeout: 20_000 });
  });
});
