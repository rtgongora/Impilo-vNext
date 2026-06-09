import { test, expect } from "@playwright/test";

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const PACS_ORIGIN = process.env.PLAYWRIGHT_PACS_URL || "http://localhost:8113";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, pacs] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${PACS_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && pacs);
  } catch {
    return false;
  }
}

test.describe("Imaging viewer (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, pacs ${PACS_ORIGIN}`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-imaging-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-radiologist-001",
          roles: ["CLINICIAN", "RADIOLOGIST"],
          actorType: "PROVIDER",
        }),
      );
    });
  });

  test("imaging workspace loads seed study via BFF", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/ehr/CPID-ZW-00001/imaging`);
    await expect(page.getByRole("heading", { name: /Imaging|Radiology/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.locator("body")).toContainText(/Chest X-ray|1.2.840.IMPILO/i, { timeout: 30_000 });
  });
});
