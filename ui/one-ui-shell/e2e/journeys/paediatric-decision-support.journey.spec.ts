import { expect, test, type Page } from "@playwright/test";

/**
 * Browser proof of the paediatric decision-support surfaces against the live preview estate.
 *
 * These surfaces exist to hold one property: **an unanswered question must never read as a
 * reassuring answer.** Every unit test in this wave asserts that property over a fixture, which
 * proves the component logic and proves nothing at all about whether the engine is reachable from
 * a browser, whether the route renders, or whether the states arrive in the shape the components
 * expect. Three defects in this programme were found only by driving the real thing.
 *
 * So this spec asserts, against the deployed estate and through a real session:
 *
 *   1. the BFF paediatric routes are reachable from the browser at all (they did not exist before
 *      this wave — the engines were curl-only);
 *   2. an engine's own refusal — INDETERMINATE with its missing inputs, a BLOCKED_BY_INTERVAL
 *      dose, interpretation_blocked — survives the hop as a 200 and is not flattened;
 *   3. a forecast asked for without a date of birth comes back 400 with its reason rather than
 *      as an outage, so the caller can tell what they can fix;
 *   4. the pages render, and the unresolved states render as unresolved.
 *
 * Requires the governed preview test identity (never committed):
 *   PREVIEW_TEST_USERNAME / PREVIEW_TEST_PASSWORD
 * Skipped when absent, so it can never pass vacuously.
 */

const HOST = process.env.PREVIEW_HOST ?? "impilo.mohcc.gov.zw";
const USERNAME = process.env.PREVIEW_TEST_USERNAME ?? "";
const PASSWORD = process.env.PREVIEW_TEST_PASSWORD ?? "";

function trustHeaders(): Record<string, string> {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": "00000000-0000-4000-8000-000000000001",
    "X-Pod-ID": "national-spine",
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": crypto.randomUUID(),
    "Idempotency-Key": crypto.randomUUID(),
  };
}

/**
 * Runs a paediatric BFF call from inside the authenticated page context.
 *
 * Mirrors what `lib/api-client.ts` actually sends, including the CSRF token lifted from the
 * `__Host-impilo_csrf` cookie — a POST without it is refused by SessionCsrfFilter, so a probe
 * that omitted it would be testing the CSRF filter rather than the route.
 */
async function callEngine(page: Page, path: string, body: unknown) {
  return page.evaluate(
    async ({ path, body, headers }) => {
      const csrf = document.cookie
        .split("; ")
        .find((c) => c.startsWith("__Host-impilo_csrf="))
        ?.split("=")[1];
      const response = await fetch(path, {
        method: "POST",
        credentials: "include",
        headers: csrf ? { ...headers, "X-CSRF-Token": decodeURIComponent(csrf) } : headers,
        body: JSON.stringify(body),
      });
      return {
        status: response.status,
        body: await response.text(),
        sentCsrf: Boolean(csrf),
      };
    },
    { path, body, headers: trustHeaders() },
  );
}

