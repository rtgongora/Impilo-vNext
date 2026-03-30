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
 *
 * SHA-256 is implemented in pure JS to support non-secure HTTP contexts
 * (crypto.subtle is only available under HTTPS / localhost).
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

// ── Pure-JS SHA-256 (RFC 6234 / FIPS 180-4) ────────────────────────────────
// Works in non-secure HTTP contexts where crypto.subtle is unavailable.
const K = [
  0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
  0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
  0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
  0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
  0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
  0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
  0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
  0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2,
];

function rotr32(x: number, n: number) {
  return (x >>> n) | (x << (32 - n));
}

function sha256Bytes(data: Uint8Array): Uint8Array {
  const len = data.length;
  const bitLen = len * 8;
  const padLen = ((len + 9 + 63) & ~63) - len;
  const padded = new Uint8Array(len + padLen);
  padded.set(data);
  padded[len] = 0x80;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 4, bitLen >>> 0, false);
  dv.setUint32(padded.length - 8, Math.floor(bitLen / 2 ** 32), false);

  let h0=0x6a09e667,h1=0xbb67ae85,h2=0x3c6ef372,h3=0xa54ff53a;
  let h4=0x510e527f,h5=0x9b05688c,h6=0x1f83d9ab,h7=0x5be0cd19;

  const w = new Int32Array(64);
  for (let i = 0; i < padded.length; i += 64) {
    for (let j = 0; j < 16; j++) w[j] = dv.getInt32(i + j * 4, false);
    for (let j = 16; j < 64; j++) {
      const s0 = rotr32(w[j-15],7)^rotr32(w[j-15],18)^(w[j-15]>>>3);
      const s1 = rotr32(w[j-2],17)^rotr32(w[j-2],19)^(w[j-2]>>>10);
      w[j] = (w[j-16]+s0+w[j-7]+s1)|0;
    }
    let [a,b,c,d,e,f,g,h] = [h0,h1,h2,h3,h4,h5,h6,h7];
    for (let j = 0; j < 64; j++) {
      const S1 = rotr32(e,6)^rotr32(e,11)^rotr32(e,25);
      const ch = (e&f)^(~e&g);
      const temp1 = (h+S1+ch+K[j]+w[j])|0;
      const S0 = rotr32(a,2)^rotr32(a,13)^rotr32(a,22);
      const maj = (a&b)^(a&c)^(b&c);
      const temp2 = (S0+maj)|0;
      [h,g,f,e,d,c,b,a] = [g,f,e,(d+temp1)|0,c,b,a,(temp1+temp2)|0];
    }
    h0=(h0+a)|0;h1=(h1+b)|0;h2=(h2+c)|0;h3=(h3+d)|0;
    h4=(h4+e)|0;h5=(h5+f)|0;h6=(h6+g)|0;h7=(h7+h)|0;
  }

  const out = new Uint8Array(32);
  const ov = new DataView(out.buffer);
  [h0,h1,h2,h3,h4,h5,h6,h7].forEach((v,i) => ov.setInt32(i*4,v,false));
  return out;
}

async function sha256(plain: string): Promise<ArrayBuffer> {
  const data = new TextEncoder().encode(plain);
  if (typeof crypto !== "undefined" && crypto.subtle) {
    return crypto.subtle.digest("SHA-256", data);
  }
  return sha256Bytes(data).buffer;
}
// ── End SHA-256 ────────────────────────────────────────────────────────────

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
