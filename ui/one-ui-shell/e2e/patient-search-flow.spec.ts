import { test, expect } from "./fixtures";

test.describe("Patient search journey", () => {
  test("search page loads with orchestration guidance", async ({ page }) => {
    await page.goto("/queue/search");

    await expect(page.getByText("Patient Search")).toBeVisible();
    await expect(page.getByText(/Patient search orchestration/i)).toBeVisible();
  });

  test("search form and queue continuity actions are exposed", async ({ page }) => {
    await page.goto("/queue/search");

    await expect(page.getByPlaceholder(/Search by name, CPID/i)).toBeVisible();
    await expect(page.getByRole("button", { name: "Search" })).toBeVisible();
    await expect(page.getByRole("link", { name: /Walk-in Registration/i })).toBeVisible();
  });

  test("correlated search preserves journey query on chart handoff links", async ({ page }) => {
    await page.goto("/queue/search?journey_id=journey-42&transaction_id=tx-42");

    await expect(page.getByText(/Patient search transaction journey|transaction was not found/i)).toBeVisible();
  });
});
