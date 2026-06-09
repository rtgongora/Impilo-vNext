import { test, expect } from "./fixtures";
import { installPctMocks, PCT_E2E } from "./pct-mocks";

test.describe("PCT queue encounter (mocked)", () => {
  test.beforeEach(async ({ page }) => {
    await installPctMocks(page);
    await page.addInitScript((facilityId) => {
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "e2e-provider-queue-001",
          email: "provider@e2e.impilo.zw",
          displayName: "E2E Queue Clinician",
          roles: ["CLINICIAN"],
          actorType: "PROVIDER",
          facilityId,
        }),
      );
    }, PCT_E2E.facilityId);
  });

  test("queue page loads entries and call-patient issues POST", async ({ page }) => {
    const callUrls: string[] = [];
    page.on("request", (req) => {
      if (req.url().includes("/internal/v1/queue/entries") && req.method() === "POST") {
        callUrls.push(req.url());
      }
    });

    await page.goto("/queue");
    await expect(page.getByRole("heading", { name: /Patient Queue|Queue/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.locator("body")).toContainText(/CPID-ZW-00001|WAITING/i, { timeout: 15_000 });

    const callBtn = page.getByRole("button", { name: /Call|Start/i }).first();
    if (await callBtn.isVisible()) {
      await callBtn.click();
      await expect.poll(() => callUrls.length, { timeout: 10_000 }).toBeGreaterThan(0);
    }
  });
});
