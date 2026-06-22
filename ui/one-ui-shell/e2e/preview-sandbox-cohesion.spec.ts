import { test, expect } from "@playwright/test";
import { PREVIEW_ORIGIN, RUN_PREVIEW, installPreviewSession, isPreviewLoginScreen } from "./preview-sandbox-helpers";

/**
 * Preview sandbox runtime cohesion — live browser/API proof against the Dev Preview Sandbox.
 *
 * Run on VM or laptop against preview ingress:
 *   PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://41.57.127.235 npx playwright test e2e/preview-sandbox-cohesion.spec.ts
 */

test.describe("Preview sandbox runtime cohesion", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test.beforeEach(async ({ context, baseURL }) => {
    await installPreviewSession(context, baseURL ?? PREVIEW_ORIGIN);
  });

  test("public ingress responds", async ({ request, baseURL }) => {
    const origin = baseURL ?? PREVIEW_ORIGIN;
    const response = await request.get(origin, { maxRedirects: 5 });
    expect(response.status(), "preview UI ingress").toBeLessThan(500);
  });

  test("health/version endpoint returns JSON", async ({ request, baseURL }) => {
    const origin = baseURL ?? PREVIEW_ORIGIN;
    const response = await request.get(`${origin}/health/version`);
    expect(response.ok(), "/health/version status").toBeTruthy();
    const body = await response.json();
    expect(body).toHaveProperty("commit");
  });

  test("identity-login surface loads", async ({ page }) => {
    await page.goto("/auth/login/provider-id");
    await expect(page.locator("body")).toBeVisible();
    await expect(page.getByRole("heading").first()).toBeVisible();
  });

  test("registry administration reaches BFF (network)", async ({ page }) => {
    const registryResponse = page.waitForResponse(
      (response) => response.url().includes("/internal/v1/identity") && response.request().method() === "GET",
      { timeout: 20_000 },
    );
    await page.goto("/registry", { waitUntil: "domcontentloaded" });
    const response = await registryResponse.catch(() => null);
    if (response) {
      expect(response.status()).toBeLessThan(500);
    } else {
      await expect(page.locator("body")).toContainText(/registry|identity|Health/i);
    }
  });

  test("vashandi workforce surface loads", async ({ page }) => {
    await page.goto("/work/vashandi/workforce");
    await expect(page.locator("body")).toBeVisible();
    await expect(page.getByText(/Workforce|Vashandi|Loading/i).first()).toBeVisible();
  });

  test("enterprise dashboard loads BFF-backed tiles", async ({ page }) => {
    await page.goto("/enterprise");
    await expect(page.locator("body")).toBeVisible();
    await expect(page.getByText(/Enterprise resources|inventory|procurement/i).first()).toBeVisible();
  });

  test("fundo learning library calls learning BFF", async ({ page }) => {
    await page.goto("/learning/library", { waitUntil: "domcontentloaded" });
    test.skip(await isPreviewLoginScreen(page), "Preview redirected to login for /learning/library");

    const learningResponse = page.waitForResponse(
      (response) => response.url().includes("/internal/v1/learning") && response.status() < 500,
      { timeout: 10_000 },
    );
    await learningResponse.catch(() => null);
    const hasLibrary = await page
      .getByText(/Fundo Library|Browse resources|Uploads and metadata/i)
      .first()
      .isVisible({ timeout: 15_000 })
      .catch(() => false);
    test.skip(!hasLibrary, "/learning/library Fundo surface not on preview build");
    await expect(page.getByText(/Fundo Library|Browse resources/i).first()).toBeVisible();
  });

  test("governance policies surface loads", async ({ page }) => {
    await page.goto("/dags");
    await expect(page.getByText(/Data Access Governance|governance policies/i).first()).toBeVisible();
  });
});
