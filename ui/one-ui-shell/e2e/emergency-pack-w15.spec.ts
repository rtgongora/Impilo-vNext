/**
 * Emergency pack W15 — browser drive against a LIVE local estate.
 *
 * Unlike e2e/trauma-journey.spec.ts, this spec mocks nothing in the emergency domain. It
 * drives the real surfaces against a running chain:
 *
 *   next dev  ->  scripts/dev/browser-drive-identity-harness.mjs  ->  experience-bff
 *             ->  pct-service  ->  postgres
 *
 * The harness answers only the four professional-identity reads (linked IDs, affiliations,
 * workforce-governance assignments, session experience contract), which are owned by VITO /
 * the council registry / workforce-governance and are not booted locally. Every emergency
 * read and write in this file goes to the real BFF and the real pct-service, so an episode
 * opened here is an episode that exists in pct.emergency_episode.
 *
 * It self-skips when the estate is not up, so it never fails a run that was not set up for
 * it. Bring the estate up with docs/clinical/emergency-domain-pack/browser-drive.md, then:
 *
 *   PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://localhost:3007 \
 *     npx playwright test e2e/emergency-pack-w15.spec.ts --project=chromium
 */

import { test as base, expect } from "@playwright/test";
import type { Page } from "@playwright/test";

/** pct rejects a non-UUID facility, so this drive cannot reuse fixtures.ts's slug facility. */
const FACILITY_ID = process.env.DRIVE_FACILITY_ID ?? "11111111-1111-1111-1111-111111111111";
const PROVIDER_ID = process.env.DRIVE_PROVIDER_ID ?? "PRV-DRIVE-001";
const HEALTH_ID = process.env.DRIVE_HEALTH_ID ?? "local-drive-provider-001";

const driveUser = {
  id: HEALTH_ID,
  email: "drive@impilo.local",
  displayName: "Dr Local Drive",
  roles: ["CLINICIAN", "ADMIN", "QUEUE", "FACILITY_ADMIN"],
  actorType: "PROVIDER" as const,
  providerId: PROVIDER_ID,
  staffId: "STF-DRIVE-001",
  assuranceLevel: "VERIFIED" as const,
  providerActivated: true,
  loginMethod: "provider_id" as const,
};

const driveFacility = {
  id: FACILITY_ID,
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB", "IMAGING"],
};

const test = base.extend({
  page: async ({ page, context }, use) => {
    await context.addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(
      (data) => {
        sessionStorage.setItem("exp:auth_token", "local-drive-token");
        sessionStorage.setItem("exp:auth_user", JSON.stringify(data.user));
        sessionStorage.setItem("exp:provider_id", data.user.providerId);
        sessionStorage.setItem("exp:assurance_level", data.user.assuranceLevel);
        sessionStorage.setItem("exp:facility", JSON.stringify(data.facility));
        sessionStorage.setItem(
          "exp:workspace",
          JSON.stringify({ id: "ws-ed-001", name: "Emergency Department", type: "CLINICAL" }),
        );
        sessionStorage.setItem(
          "exp:shift",
          JSON.stringify({ id: "shift-001", startedAt: new Date().toISOString(), workspace: "ED" }),
        );
        localStorage.setItem(
          "exp:consent_accepted",
          JSON.stringify({ acceptedAt: new Date().toISOString(), version: "2026-04-11", userId: data.user.id }),
        );
        localStorage.setItem("exp:consent_version", "2026-04-11");
      },
      { user: driveUser, facility: driveFacility },
    );
    await use(page);
  },
});

/**
 * The whole point of this spec is that pct is real, so a run without it must skip rather
 * than pass. A summary that answers proves the full chain, not just that Next is serving.
 */
test.beforeAll(async ({ request, baseURL }) => {
  let reachable = false;
  try {
    const res = await request.get(
      `${baseURL}/internal/v1/emergency-episodes/command-summary?facilityId=${FACILITY_ID}`,
      {
        headers: {
          "X-Tenant-ID": "mohcc",
          "X-Pod-ID": "national-spine",
          "X-Request-ID": "emergency-w15-drive-probe",
          "X-Correlation-ID": "emergency-w15-drive-probe",
          "X-Facility-ID": FACILITY_ID,
          "X-Actor-ID": HEALTH_ID,
          "X-Actor-Type": "PROVIDER",
        },
        timeout: 10_000,
      },
    );
    reachable = res.ok();
  } catch {
    reachable = false;
  }
  test.skip(!reachable, "live estate not reachable — see docs/clinical/emergency-domain-pack/browser-drive.md");
});

/** Screenshots are the drive's evidence; keep them beside the other journey artefacts. */
async function shot(page: Page, name: string) {
  await page.screenshot({ path: `../../reports/journeys/emergency-pack-w15/${name}.png`, fullPage: true });
}

