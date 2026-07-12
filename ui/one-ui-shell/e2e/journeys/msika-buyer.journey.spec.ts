import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs } from "./honest-auth";

/**
 * Golden journey — the Msika buyer lane as a citizen.
 *
 * citizen.moyo (CITIZEN) browses the marketplace store (published listings or
 * an honest empty state — never a blank success), finds a real add-to-cart
 * affordance on a search card, reviews the cart with real price structure
 * (currency + amount from the payload, never a hardcoded $0.00), and the
 * money-leg shells (orders + pay page) render without dead-ending. Checkout
 * itself needs a priced published listing, so this journey proves the shells
 * and affordances rather than forcing a synthetic order into the estate.
 */

test.describe("Msika buyer journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("citizen browses the store, finds add-to-cart, and the cart shows priced truth", async ({ page }) => {
    await loginAs(page, PERSONAS.citizen);

    // Store home: featured listings render, or the honest empty/degraded state.
    await page.goto("/marketplace/store");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/marketplace store/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/featured & recently published/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByRole("link", { name: /view/i })
        .first()
        .or(page.getByText(/no published listings yet/i).first())
        .or(page.getByText(/could not load listings right now/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Search surface: run a real search; a non-clinical card carries the compact
    // add-to-cart affordance ("Add"). An honest empty/unavailable state is also
    // acceptable — a dead or decorative button is not.
    await page.goto("/marketplace/store/search");
    await expectNoDeadEnd(page);
    const searchBtn = page.getByRole("button", { name: /^search$/i });
    await expect(searchBtn).toBeVisible({ timeout: 20_000 });
    await searchBtn.click();
    const addToCart = page.getByRole("button", { name: /^add$/i }).first();
    const searchOutcome = addToCart
      .or(page.getByText(/no published listings match your search/i).first())
      .or(page.getByText(/search is unavailable right now/i).first());
    await expect(searchOutcome).toBeVisible({ timeout: 20_000 });
    if (await addToCart.isVisible().catch(() => false)) {
      await expect(addToCart).toBeEnabled();
    } else {
      test.info().annotations.push({
        type: "note",
        description: "no non-clinical published listing in the estate — add-to-cart affordance asserted only against the honest empty state",
      });
    }

    // Cart: items with real currency+amount lines, or the honest empty state.
    // The total is composed from payload prices — never a hardcoded $0.00.
    await page.goto("/marketplace/cart");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/shopping cart/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByText(/your cart is empty/i)
        .first()
        .or(page.getByText(/^Total/).first()),
    ).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("$0.00")).toHaveCount(0);
    if (await page.getByText(/^Total/).first().isVisible().catch(() => false)) {
      // Priced structure: ISO currency + decimal amount (e.g. "USD 12.50").
      await expect(page.getByText(/[A-Z]{3}\s\d+\.\d{2}/).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /checkout via/i })).toBeVisible();
    }
  });

  test("the money-leg shells render: orders page and the pay route", async ({ page }) => {
    await loginAs(page, PERSONAS.citizen);

    // Orders shell: renders honestly for a citizen (facility-procurement form
    // needs a facility context, so the honest select-a-facility prompt counts).
    await page.goto("/marketplace/orders");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/marketplace orders/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByText(/select a facility before placing procurement orders/i)
        .first()
        .or(page.getByText(/create order/i).first()),
    ).toBeVisible({ timeout: 20_000 });

    // Pay route shell: renders its page (with the honest could-not-load message
    // for an unknown order id) — never a 404 or an error boundary. A checkout
    // redirect lands here with a real order id in the buyer lane.
    await page.goto("/marketplace/orders/journey-shell-probe/pay");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/pay for order/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(
      page
        .getByRole("button", { name: /start payment/i })
        .or(page.getByText(/this order could not be loaded/i).first()),
    ).toBeVisible({ timeout: 20_000 });
    test.info().annotations.push({
      type: "note",
      description: "checkout is not forced end-to-end here — it needs a priced published listing; the shells and affordances are proven instead",
    });
  });
});
