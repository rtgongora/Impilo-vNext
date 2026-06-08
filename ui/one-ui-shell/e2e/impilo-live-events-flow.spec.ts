import { test, expect } from "./fixtures";

test.describe("Impilo Live events journey", () => {
  test("discover page loads health talks shell", async ({ page }) => {
    await page.goto("/live/discover");
    await expect(page.getByRole("heading", { name: "Live Health Talks" })).toBeVisible();
    await expect(page.getByRole("button", { name: "CITIZEN" })).toBeVisible();
  });

  test("live hub links primary journey routes", async ({ page }) => {
    await page.goto("/live");
    await expect(page.getByRole("link", { name: /Discover Talks/i })).toHaveAttribute("href", "/live/discover");
    await expect(page.getByRole("link", { name: /My Events/i })).toHaveAttribute("href", "/live/my-events");
    await expect(page.getByRole("link", { name: /Replays/i })).toHaveAttribute("href", "/live/replays");
  });
});
