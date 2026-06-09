import { test, expect } from "./fixtures";
import { WELLNESS_E2E, installWellnessMocks } from "./wellness-mocks";

test.describe("Wellness journey (mocked Simba + citizen activity)", () => {
  test.beforeEach(async ({ page }) => {
    await installWellnessMocks(page);
    await page.addInitScript(() => {
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "e2e-wellness-citizen-001",
          email: "citizen@e2e.impilo.zw",
          displayName: "E2E Wellness Citizen",
          roles: ["CITIZEN"],
          actorType: "CITIZEN",
        }),
      );
    });
  });

  test("full journey: goals → activity → diet → club join → challenge", async ({ page }) => {
    test.setTimeout(120_000);
    const goalUrls: string[] = [];
    const activityUrls: string[] = [];
    const dietUrls: string[] = [];
    const clubJoinUrls: string[] = [];
    const challengeJoinUrls: string[] = [];

    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/wellness/goals") && req.method() === "POST") goalUrls.push(u);
      if (
        u.includes("/internal/v1/wellness/activities") ||
        u.includes("/internal/v1/mobile/citizen/wellness/activities")
      ) {
        activityUrls.push(u);
      }
      if (u.includes("/internal/v1/wellness/diet") && req.method() === "POST") dietUrls.push(u);
      if (u.includes("/internal/v1/wellness/clubs") && u.includes("/join")) clubJoinUrls.push(u);
      if (u.includes("/internal/v1/wellness/challenges") && u.includes("/join")) challengeJoinUrls.push(u);
    });

    await page.goto("/wellness/goals");
    await expect(page.getByRole("heading", { name: "Health Goals" })).toBeVisible();
    await page.getByTestId("wellness-goal-template-steps").click();
    await expect.poll(() => goalUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);
    await expect(page.getByText(/STEPS/i)).toBeVisible({ timeout: 10_000 });

    await page.goto("/wellness/activity");
    await expect(page.getByRole("heading", { name: "Activity & Fitness" })).toBeVisible();
    await page.getByTestId("wellness-activity-steps").fill("3200");
    await page.getByTestId("wellness-activity-save").click();
    await expect.poll(() => activityUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);

    await page.goto("/wellness/diet");
    await expect(page.getByRole("heading", { name: "Diet & Nutrition" })).toBeVisible({ timeout: 30_000 });
    await page.getByTestId("wellness-diet-name").fill("Breakfast oats");
    await page.getByTestId("wellness-diet-calories").fill("380");
    await page.getByTestId("wellness-diet-add").click();
    await expect.poll(() => dietUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);

    await page.goto("/wellness/clubs");
    await expect(page.getByTestId("wellness-club-join").first()).toBeVisible({ timeout: 30_000 });
    await page.getByTestId("wellness-club-join").first().click();
    await expect.poll(() => clubJoinUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);
    await expect.poll(
      async () => page.getByText("Leave Club").isVisible(),
      { timeout: 15_000 },
    ).toBe(true);

    await page.goto("/wellness/challenges");
    await expect(page.getByTestId("wellness-challenge-join").first()).toBeVisible({ timeout: 30_000 });
    await page.getByTestId("wellness-challenge-join").first().click();
    await expect.poll(() => challengeJoinUrls.length, { timeout: 15_000 }).toBeGreaterThan(0);
    await expect.poll(
      async () => page.getByText("Joined").isVisible(),
      { timeout: 15_000 },
    ).toBe(true);
  });
});
