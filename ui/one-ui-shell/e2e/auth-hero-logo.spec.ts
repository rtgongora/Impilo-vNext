import { test, expect } from "@playwright/test";

const VIEWPORTS = [
  { name: "mobile", width: 375, height: 812 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "desktop", width: 1280, height: 800 },
] as const;

test.describe("Auth hero logo prominence", () => {
  for (const viewport of VIEWPORTS) {
    test(`login shows hero logo at ${viewport.width}px`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto("/auth/login");

      const heroLogo = page.getByTestId("impilo-brand-hero");
      await expect(heroLogo).toBeVisible();

      if (viewport.width < 1024) {
        await expect(page.getByTestId("auth-mobile-hero-logo")).toBeVisible();
      } else {
        await expect(page.getByTestId("auth-desktop-hero-logo")).toBeVisible();
      }

      const box = await heroLogo.boundingBox();
      expect(box).not.toBeNull();
      if (box) {
        expect(box.height).toBeGreaterThanOrEqual(40);
        expect(box.height).toBeLessThanOrEqual(72);
      }
    });
  }
});
