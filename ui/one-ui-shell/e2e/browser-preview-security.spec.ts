import { expect, test } from "@playwright/test";

/**
 * CP3 browser runtime proof (unauthenticated-observable facets) against the live preview.
 *
 * Proves: PKCE S256 + state + nonce on the authorize redirect, correct Keycloak issuer,
 * anonymous session-status fail-closed, open-redirect rejection, and the server-side token
 * storage boundary (no access/refresh token reaches browser storage or JS-visible cookies).
 *
 * All assertions are driven through the real browser (`page`) rather than the Node request
 * API, because only Chromium honours the host-resolver mapping to the in-cluster ingress.
 *
 * Authenticated completion (session cookie set, authenticated session body, logout
 * revocation) requires a live preview credential that is a deployment secret not available
 * to the automated runner; that facet is proven by the BFF unit tests and is recorded as
 * INSUFFICIENT_EVIDENCE for browser runtime here.
 */

const HOST = process.env.PREVIEW_HOST ?? "impilo.mohcc.gov.zw";

test("authorize redirect carries PKCE S256, state and nonce to the Keycloak realm", async ({ page }) => {
  await page.goto("/internal/v1/auth/oidc/authorize?returnTo=/home", { waitUntil: "domcontentloaded" });
  // The browser follows the 302 into the real Keycloak realm login page; its URL preserves
  // the OAuth request parameters we minted server-side.
  const landed = page.url();
  expect(landed).toContain(`https://${HOST}/realms/impilo/protocol/openid-connect/auth`);
  expect(landed).toContain("response_type=code");
  expect(landed).toContain("code_challenge_method=S256");
  expect(landed).toMatch(/[?&]state=/);
  expect(landed).toMatch(/[?&]nonce=/);
  expect(landed).toMatch(/[?&]code_challenge=/);
  expect(landed).not.toContain("response_type=token");
});

test("anonymous session-status fails closed with NO_ACTIVE_SESSION", async ({ page }) => {
  const response = await page.goto("/internal/v1/auth/oidc/session", { waitUntil: "domcontentloaded" });
  expect(response?.status()).toBe(401);
  const body = await response!.json();
  expect(body?.error?.code).toBe("NO_ACTIVE_SESSION");
});

test("open-redirect returnTo values never reach the attacker host", async ({ page }) => {
  for (const evil of ["https://attacker.example/x", "//attacker.example/x"]) {
    await page.goto(`/internal/v1/auth/oidc/authorize?returnTo=${encodeURIComponent(evil)}`,
      { waitUntil: "domcontentloaded" }).catch(() => undefined);
    // The browser must never navigate TO the attacker host, and must not have been bounced
    // into Keycloak carrying the attacker destination (returnTo is rejected before any IdP
    // round-trip). The evil string may appear only inside our own request query, so assert on
    // the navigated hostname, not on raw substring presence.
    const navigatedHost = new URL(page.url()).hostname;
    expect(navigatedHost).toBe(HOST);
    expect(page.url()).not.toContain("/realms/impilo/protocol/openid-connect/auth");
  }
});

test("visiting the authorize entrypoint leaves no token material in the browser", async ({ page }) => {
  await page.goto("/internal/v1/auth/oidc/authorize?returnTo=/home", { waitUntil: "domcontentloaded" });
  const storage = await page.evaluate(() => {
    const dump = (s: Storage) => JSON.stringify(Object.entries({ ...s }));
    return {
      local: dump(window.localStorage),
      session: dump(window.sessionStorage),
      cookies: document.cookie,
    };
  });
  const tokenLike = /(access_token|refresh_token|id_token|eyJ[A-Za-z0-9_-]+\.)/;
  expect(storage.local).not.toMatch(tokenLike);
  expect(storage.session).not.toMatch(tokenLike);
  // __Host-impilo_session is HttpOnly, so it must never be visible to document.cookie.
  expect(storage.cookies).not.toContain("__Host-impilo_session");
  expect(storage.cookies).not.toMatch(tokenLike);
});
