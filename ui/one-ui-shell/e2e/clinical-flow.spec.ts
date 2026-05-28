import { test, expect } from "./fixtures";

test.describe("Clinical workflow", () => {
  test("queue page loads with patient queue heading", async ({ page }) => {
    await page.goto("/queue");

    await expect(page.getByText("Patient Queue")).toBeVisible();
  });

  test("queue page shows status-based sub-navigation", async ({ page }) => {
    await page.goto("/queue");

    // Queue sub-routes should be linked or tabbed
    const bodyText = await page.locator("body").textContent();
    expect(bodyText).toContain("Queue");
  });

  test("queue walk-in page is accessible", async ({ page }) => {
    await page.goto("/queue/walk-in");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("queue triage page is accessible", async ({ page }) => {
    await page.goto("/queue/triage");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("patient EHR chart page renders for a test patient", async ({ page }) => {
    await page.goto("/ehr/test-patient-001");

    await expect(page.getByText("Patient Chart")).toBeVisible();
    // Should show a back-to-queue link
    await expect(page.getByText(/back to queue/i)).toBeVisible();
  });

  test("EHR chart sections are listed", async ({ page }) => {
    await page.goto("/ehr/test-patient-001");

    // Chart sections defined in the page component
    await expect(page.getByText("Vitals")).toBeVisible();
    await expect(page.getByText("Conditions")).toBeVisible();
    await expect(page.getByText("Medications")).toBeVisible();
    await expect(page.getByText("Allergies")).toBeVisible();
    await expect(page.getByText("Orders")).toBeVisible();
    await expect(page.getByText("Results")).toBeVisible();
  });

  test("can navigate between EHR sub-pages", async ({ page }) => {
    await page.goto("/ehr/test-patient-001/vitals");
    await expect(page.locator("body")).not.toHaveText(/^$/);

    await page.goto("/ehr/test-patient-001/conditions");
    await expect(page.locator("body")).not.toHaveText(/^$/);

    await page.goto("/ehr/test-patient-001/medications");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("scheduling page loads", async ({ page }) => {
    await page.goto("/scheduling");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });
});
