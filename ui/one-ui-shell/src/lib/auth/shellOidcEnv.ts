/**
 * OIDC (Keycloak) settings for the one-ui-shell. Server routes read these;
 * NEXT_PUBLIC_ONE_UI_SHELL_URL is also safe on the client for logout redirects.
 */
export function getShellPublicUrl(): string {
  return (
    process.env.NEXT_PUBLIC_ONE_UI_SHELL_URL?.replace(/\/$/u, "") ||
    "http://localhost:3000"
  );
}

export function getKeycloakIssuerUrl(): string {
  const base = (
    process.env.NEXT_PUBLIC_KEYCLOAK_URL ||
    process.env.KEYCLOAK_URL ||
    "http://localhost:8080"
  ).replace(/\/$/u, "");
  const realm =
    process.env.NEXT_PUBLIC_KEYCLOAK_REALM ||
    process.env.KEYCLOAK_REALM ||
    "impilo";
  return `${base}/realms/${realm}`;
}

export function getKeycloakClientId(): string {
  return (
    process.env.ONE_UI_SHELL_KEYCLOAK_CLIENT_ID ||
    process.env.NEXT_PUBLIC_ONE_UI_SHELL_KEYCLOAK_CLIENT_ID ||
    "one-ui-shell"
  );
}

export const OIDC_COOKIE_VERIFIER = "impilo_oidc_code_verifier";
export const OIDC_COOKIE_STATE = "impilo_oidc_state";

/**
 * Default scopes for the authorize request. Matches typical `impilo` realm
 * discovery (`openid`, `profile`, `email`, `impilo-tenant`). Do not include
 * `roles` here unless your Keycloak realm lists it in `scopes_supported` —
 * many live realms reject `invalid_scope` for unknown scope names.
 *
 * Override with `ONE_UI_SHELL_OIDC_SCOPE` (space-separated, server-only).
 */
const OIDC_SCOPE_DEFAULT = "openid profile email impilo-tenant";

export function getOidcScope(): string {
  const raw = process.env.ONE_UI_SHELL_OIDC_SCOPE?.trim();
  if (raw) return raw;
  return OIDC_SCOPE_DEFAULT;
}
