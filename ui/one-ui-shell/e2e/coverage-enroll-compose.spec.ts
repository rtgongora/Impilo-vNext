import { test, expect } from "@playwright/test";

/**
 * Real-stack Coverage flows — **no** `page.route` mocks.
 *
 * Prereqs:
 *   docker compose -f compose/experience/docker-compose.yml up
 *   (includes coverage-service:8140 + experience-bff:8160 + one-ui-shell:3000)
 *
 * Run:
 *   PLAYWRIGHT_COMPOSE_E2E=1 PLAYWRIGHT_SKIP_WEBSERVER=1 \
 *     npx playwright test e2e/coverage-enroll-compose.spec.ts --project=chromium
 */
const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const COVERAGE_ORIGIN = process.env.PLAYWRIGHT_COVERAGE_URL || "http://localhost:8140";

const COMPOSE_PLAN_ID = "bbbbbbbb-0001-0000-0000-000000000001";
const COMPOSE_CLIENT_ID = "compose-coverage-citizen-001";
const COMPOSE_MEMBER_NUMBER = `MOH-COMPOSE-${Date.now()}`;

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, coverage] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${COVERAGE_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && coverage);
  } catch {
    return false;
  }
}

test.describe("Coverage enrollment (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose: ./tools/dev/up.sh — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, coverage ${COVERAGE_ORIGIN}`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript((clientId) => {
      sessionStorage.setItem("exp:auth_token", "compose-coverage-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: clientId,
          email: "citizen@compose.coverage",
          displayName: "Compose Coverage Citizen",
          roles: ["CITIZEN"],
          actorType: "CITIZEN",
          assuranceLevel: "VERIFIED",
        }),
      );
    }, COMPOSE_CLIENT_ID);
  });

  test("coverage hub loads governed seed plans via BFF", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/coverage`);
    await expect(page.getByRole("heading", { name: "Coverage Operations" })).toBeVisible({ timeout: 60_000 });
    await page.getByRole("button", { name: "Schemes" }).click();
    await expect(page.locator("body")).toContainText(/COV-MOHCC-CORE|MOHCC National Core Scheme/i, { timeout: 30_000 });
  });

  test("enrollment journey against real coverage-service", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/coverage/enroll`);
    await expect(page.getByTestId("coverage-enroll-plan-select")).toBeVisible({ timeout: 60_000 });

    await page.getByTestId("coverage-enroll-plan-select").selectOption(COMPOSE_PLAN_ID);
    await page.getByTestId("coverage-run-eligibility").click();
    await expect(page.getByTestId("coverage-eligibility-result")).toContainText(/ELIGIBLE/i, { timeout: 30_000 });

    await page.getByTestId("coverage-member-number").fill(COMPOSE_MEMBER_NUMBER);
    await page.getByTestId("coverage-enroll-submit").click();
    await expect(page.getByTestId("coverage-enroll-success")).toBeVisible({ timeout: 30_000 });

    await page.goto(`${UI_ORIGIN}/coverage/member`);
    await expect(page.locator("body")).toContainText(/MOHCC National Core Scheme|COV-MOHCC-CORE/i, { timeout: 30_000 });
    await expect(page.locator("body")).toContainText(COMPOSE_MEMBER_NUMBER);
  });

  test("subsidies tab loads programmes from real coverage-service", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/coverage`);
    await expect(page.getByRole("heading", { name: "Coverage Operations" })).toBeVisible({ timeout: 60_000 });
    await page.getByRole("button", { name: "Subsidies" }).click();
    await expect(page.getByTestId("coverage-subsidies-tab")).toBeVisible();
    await expect(page.locator("body")).toContainText(/SUB-MOHCC-PRIMARY|MOHCC Primary Care Subsidy/i, { timeout: 30_000 });
  });
});