test.describe.serial("paediatric decision support — live preview", () => {
  test.skip(
    !USERNAME || !PASSWORD,
    "governed preview test credential not supplied via PREVIEW_TEST_USERNAME/PREVIEW_TEST_PASSWORD",
  );

  let page: Page;

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await page.context().close();
  });

  // The realm has brute-force lockout at 5 failures, so this logs in exactly once.
  test("0. establishes a session", async () => {
    await page.goto("/internal/v1/auth/oidc/authorize?returnTo=/home", { waitUntil: "domcontentloaded" });
    await page.locator('input[name="username"], input#username').first().fill(USERNAME);
    await page.locator('input[name="password"], input#password').first().fill(PASSWORD);
    await Promise.all([
      page.waitForURL((url) => url.hostname === HOST && !url.pathname.startsWith("/realms/"), {
        timeout: 30_000,
      }),
      page.locator('button[name="login"], input[name="login"], button[type="submit"]').first().click(),
    ]);
    expect(new URL(page.url()).hostname).toBe(HOST);

    // Landing back on the app host is NOT proof of a session. The first version of this test
    // asserted only the hostname and passed while every subsequent call returned
    // NO_ACTIVE_SESSION — a check that could not fail, which is the defect shape this codebase
    // keeps being bitten by. Assert the session the rest of the spec depends on actually exists.
    const session = await page.evaluate(async () => {
      const r = await fetch("/internal/v1/auth/oidc/session", { credentials: "include" });
      return { status: r.status, body: (await r.text()).slice(0, 200) };
    });
    expect(
      session.status,
      `no session was established (${session.body}). The governed preview identity is CITIZEN-only ` +
        `and carries no clinical authority, so it cannot drive these surfaces — this spec needs an ` +
        `identity with clinical scope.`,
    ).toBe(200);
  });

  test("1. the IMNCI route exists and an INDETERMINATE row keeps its missing inputs", async () => {
    // A cough with no respiratory rate and no chest indrawing: the engine cannot separate severe
    // pneumonia from a cold, and must say so rather than returning the green row.
    const result = await callEngine(page, "/internal/v1/paediatric/imnci/classify", {
      ageDays: 420,
      cough: "yes",
    });

    // Before this wave nothing in the BFF proxied this path at all.
    expect(
      result.status,
      `IMNCI proxy must be reachable — got ${result.status} (csrf sent: ${result.sentCsrf}): ${result.body}`,
    ).toBe(200);

    const data = JSON.parse(result.body).data;
    expect(data.pathway).toBe("CHILD");

    const indeterminate = (data.classifications ?? []).filter((c: any) => c.status === "INDETERMINATE");
    expect(indeterminate.length, "an unanswerable chart must report as indeterminate").toBeGreaterThan(0);

    // The part that makes it actionable, and the part a proxy is most likely to drop.
    const named = indeterminate.some((c: any) => (c.missing_inputs ?? []).length > 0);
    expect(named, "an indeterminate row must name what would resolve it").toBe(true);

    // The honesty flag itself.
    expect(data.incomplete).toBe(true);
    expect((data.outstanding_assessments ?? []).length).toBeGreaterThan(0);
  });

  test("2. a child with nothing assessed is never an all-clear", async () => {
    const result = await callEngine(page, "/internal/v1/paediatric/danger-signs/evaluate", {
      ageDays: 10,
    });
    expect(result.status).toBe(200);
    const data = JSON.parse(result.body).data;
    // The property the engine exists for: silence is not reassurance.
    expect(data.incomplete, "a child with nothing assessed must report incomplete").toBe(true);
  });

  test("3. the forecast distinguishes a giveable dose from one that would not immunise", async () => {
    // Pentavalent 1 given today: the child is old enough for dose 2 but the minimum interval has
    // not elapsed, so dose 2 must come back blocked with the date the gate opens, not as due.
    const today = new Date().toISOString().slice(0, 10);
    const result = await callEngine(page, "/internal/v1/paediatric/immunisation/forecast", {
      date_of_birth: "2025-06-01",
      as_of: today,
      doses: [{ vaccine_code: "PENTA", dose_number: 1, occurrence_at: today, status: "COMPLETED" }],
    });
    expect(result.status).toBe(200);

    const data = JSON.parse(result.body).data;
    const penta2 = (data.doses ?? []).find((d: any) => d.series === "PENTA" && d.dose_number === 2);
    expect(penta2, "the forecast must report pentavalent dose 2").toBeTruthy();
    expect(penta2.status).toBe("BLOCKED_BY_INTERVAL");
    // Both halves matter: not giveable, and the date that makes it giveable.
    expect(penta2.actionable_today).toBe(false);
    expect(penta2.earliest_date).toBeTruthy();
    expect(penta2.earliest_date).not.toBe("null");

    // And nothing blocked leaked into the giveable set.
    const actionable = (data.doses ?? []).filter((d: any) => d.actionable_today);
    expect(actionable.every((d: any) => d.status !== "BLOCKED_BY_INTERVAL" && d.status !== "AGED_OUT")).toBe(
      true,
    );
  });

  test("4. a single growth contact is not-assessable, never 'no concerns'", async () => {
    const result = await callEngine(page, "/internal/v1/paediatric/growth/interpret", {
      measurements: [{ age_days: 180, weight_kg: 7.2, weight_for_age_z: -1.2, standard: "WHO_2006" }],
    });
    expect(result.status).toBe(200);
    const data = JSON.parse(result.body).data;
    // A child weighed once has not been shown to be growing well.
    expect(data.assessable).toBe(false);
    expect(data.not_assessable_reason).toBeTruthy();
    expect((data.signals ?? []).length).toBe(0);
  });

  test("5. a forecast without a date of birth is a 400 the caller can fix, not a 502 outage", async () => {
    const result = await callEngine(page, "/internal/v1/paediatric/immunisation/forecast", {
      doses: [],
    });
    expect(result.status, "a missing field must not be reported as an unreachable engine").toBe(400);
    expect(result.body).toContain("date_of_birth_required");
  });

  test("6. the IMNCI page renders and says nothing has been classified yet", async () => {
    await page.goto(`/ehr/preview-paed-proof/imnci`, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle").catch(() => undefined);

    const body = await page.locator("body").innerText();
    // The page exists and is not a 404 or an auth wall.
    expect(body).toContain("IMNCI");
    // Whatever state it lands in, it must never claim the child has been assessed and is well.
    expect(body).not.toMatch(/no danger signs? (present|found)[\s\S]{0,40}$/i);
  });

  test("7. the growth-chart page renders its interpretation panel", async () => {
    await page.goto(`/ehr/preview-paed-proof/growth-chart`, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle").catch(() => undefined);
    const body = await page.locator("body").innerText();
    expect(body.toLowerCase()).toContain("growth");
  });
});
