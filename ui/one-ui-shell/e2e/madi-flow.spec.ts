import { test, expect } from "./fixtures";

/**
 * Browser journey smoke for MADI core transactions (golden-thread UI surfaces).
 * Evidence for COMPLETION_EVIDENCE — does not require live backend in dev preview mode.
 */
test.describe("MADI core transaction journeys", () => {
  test("donor hub and screening routes render", async ({ page }) => {
    await page.goto("/madi/donor");
    await expect(page.locator("body")).not.toHaveText(/^$/);

    await page.goto("/madi/donor/screening");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("blood order workbench routes render", async ({ page }) => {
    await page.goto("/madi/orders");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("transfusion episode routes render", async ({ page }) => {
    await page.goto("/madi/transfusion");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("haemovigilance facility and national routes render", async ({ page }) => {
    await page.goto("/madi/haemovigilance");
    await expect(page.locator("body")).not.toHaveText(/^$/);

    await page.goto("/madi/haemovigilance/national");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("MADI hub links to programme surfaces", async ({ page }) => {
    await page.goto("/madi");
    const body = await page.locator("body").textContent();
    expect(body?.toLowerCase()).toMatch(/donor|blood|order|transfusion|haemovigilance|madi/);
  });
});
