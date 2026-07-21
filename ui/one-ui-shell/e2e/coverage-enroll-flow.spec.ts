import { test, expect } from "./fixtures";
import { COVERAGE_E2E, installCoverageMocks } from "./coverage-mocks";

test.describe("Coverage enrollment journey", () => {
  test.beforeEach(async ({ page }) => {
    await installCoverageMocks(page);
  });

  test("enroll page loads with three-step workflow", async ({ page }) => {
    await page.goto("/coverage/enroll");

    await expect(page.getByRole("heading", { name: "Enroll in coverage" })).toBeVisible();
    await expect(page.getByText("1. Choose a plan")).toBeVisible();
    await expect(page.getByText("2. Eligibility check")).toBeVisible();
    await expect(page.getByText("3. Confirm enrollment")).toBeVisible();
  });

  test("full journey: plan load → eligibility → enroll → member dashboard", async ({ page }) => {
    const planUrls: string[] = [];
    const eligibilityUrls: string[] = [];
    const enrollUrls: string[] = [];

    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/coverage/plans")) planUrls.push(u);
      if (u.includes("/internal/v1/coverage/eligibility/enrollment")) eligibilityUrls.push(u);
      if (u.includes("/internal/v1/coverage/members") && req.method() === "POST") enrollUrls.push(u);
    });

    await page.goto("/coverage/enroll");
    await expect(page.getByTestId("coverage-enroll-plan-select")).toBeVisible();

    await page.getByTestId("coverage-enroll-plan-select").selectOption(COVERAGE_E2E.planId);
    await expect.poll(() => planUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);

    await page.getByTestId("coverage-run-eligibility").click();
    await expect(page.getByTestId("coverage-eligibility-result")).toContainText(/ELIGIBLE/i);
    await expect.poll(() => eligibilityUrls.length).toBeGreaterThan(0);

    await page.getByTestId("coverage-member-number").fill(COVERAGE_E2E.memberNumber);
    await page.getByTestId("coverage-enroll-submit").click();
    await expect(page.getByTestId("coverage-enroll-success")).toBeVisible({ timeout: 15_000 });
    await expect.poll(() => enrollUrls.length).toBeGreaterThan(0);

    await page.goto("/coverage/member");
    await expect(page.getByTestId("coverage-member-plans-table")).toContainText(COVERAGE_E2E.planName, {
      timeout: 15_000,
    });
    await expect(page.getByTestId("coverage-member-plans-table")).toContainText("ACTIVE");

    // Appeals section must render its data, not the error box (regression guard for
    // the cross-namespace BFF hop that made "Appeals" fail live).
    await expect(page.getByText("Claim partially declined — requesting review")).toBeVisible();
    await expect(page.getByText("Could not load this section.")).toHaveCount(0);
  });

  test("coverage hub links back from enroll", async ({ page }) => {
    await page.goto("/coverage/enroll");
    await expect(page.getByRole("link", { name: /back to coverage/i })).toHaveAttribute("href", "/coverage");
  });
});
