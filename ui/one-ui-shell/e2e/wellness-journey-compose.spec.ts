import { test, expect } from "@playwright/test";

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const SIMBA_ORIGIN = process.env.PLAYWRIGHT_SIMBA_URL || "http://localhost:8125";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, simba] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${SIMBA_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && simba);
  } catch {
    return false;
  }
}

test.describe("Wellness journey (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose with simba-service: docker compose -f compose/experience/docker-compose.yml up — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, Simba ${SIMBA_ORIGIN}`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-e2e-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-wellness-cpid-001",
          email: "citizen@compose.wellness",
          displayName: "Compose Wellness Citizen",
          roles: ["CITIZEN", "DEVELOPER"],
          actorType: "CITIZEN",
        }),
      );
      localStorage.setItem(
        "exp:consent_accepted",
        JSON.stringify({
          userId: "compose-wellness-cpid-001",
          version: "2026-04-11",
          acceptedAt: new Date().toISOString(),
        }),
      );
      localStorage.setItem("exp:consent_version", "2026-04-11");
    });
  });

  test("goals write and clubs catalogue from real Simba via BFF", async ({ page }) => {
    const goalPostUrls: string[] = [];
    const clubsUrls: string[] = [];

    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/wellness/goals") && req.method() === "POST") goalPostUrls.push(u);
      if (u.includes("/internal/v1/wellness/clubs") && req.method() === "GET") clubsUrls.push(u);
    });

    await page.goto(`${UI_ORIGIN}/wellness/goals`);
    await expect(page.getByRole("heading", { name: "Health Goals" })).toBeVisible({ timeout: 60_000 });
    await page.getByTestId("wellness-goal-template-steps").click();
    await expect.poll(() => goalPostUrls.length, { timeout: 60_000 }).toBeGreaterThan(0);
    expect(goalPostUrls[0]).toMatch(/^https?:\/\/localhost:8160\//);

    await page.goto(`${UI_ORIGIN}/wellness/clubs`);
    await expect(page.getByText("Harare Morning Walkers")).toBeVisible({ timeout: 60_000 });
    await expect.poll(() => clubsUrls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  });
});
