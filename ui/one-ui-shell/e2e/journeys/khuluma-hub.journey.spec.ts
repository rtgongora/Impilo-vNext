import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

/**
 * Golden journey — the Khuluma communication front door.
 *
 * A citizen reaches the first-class /khuluma hub (the front door the PO could never find —
 * it was gated OPERATIONS_AGGREGATE and cmd-comms pointed at the legacy page), the hub loads
 * real data (comms summary + notifications) with no invented counts, and the workspace sub-nav
 * navigates. Live preview only — real login, real ingress, real reads.
 */
test.describe("Khuluma hub — front door + real data (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("citizen reaches /khuluma, hub loads real data, tabs navigate", async ({ page }) => {
    await loginAs(page, PERSONAS.citizen);

    // The hub loads on a real notifications read (recipient-scoped) — assert we see the
    // response, so the attention counts come from live data, not fabricated numbers.
    const [notifResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes("/internal/v1/notifications") && r.request().method() === "GET",
        { timeout: 25_000 },
      ),
      gotoAs(page, PERSONAS.citizen, "/khuluma"),
    ]);
    expect(notifResp.status(), "notifications read reached").toBeLessThan(500);

    // The role-aware home rendered as the citizen ("life") persona.
    const home = page.getByTestId("khuluma-home");
    await expect(home).toBeVisible({ timeout: 20_000 });
    await expect(home).toHaveAttribute("data-persona", "life");

    // Quick actions + sub-nav are present (Impilo Live is a link-out).
    await expect(page.getByRole("heading", { name: "Quick actions" })).toBeVisible();
    await expect(page.getByRole("link", { name: /Impilo Live/ })).toBeVisible();

    // Navigate to the Inbox workspace — the embedded CommsHub renders (one messaging surface).
    await page.getByRole("link", { name: "Inbox", exact: false }).first().click();
    await expect(page.getByTestId("comms-hub")).toBeVisible({ timeout: 20_000 });

    // And to Updates — the notification feed workspace.
    await gotoAs(page, PERSONAS.citizen, "/khuluma/updates");
    await expect(page.getByRole("heading", { name: /Updates/ })).toBeVisible({ timeout: 20_000 });
  });
});
