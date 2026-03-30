/**
 * OIDC Authorization Code + PKCE flow utilities.
 *
 * Flow:
 *   1. generatePkce()       — create code_verifier + code_challenge
 *   2. buildAuthUrl()       — redirect to Keycloak authorize endpoint
 *   3. exchangeCode()       — exchange code for tokens at Keycloak token endpoint
 *   4. parseUserFromToken() — extract user info from the access_token JWT claims
 *
 * Storage keys (sessionStorage):
 *   oidc:pkce_verifier  — PKCE code verifier (cleared after exchange)
 *   oidc:state          — random state param (CSRF protection)
 */

const KEYCLOAK_URL =
  process.env.NEXT_PUBLIC_KEYCLOAK_URL || "http://localhost:8080";
const KEYCLOAK_REALM =
  process.env.NEXT_PUBLIC_KEYCLOAK_REALM || "impilo";
const KEYCLOAK_CLIENT_ID =
  process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || "experience-ui";

export const OIDC_ISSUER = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`;
const AUTHORIZE_URL = `${OIDC_ISSUER}/protocol/openid-connect/auth`;
const TOKEN_URL = `${OIDC_ISSUER}/protocol/openid-connect/token`;
const LOGOUT_URL = `${OIDC_ISSUER}/protocol/openid-connect/logout`;

async function sha256(plain: string): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  const data = encoder.encode(plain);
  return crypto.subtle.digest("SHA-256", data);
}

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let str = "";
  bytes.forEach((b) => (str += String.fromCharCode(b)));
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}

export async function generatePkce(): Promise<{
  verifier: string;
  challenge: string;
}> {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  const verifier = base64UrlEncode(array.buffer);
  const challengeBuffer = await sha256(verifier);
  const challenge = base64UrlEncode(challengeBuffer);
  return { verifier, challenge };
}

export async function buildAuthUrl(
  redirectUri: string,
  acrValues?: string
): Promise<string> {
  const { verifier, challenge } = await generatePkce();
  const state = base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)).buffer);

  sessionStorage.setItem("oidc:pkce_verifier", verifier);
  sessionStorage.setItem("oidc:state", state);

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    response_type: "code",
    scope: "openid profile email",
    redirect_uri: redirectUri,
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
    ...(acrValues ? { acr_values: acrValues } : {}),
  });

  return `${AUTHORIZE_URL}?${params.toString()}`;
}

export interface OidcTokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
  token_type: string;
}

export async function exchangeCode(
  code: string,
  state: string,
  redirectUri: string
): Promise<OidcTokenResponse> {
  const storedState = sessionStorage.getItem("oidc:state");
  if (storedState !== state) {
    throw new Error("OIDC state mismatch — possible CSRF attack");
  }

  const verifier = sessionStorage.getItem("oidc:pkce_verifier");
  if (!verifier) {
    throw new Error("PKCE verifier not found in session");
  }

  sessionStorage.removeItem("oidc:pkce_verifier");
  sessionStorage.removeItem("oidc:state");

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: KEYCLOAK_CLIENT_ID,
    code,
    redirect_uri: redirectUri,
    code_verifier: verifier,
  });

  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    const err = await response.text();
    throw new Error(`Token exchange failed: ${err}`);
  }

  return response.json();
}

export function parseUserFromToken(accessToken: string): {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";
} {
  const [, payloadB64] = accessToken.split(".");
  const payload = JSON.parse(atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/")));

  const realmRoles: string[] = payload?.realm_access?.roles ?? [];
  const actorTypeClaim: string = payload?.actor_type ?? "";

  let actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM" = "PROVIDER";
  if (actorTypeClaim) {
    actorType = actorTypeClaim.toUpperCase() as typeof actorType;
  } else if (realmRoles.includes("admin") || realmRoles.includes("system-admin")) {
    actorType = "OPERATOR";
  } else if (realmRoles.includes("citizen") || realmRoles.includes("patient")) {
    actorType = "CITIZEN";
  } else if (realmRoles.includes("service-account")) {
    actorType = "SYSTEM";
  }

  return {
    id: payload.sub ?? "",
    email: payload.email ?? payload.preferred_username ?? "",
    displayName:
      payload.name ?? payload.preferred_username ?? payload.email ?? "",
    roles: realmRoles,
    actorType,
  };
}

export function buildLogoutUrl(redirectUri: string): string {
  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    post_logout_redirect_uri: redirectUri,
  });
  return `${LOGOUT_URL}?${params.toString()}`;
}
