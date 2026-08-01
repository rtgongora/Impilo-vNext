import { expect, test, type Page } from "@playwright/test";

/**
 * CP3 authenticated browser runtime proof against the live preview.
 *
 * Requires a governed preview test identity supplied via env (never committed here):
 *   PREVIEW_TEST_USERNAME / PREVIEW_TEST_PASSWORD
 * The suite is skipped when the credential is absent so it can never "pass" vacuously.
 *
 * Proves, in one serial journey (single login attempt — the realm has brute-force
 * lockout at 5 failures, so this spec must never retry credentials):
 *   1. authorization-code + PKCE login establishes a session via the BFF callback;
 *   2. Set-Cookie attributes: __Host-impilo_session is Secure + HttpOnly + Path=/ +
 *      SameSite and carries no Domain attribute (required for the __Host- prefix);
 *   3. the browser cookie is opaque (no JWT structure) and the CSRF cookie is separate;
 *   4. authenticated session-status returns the principal without any token material;
 *   5. sessionStorage/localStorage/document.cookie carry no tokens;
 *   6. cookie-authenticated mutation without X-CSRF-Token is rejected (CSRF_REJECTED);
 *   7. logout with CSRF succeeds and clears the session;
 *   8. post-logout session-status fails closed;
 *   9. replaying the consumed callback URL never re-establishes a session.
 */

const HOST = process.env.PREVIEW_HOST ?? "impilo.mohcc.gov.zw";
const USERNAME = process.env.PREVIEW_TEST_USERNAME ?? "";
const PASSWORD = process.env.PREVIEW_TEST_PASSWORD ?? "";

const TOKEN_LIKE = /(access_token|refresh_token|id_token|eyJ[A-Za-z0-9_-]{10,}\.)/;

