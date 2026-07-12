import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs } from "./honest-auth";

/**
 * Golden journey — Msika marketplace operations.
 *
 * msika.operator (MARKETPLACE_OPERATOR) works the governance surfaces: the
 * marketplace ops console with vendor governance and the audit trail (real
 * fetch actions over the commerce ops family — not 404 placeholders), and the
 * substitutions desk with its inbox list and reject affordance. Every surface
 * must be live or honestly degraded — never a blank page or a dead button.
 */

test.describe("Msika operator journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("ops console renders vendor governance and the audit trail with live actions", async ({ page }) => {
    await loginAs(page, PERSONAS.msikaOperator);

    await page.goto("/marketplace/ops");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/marketplace ops/i).first()).toBeVisible({ timeout: 20_000 });

    // Vendor governance: section + real fetch action (armed query, not a stub).
    await expect(page.getByText(/vendor governance/i).first()).toBeVisible({ timeout: 20_000 });
    const fetchVendors = page.getByRole("button", { name: /fetch vendors/i });
    await expect(fetchVendors).toBeVisible();
    await expect(fetchVendors).toBeEnabled();

    // Audit trail: section + real fetch action over the commerce ops audit API.
    await expect(page.getByText(/audit trail/i).first()).toBeVisible({ timeout: 20_000 });
    const fetchAudit = page.getByRole("button", { name: /fetch audit/i });
    await expect(fetchAudit).toBeVisible();
    await expect(fetchAudit).toBeEnabled();

    // Prove the audit fetch is wired: results table, an honest empty payload,
    // or an honest failure message — never silence.
    await fetchAudit.click();
    await expect(
      page
        .getByText(/could not load|no .*returned|no rows/i)
        .first()
        .or(page.locator("table").first()),
    ).toBeVisible({ timeout: 30_000 });
  });

  test("substitutions desk renders the inbox list and reject affordance", async ({ page }) => {
    await loginAs(page, PERSONAS.msikaOperator);

    await page.goto("/marketplace/substitutions");
    await expectNoDeadEnd(page);
    await expect(page.getByText(/substitution inbox/i).first()).toBeVisible({ timeout: 20_000 });

    // Inbox loads on arrival (armed by default): rows, the honest empty state,
    // or an honest load failure.
    await expect(
      page
        .getByText(/no substitution rows were returned/i)
        .first()
        .or(page.getByText(/could not load substitutions/i).first())
        .or(page.locator("table").first()),
    ).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /fetch substitutions/i })).toBeVisible();

    // Decision panel: approve/reject affordances render. Reject is guarded —
    // it stays disabled until an order id is selected, which is governance, not
    // a dead button.
    await expect(page.getByText(/approve or reject/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /reject substitution/i })).toBeVisible();
  });
});
