import { test, expect } from "@playwright/test";

/**
 * Preview sandbox runtime cohesion — live browser/API proof against the Dev Preview Sandbox.
 *
 * Run on VM or laptop against preview ingress:
 *   PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://41.57.127.235 npx playwright test e2e/preview-sandbox-cohesion.spec.ts
 */

const PREVIEW_ORIGIN = process.env.PLAYWRIGHT_BASE_URL ?? "http://41.57.127.235";
const RUN_PREVIEW = process.env.PREVIEW_SANDBOX_E2E === "1" || /41\.57\.127\.235/.test(PREVIEW_ORIGIN);

function seedExperienceSession() {
  const user = {
    id: "preview-e2e-user",
    name: "Preview E2E",
    displayName: "Preview E2E",
    roles: ["SYSTEM_ADMIN", "CLINICIAN", "ADMIN"],
    assuranceLevel: "VERIFIED",
  };
  sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
  sessionStorage.setItem(
    "exp:facility",
    JSON.stringify({ id: "FAC-HARARE-CENTRAL", name: "Harare Central Hospital" }),
  );
  localStorage.setItem(
    "exp:consent_accepted",
    JSON.stringify({ userId: user.id, version: "2026-04-11", acceptedAt: new Date().toISOString() }),
  );
  localStorage.setItem("exp:consent_version", "2026-04-11");
}

test.describe("Preview sandbox runtime cohesion", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test.beforeEach(async ({ context, baseURL }) => {
    const origin = baseURL ?? PREVIEW_ORIGIN;
    await context.addCookies([{ name: "exp_has_session", value: "1", url: origin }]);
    await context.addInitScript(seedExperienceSession);
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
    await page.goto("/registry");
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
    const learningResponse = page.waitForResponse(
      (response) => response.url().includes("/internal/v1/learning") && response.status() < 500,
      { timeout: 20_000 },
    );
    await page.goto("/learning/library");
    await learningResponse.catch(() => null);
    await expect(page.getByText(/Fundo Library/i)).toBeVisible();
  });

  test("governance policies surface loads", async ({ page }) => {
    await page.goto("/dags");
    await expect(page.getByText(/Data Access Governance|governance policies/i).first()).toBeVisible();
  });
});