test.describe("emergency pack W15 — live browser drive", () => {
  test("a deep link into a clinical route renders instead of bouncing to /home", async ({ page }) => {
    // The regression fixed in this wave: the identity reads had not settled, the citizen
    // heuristic was briefly true, and the guard acted on it before the page could render.
    await page.goto("/clinical/emergency/command");
    await expect(page).toHaveURL(/\/clinical\/emergency\/command$/);
    await expect(page.getByRole("heading", { name: "Emergency command" })).toBeVisible();
  });

  test("the command board reads live pct counts and names its blind spots", async ({ page }) => {
    const boardResponse = page.waitForResponse(
      (r) => r.url().includes("/emergency-episodes/command-board") && r.request().method() === "GET",
    );
    await page.goto("/clinical/emergency/command");
    const board = page.getByTestId("command-board");
    await expect(board).toBeVisible();

    // Every count on this board names where it came from. pct answered the facility summary
    // here, so the tile is the summary's own tally and carries no fallback note; the note
    // appears only when the board had to count the episodes it happened to load instead.
    const payload = await (await boardResponse).json();
    expect(payload.items?.summary).toBeTruthy();
    await expect(page.getByTestId("command-tally-source")).toHaveCount(0);
    await expect(page.getByTestId("command-tile-Episodes by state")).toBeVisible();

    // An empty alert queue must say it is empty, never render as a blind spot, and vice versa.
    const empty = page.getByTestId("alert-queue-empty");
    const blind = page.getByTestId("alert-queue-blind");
    const queue = page.getByTestId("alert-queue");
    expect(
      (await empty.count()) + (await blind.count()) + (await queue.count()),
    ).toBeGreaterThan(0);
    await shot(page, "command-board");
  });

  test("opening an episode writes to pct and lands on that episode's spine", async ({ page }) => {
    await page.goto("/clinical/emergency/activation");
    await expect(page.getByTestId("activation-form")).toBeVisible();

    await page.getByTestId("activation-entry-route").selectOption("AMBULANCE");
    await page.getByTestId("activation-episode-class").selectOption("TRAUMA");
    await shot(page, "activation-form");

    const created = page.waitForResponse(
      (r) => r.url().endsWith("/internal/v1/emergency-episodes") && r.request().method() === "POST",
    );
    await page.getByTestId("activation-submit").click();

    // A rejected write shows as "could not be retrieved" in the UI, which says nothing about
    // why. Carry pct's own answer into the failure so a red run is diagnosable.
    const response = await created;
    expect(response.status(), await response.text()).toBe(201);

    // pct mints the id; the redirect proves the write landed, not just that the form posted.
    await expect(page).toHaveURL(/\/clinical\/emergency\/spine\/[0-9a-f-]{36}$/, { timeout: 20_000 });
    await expect(page.getByTestId("activation-error")).toHaveCount(0);
    await shot(page, "episode-spine");

    const episodeId = new URL(page.url()).pathname.split("/").pop() as string;

    // Observation stay: the panel must distinguish "nothing recorded" from "could not be read".
    await page.goto(`/clinical/emergency/spine/${episodeId}/observation`);
    await expect(page.getByTestId("observation-unreadable")).toHaveCount(0);
    await expect(page.getByTestId("observation-panel")).toBeVisible();
    await shot(page, "observation-stay");

    // Disposition: pct owns which fields each of the fifteen types requires; the form guides
    // but never enforces, so the hint is what we assert on.
    await page.goto(`/clinical/emergency/spine/${episodeId}/disposition`);
    await expect(page.getByTestId("disposition-unreadable")).toHaveCount(0);
    await expect(page.getByTestId("disposition-form")).toBeVisible();
    await page.getByTestId("disposition-type").selectOption("TRANSFERRED_OUT");
    await expect(page.getByTestId("disposition-destination")).toBeVisible();
    await shot(page, "disposition-form");
  });

  test("the pre-arrival board reads the live ED projection", async ({ page }) => {
    await page.goto("/clinical/emergency/pre-arrival");
    await expect(page.getByTestId("pre-arrival-form")).toBeVisible();
    await expect(page.getByTestId("pre-arrival-unreadable")).toHaveCount(0);
    await shot(page, "pre-arrival");
  });

  test("analytics names every missing measure rather than rendering zeros", async ({ page }) => {
    await page.goto("/clinical/emergency/analytics");
    await expect(page.getByTestId("analytics-w17-live")).toBeVisible();
    await expect(page.getByTestId("analytics-live-measures")).toBeVisible();
    const measures = page.getByTestId("analytics-pending-measure");
    await expect(measures.first()).toBeVisible();
    // A zero on an emergency dashboard reads as "no deaths", so there must be no figures.
    await expect(page.getByTestId("analytics-pending-measures")).not.toContainText(/\b0\b/);
    await shot(page, "analytics-named-absence");
  });

  test("the mental health record workspace and restraint backlog render", async ({ page }) => {
    await page.goto("/work/mental-health");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await shot(page, "mental-health-referrals");

    await page.goto("/work/mental-health/restraint-review");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await shot(page, "mental-health-restraint-review");
  });
});
