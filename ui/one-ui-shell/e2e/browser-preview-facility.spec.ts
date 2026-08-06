import { expect, test, type Page } from "@playwright/test";

/**
 * Can a signed-in person actually reach the facility master?
 *
 * The estate holds 7,284 facilities on the REGISTRY plane
 * (`00000000-0000-0000-0000-000000000001`), while an authenticated session presents the
 * CARE plane by default. The BFF pins the registry plane per-lane and the trust
 * interceptor forwards X-Tenant-ID only-if-absent, so in source that pin survives. This
 * spec exists because "in source" is not proof: nothing had ever watched a real signed-in
 * browser ask for facilities and counted what came back.
 *
 * It fails on an empty result rather than reporting a healthy page, because an empty
 * facility list is exactly the silent failure the two-plane split produces — a screen that
 * renders perfectly and shows nothing.
 */

const HOST = process.env.PREVIEW_HOST ?? "impilo.mohcc.gov.zw";
const USERNAME = process.env.PREVIEW_TEST_USERNAME ?? "";
const PASSWORD = process.env.PREVIEW_TEST_PASSWORD ?? "";

interface ApiCall {
  url: string;
  status: number;
  items: number | null;
  tenant: string | null;
}

/** Count the rows a payload actually carries, whatever envelope it uses. */
function countItems(body: unknown): number | null {
  if (Array.isArray(body)) return body.length;
  if (body && typeof body === "object") {
    for (const key of ["content", "items", "results", "facilities", "data"]) {
      const v = (body as Record<string, unknown>)[key];
      if (Array.isArray(v)) return v.length;
    }
    const total = (body as Record<string, unknown>).totalElements;
    if (typeof total === "number") return total;
  }
  return null;
}

test.describe.serial("signed-in facility surface", () => {
  test.skip(!USERNAME || !PASSWORD,
    "governed preview test credential not supplied via PREVIEW_TEST_USERNAME/PREVIEW_TEST_PASSWORD");

  let page: Page;
  const calls: ApiCall[] = [];

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    page = await context.newPage();

    page.on("response", async (response) => {
      const url = response.url();
      if (!url.includes("/internal/v1/") && !url.includes("/api/v1/")) return;
      let items: number | null = null;
      try {
        const ct = response.headers()["content-type"] ?? "";
        if (ct.includes("json")) items = countItems(await response.json());
      } catch {
        // A body that cannot be read is not a body with zero rows — leave it null.
      }
      calls.push({
        url: new URL(url).pathname + new URL(url).search,
        status: response.status(),
        items,
        tenant: response.request().headers()["x-tenant-id"] ?? null,
      });
    });
  });

  test.afterAll(async () => {
    await page.context().close();
  });

  test("1. sign in once (the realm has brute-force protection)", async () => {
    await page.goto("/internal/v1/auth/oidc/authorize?returnTo=/welcome/find-care",
      { waitUntil: "domcontentloaded" });
    expect(page.url()).toContain(`https://${HOST}/realms/impilo/protocol/openid-connect/auth`);

    await page.locator('input[name="username"], input#username').first().fill(USERNAME);
    await page.locator('input[name="password"], input#password').first().fill(PASSWORD);
    await Promise.all([
      page.waitForURL((url) => url.hostname === HOST && !url.pathname.startsWith("/realms/"),
        { timeout: 20_000 }),
      page.locator('button[name="login"], input[name="login"], button[type="submit"]').first().click(),
    ]);
    expect(new URL(page.url()).hostname).toBe(HOST);
  });

  test("2. the facility surface loads for a signed-in person", async () => {
    calls.length = 0;
    const response = await page.goto("/welcome/find-care", { waitUntil: "networkidle" });
    expect(response?.status(), "the facility surface must render, not 4xx/5xx").toBeLessThan(400);
    // Give client-side fetches a moment beyond networkidle to settle.
    await page.waitForTimeout(2500);
  });

  test("3. it asks the estate for facilities and is not refused", async () => {
    const refused = calls.filter((c) => c.status === 401 || c.status === 403);
    // eslint-disable-next-line no-console
    console.log("\n  API calls made by the facility surface:\n" +
      calls.map((c) => `    ${c.status}  items=${c.items ?? "-"}  ${c.url}`).join("\n") + "\n");
    expect(calls.length, "the surface must actually call the estate").toBeGreaterThan(0);
    expect(refused.map((c) => `${c.status} ${c.url}`),
      "a signed-in person must not be refused on the facility surface").toEqual([]);
  });

  test("4. the facility master answers the signed-in session with rows", async () => {
    // Ask directly rather than hunting for a screen that happens to list facilities.
    // /welcome/find-care is a landing page — it explains the service and searches nothing,
    // so navigating it proves only that the shell renders. This uses the browser's real
    // session cookie and the exact trust headers api-client injects, including the
    // CARE-plane tenant the shell defaults to. That is the whole question: does the BFF's
    // per-lane registry-plane pin survive a care-plane caller?
    const result = await page.evaluate(async () => {
      const res = await fetch("/internal/v1/registry/facilities?size=5&page=0", {
        credentials: "include",
        headers: {
          "X-Tenant-ID": "00000000-0000-4000-8000-000000000001", // care plane, as the shell sends
          "X-Pod-ID": "national-spine",
          "X-Request-ID": crypto.randomUUID(),
          "X-Correlation-ID": crypto.randomUUID(),
        },
      });
      let body: unknown = null;
      try { body = await res.json(); } catch { /* non-JSON body stays null, not zero */ }
      // Count here, where the whole body is. Counting after truncating the JSON for display
      // is how the previous revision turned a passing result into a parse error.
      let rows: number | null = null;
      if (Array.isArray(body)) rows = body.length;
      else if (body && typeof body === "object") {
        for (const key of ["data", "content", "items", "results", "facilities"]) {
          const v = (body as Record<string, unknown>)[key];
          if (Array.isArray(v)) { rows = v.length; break; }
        }
      }
      const names = (((body as Record<string, unknown>)?.data as Array<Record<string, never>>) ?? [])
        .slice(0, 3)
        .map((r) => (r?.attributes as Record<string, string> | undefined)?.name ?? "?");
      return { status: res.status, rows, names };
    });

    // eslint-disable-next-line no-console
    console.log(`  /internal/v1/registry/facilities -> ${result.status}, rows=${result.rows}` +
      `\n  sample: ${result.names.join(" | ")}`);

    expect(result.status,
      "a signed-in person must not be refused the facility register").toBeLessThan(400);

    const rows = result.rows;
    expect(rows, "the response must carry a countable collection").not.toBeNull();
    // The registry plane holds 7,284 facilities; the care-plane slice holds 1. This
    // threshold separates "reached the master" from "read the wrong plane" without pinning
    // a number that legitimate paging or filtering would change.
    expect(rows ?? 0,
      "the signed-in session must reach the facility master, not the 1-row care-plane slice")
      .toBeGreaterThan(1);
  });
});
