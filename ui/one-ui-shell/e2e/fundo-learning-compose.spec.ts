import { test, expect } from "@playwright/test";

/**
 * Real-stack Fundo flows — **no** `page.route` mocks.
 *
 * Prereqs:
 *   docker compose -f compose/experience/docker-compose.yml up
 *   (includes learning-service:8235 + experience-bff:8160 + one-ui-shell:3000)
 *
 * Run:
 *   PLAYWRIGHT_COMPOSE_E2E=1 PLAYWRIGHT_SKIP_WEBSERVER=1 \
 *     npx playwright test e2e/fundo-learning-compose.spec.ts --project=chromium
 */
const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const LEARNING_ORIGIN = process.env.PLAYWRIGHT_LEARNING_URL || "http://localhost:8235";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, learning] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${LEARNING_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && learning);
  } catch {
    return false;
  }
}

test.describe("Fundo learning (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose: ./tools/dev/up.sh — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, learning ${LEARNING_ORIGIN}`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-fundo-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-fundo-provider-001",
          email: "provider@compose.fundo",
          displayName: "Compose Fundo Provider",
          roles: ["CLINICIAN", "DEVELOPER"],
          actorType: "PROVIDER",
          providerId: "PRV-COMPOSE-FUNDO",
          providerActivated: true,
          assuranceLevel: "VERIFIED",
        }),
      );
    });
  });

  test("catalog page loads published courses from real learning-service via BFF", async ({ page }) => {
    const catalogUrls: string[] = [];
    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/learning/v11/catalog")) catalogUrls.push(u);
    });

    await page.goto(`${UI_ORIGIN}/learning/catalog`);
    await expect(page.getByRole("heading", { name: "Impilo Fundo Catalogue" })).toBeVisible({ timeout: 60_000 });
    await expect.poll(() => catalogUrls.length, { timeout: 60_000 }).toBeGreaterThan(0);
    await expect(page.locator("body")).toContainText(/FUNDO-EHR-101|Introduction to Impilo EHR/i, { timeout: 30_000 });
  });

  test("library uploads exposes governed format guide", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/learning/library/uploads`);
    await expect(page.getByTestId("fundo-content-formats-guide")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("fundo-upload-type")).toBeVisible();
  });

  test("admin moderation queue is reachable", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/learning/admin/moderation`);
    await expect(page.getByRole("heading", { name: "Assessment moderation" })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("fundo-moderation-queue")).toBeVisible();
  });
});
