import { test, expect } from "@playwright/test";

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff);
  } catch {
    return false;
  }
}

function seedExperienceSession() {

  const user = {

    id: "e2e-user",

    name: "E2E User",

    displayName: "E2E User",

    roles: ["SYSTEM_ADMIN", "CLINICIAN"],

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



test.describe("Production readiness surfaces", () => {
  test.beforeAll(async () => {
    if (process.env.PLAYWRIGHT_COMPOSE_E2E === "1") {
      test.skip(
        !(await stackReachable()),
        `Compose stack required: UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}. Run: docker compose -f compose/experience/docker-compose.yml up --build`,
      );
    }
  });

  test.beforeEach(async ({ context, baseURL }) => {

    const origin = baseURL ?? "http://localhost:3000";

    await context.addCookies([

      {

        name: "exp_has_session",

        value: "1",

        url: origin,

      },

    ]);

    await context.addInitScript(seedExperienceSession);

  });



  test("command centre loads with backbone tiles, trust banner, and hub probe", async ({ page }) => {

    await page.goto("/production-command-centre", { waitUntil: "domcontentloaded" });

    await expect(page.getByRole("heading", { name: "Production Command Centre" })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

    await expect(page.getByTestId("related-services-panel")).toBeVisible();

    await expect(page.getByTestId("nompilo-context-panel")).toBeVisible();

    await expect(page.getByText("Core backbone")).toBeVisible();

    await expect(page.getByText("Vito client registry")).toBeVisible();

    await expect(

      page

        .getByText(/Integration hub: (checking…|live \(\d+ routes\)|unavailable)/)

        .or(page.getByTitle("Integration hub route probe via Experience BFF"))

        .first(),

    ).toBeVisible({ timeout: 15_000 });

  });



  test("Rx transaction journey stepper renders with trust and related services", async ({ page }) => {

    await page.goto("/pharmacy/transaction-journey?patientId=CPID-ZW-00001");

    await expect(page.getByRole("heading", { name: "Rx transaction journey" })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

    await expect(page.getByTestId("related-services-panel")).toBeVisible();

    await expect(page.getByTestId("nompilo-context-panel")).toBeVisible();

    await expect(page.getByText(/Prescription \(Rx\)/)).toBeVisible();

    await expect(page.getByText(/Dispatch \/ delivery \(Nhume\)/)).toBeVisible();

    const banner = page.getByTestId("rx-live-path-probes");

    await expect(banner).toBeVisible({ timeout: 15_000 });

    await expect(banner).toContainText("Pharmacy (Rx):");

  });



  test("clinical hub shows trust banner, Nompilo, and related services", async ({ page }) => {

    await page.goto("/clinical");

    await expect(page.getByRole("heading", { name: "Clinical Care" })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

    await expect(page.getByTestId("related-services-panel")).toBeVisible();

    await expect(page.getByTestId("nompilo-context-panel")).toBeVisible();

    await expect(page.getByRole("link", { name: "Core transactions" })).toBeVisible();

  });



  test("pharmacy hub shows trust banner, Nompilo, and related services", async ({ page }) => {
    await page.goto("/pharmacy");
    await expect(page.getByRole("heading", { name: "Pharmacy", exact: true })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

    await expect(page.getByTestId("related-services-panel")).toBeVisible();

    await expect(page.getByTestId("nompilo-context-panel")).toBeVisible();

    await expect(
      page.getByTestId("related-services-panel").getByRole("link", { name: "Rx transaction journey" }),
    ).toBeVisible();

  });



  test("core transaction hub is reachable", async ({ page }) => {

    await page.goto("/core-transaction");

    await expect(page.getByRole("heading", { name: "Core transactions" })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

    await expect(page.getByTestId("related-services-panel")).toBeVisible();

    await expect(page.getByTestId("nompilo-context-panel")).toBeVisible();

  });



  test("inpatient hub is reachable", async ({ page }) => {

    await page.goto("/clinical/inpatient");

    await expect(page.getByRole("heading", { name: "Inpatient care" })).toBeVisible();

    await expect(page.getByRole("link", { name: "Admissions", exact: true })).toBeVisible();

  });



  test("inpatient nursing workbench is reachable", async ({ page }) => {

    await page.goto("/clinical/inpatient/nursing");

    await expect(page.getByRole("heading", { name: "Nursing workbench" })).toBeVisible();

  });



  test("data intelligence hub is reachable", async ({ page }) => {

    await page.goto("/data-intelligence");

    await expect(page.getByRole("heading", { name: "Data & intelligence" })).toBeVisible();

    await expect(page.getByTestId("trust-context-banner")).toBeVisible();

  });



  test("role-aware home surfaces production readiness modules", async ({ page }) => {
    await page.goto("/home");
    await page.getByRole("button", { name: "Work", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Modules" })).toBeVisible();
    await page.getByRole("button", { name: /Production readiness/i }).click();

    await expect(page.getByRole("link", { name: "Core transactions" }).first()).toBeVisible();

    await expect(page.getByRole("link", { name: "Data & intelligence" }).first()).toBeVisible();

    await expect(page.getByRole("link", { name: "Rx transaction journey" }).first()).toBeVisible();

  });

});