test.describe.serial("authenticated preview session proof", () => {
  test.skip(!USERNAME || !PASSWORD,
    "governed preview test credential not supplied via PREVIEW_TEST_USERNAME/PREVIEW_TEST_PASSWORD");

  let page: Page;
  let callbackUrl = "";
  let callbackSetCookies: string[] = [];
  let callbackStatus = 0;

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await page.context().close();
  });

  test("1. PKCE login establishes a session through the BFF callback", async () => {
    page.on("response", async (response) => {
      if (response.url().includes("/internal/v1/auth/oidc/callback")) {
        callbackUrl = response.url();
        callbackStatus = response.status();
        callbackSetCookies = (await response.headersArray())
          .filter((h) => h.name.toLowerCase() === "set-cookie")
          .map((h) => h.value);
      }
    });

    await page.goto("/internal/v1/auth/oidc/authorize?returnTo=/home", { waitUntil: "domcontentloaded" });
    expect(page.url()).toContain(`https://${HOST}/realms/impilo/protocol/openid-connect/auth`);

    // Keycloak login form (Impilo-themed, field ids stable across KC 24–26).
    await page.locator('input[name="username"], input#username').first().fill(USERNAME);
    await page.locator('input[name="password"], input#password').first().fill(PASSWORD);
    await Promise.all([
      page.waitForURL((url) => url.hostname === HOST && !url.pathname.startsWith("/realms/"), { timeout: 20_000 }),
      page.locator('button[name="login"], input[name="login"], button[type="submit"]').first().click(),
    ]);

    expect(callbackStatus, "BFF callback must redirect (302) into the app").toBe(302);
    expect(callbackUrl).toContain("state=");
    expect(callbackUrl).toContain("code=");
    // Landed back inside the app shell, not on an IdP or error page.
    expect(new URL(page.url()).hostname).toBe(HOST);
    expect(page.url()).not.toContain("/realms/");
  });

  test("2. Set-Cookie attributes satisfy the __Host- contract", async () => {
    const sessionSetCookie = callbackSetCookies.find((c) => c.startsWith("__Host-impilo_session="));
    expect(sessionSetCookie, "callback must set __Host-impilo_session").toBeTruthy();
    const attrs = sessionSetCookie!.toLowerCase();
    expect(attrs).toContain("secure");
    expect(attrs).toContain("httponly");
    expect(attrs).toContain("path=/");
    expect(attrs).toContain("samesite");
    expect(attrs).not.toContain("domain=");

    const csrfSetCookie = callbackSetCookies.find((c) => /csrf/i.test(c.split("=")[0]));
    expect(csrfSetCookie, "callback must set the CSRF companion cookie").toBeTruthy();
  });

  test("3. session cookie is opaque, never a JWT", async () => {
    const cookies = await page.context().cookies(`https://${HOST}`);
    const session = cookies.find((c) => c.name === "__Host-impilo_session");
    expect(session).toBeTruthy();
    expect(session!.value).not.toMatch(/^eyJ/);
    expect(session!.value.split(".").length).toBeLessThan(3);
    expect(session!.httpOnly).toBe(true);
    expect(session!.secure).toBe(true);
  });

  test("4. authenticated session-status returns principal without token material", async () => {
    const result = await page.evaluate(async () => {
      const response = await fetch("/internal/v1/auth/oidc/session", { credentials: "include" });
      return { status: response.status, body: await response.text() };
    });
    expect(result.status).toBe(200);
    expect(result.body).not.toMatch(TOKEN_LIKE);
    const body = JSON.parse(result.body);
    expect(body?.data ?? body).toBeTruthy();
  });

  test("5. no token material in browser storage after login", async () => {
    const storage = await page.evaluate(() => {
      const dump = (s: Storage) => JSON.stringify(Object.entries({ ...s }));
      return { local: dump(window.localStorage), session: dump(window.sessionStorage), cookies: document.cookie };
    });
    expect(storage.local).not.toMatch(TOKEN_LIKE);
    expect(storage.session).not.toMatch(TOKEN_LIKE);
    expect(storage.cookies).not.toMatch(TOKEN_LIKE);
    // HttpOnly session cookie must be invisible to JS.
    expect(storage.cookies).not.toContain("__Host-impilo_session");
  });

  test("6. cookie-authenticated mutation without CSRF header is rejected", async () => {
    const result = await page.evaluate(async () => {
      const response = await fetch("/internal/v1/auth/oidc/logout", { method: "POST", credentials: "include" });
      return { status: response.status, body: await response.text() };
    });
    expect(result.status).toBe(403);
    expect(result.body).toContain("CSRF_REJECTED");
    // Session must still be alive after the rejected mutation.
    const still = await page.evaluate(async () =>
      (await fetch("/internal/v1/auth/oidc/session", { credentials: "include" })).status);
    expect(still).toBe(200);
  });

  test("7. logout with CSRF token succeeds; 8. post-logout fails closed", async () => {
    const cookies = await page.context().cookies(`https://${HOST}`);
    const csrf = cookies.find((c) => /csrf/i.test(c.name));
    expect(csrf, "CSRF cookie must exist for the logout").toBeTruthy();

    const logout = await page.evaluate(async (token) => {
      const response = await fetch("/internal/v1/auth/oidc/logout", {
        method: "POST",
        credentials: "include",
        headers: { "X-CSRF-Token": token },
      });
      return response.status;
    }, csrf!.value);
    expect(logout).toBe(200);

    const after = await page.evaluate(async () => {
      const response = await fetch("/internal/v1/auth/oidc/session", { credentials: "include" });
      return { status: response.status, body: await response.text() };
    });
    expect(after.status).toBe(401);
    expect(after.body).toContain("NO_ACTIVE_SESSION");
  });

  test("9. replaying the consumed callback never re-establishes a session", async () => {
    expect(callbackUrl, "callback URL captured during login").toBeTruthy();
    const replay = await page.goto(callbackUrl, { waitUntil: "domcontentloaded" }).catch(() => null);
    // The single-use transaction was consumed at login: replay must be refused.
    // (Deployed build currently surfaces this as a 5xx INTERNAL_ERROR; source now
    // maps OidcProtocolException to 400 — either way it must NOT be a 2xx/3xx
    // that mints a session.)
    if (replay) expect(replay.status()).toBeGreaterThanOrEqual(400);

    const session = await page.evaluate(async () =>
      (await fetch("/internal/v1/auth/oidc/session", { credentials: "include" })).status);
    expect(session).toBe(401);
  });
});
