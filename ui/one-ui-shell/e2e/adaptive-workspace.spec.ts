import { test, expect } from "./fixtures";

/**
 * Adaptive workspace shell — screen-real-estate remediation evidence.
 * Validates the collapsible nav rail, taskbar minimise, focus mode, and the
 * workflow-state hard gate (layout changes never lose entered data), at
 * desktop and phone viewports.
 */
test.describe("Adaptive workspace shell", () => {
  test("nav rail expands on landing surfaces and compacts inside an application", async ({ page }) => {
    test.slow();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/home");
    const rail = page.locator("[data-nav-rail]");
    await expect(rail).toBeVisible();
    await expect(rail).toHaveAttribute("data-mode", "expanded");

    await page.goto("/diagnostics/orders/new");
    await expect(rail).toHaveAttribute("data-mode", "compact");
    // icon-only mode keeps accessible names on links
    const firstNavLink = rail.locator("nav a").first();
    await expect(firstNavLink).toHaveAttribute("aria-label", /.+/);
  });

  test("manual rail preference persists and wins over route context", async ({ page }) => {
    test.slow();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/home");
    const rail = page.locator("[data-nav-rail]");
    await expect(rail).toHaveAttribute("data-mode", "expanded");

    await page.getByTestId("nav-rail-toggle").click();
    await expect(rail).toHaveAttribute("data-mode", "compact");

    await page.reload();
    await expect(rail).toHaveAttribute("data-mode", "compact");

    await page.getByTestId("nav-rail-toggle").click();
    await expect(rail).toHaveAttribute("data-mode", "expanded");
  });

  test("taskbar minimises to a restore handle and frees its reserved height", async ({ page }) => {
    test.slow();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/home");
    await expect(page.getByTestId("shell-floating-dock")).toBeVisible();

    await page.getByRole("button", { name: /minimize taskbar/i }).click();
    await expect(page.getByTestId("shell-floating-dock")).toHaveCount(0);
    const handle = page.getByTestId("shell-taskbar-handle");
    await expect(handle).toBeVisible();

    const reserved = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue("--shell-taskbar-height").trim(),
    );
    expect(reserved).toBe("0px");

    await handle.click();
    await expect(page.getByTestId("shell-floating-dock")).toBeVisible();
  });

  test("focus mode maximises the workspace without losing entered data (hard gate)", async ({ page }) => {
    test.slow();
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/diagnostics/orders/new");

    const cpid = page.getByLabel("Patient CPID");
    await cpid.fill("CPID-E2E-42");

    await page.getByTestId("focus-mode-toggle").click();
    await expect(page.locator("[data-nav-rail]")).toHaveCount(0);
    await expect(page.getByTestId("shell-taskbar-handle")).toBeVisible();
    // entered data survives the layout change
    await expect(cpid).toHaveValue("CPID-E2E-42");

    await page.getByTestId("focus-mode-toggle").click();
    await expect(page.locator("[data-nav-rail]")).toBeVisible();
    await expect(cpid).toHaveValue("CPID-E2E-42");
  });

  test("phone viewport: rail hidden, no horizontal overflow on converted forms", async ({ page }) => {
    test.slow();
    await page.setViewportSize({ width: 393, height: 851 });
    for (const route of ["/diagnostics/orders/new", "/nhume/deliveries/new", "/auth/register"]) {
      await page.goto(route);
      // rail is CSS-hidden below lg (drawer + taskbar serve navigation)
      await expect(page.locator("[data-nav-rail]")).toBeHidden();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow, `${route} must not scroll horizontally`).toBeLessThanOrEqual(1);
    }
  });
});
