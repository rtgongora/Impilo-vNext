/**
 * Keycloak PKCE Client — OAuth 2.0 Authorization Code Flow with PKCE
 *
 * Implements the full Keycloak OIDC flow for mobile:
 *   1. Generate code_verifier + code_challenge (S256)
 *   2. Open authorization URL in system browser / in-app browser
 *   3. Handle redirect callback with authorization code
 *   4. Exchange code for access + refresh tokens
 *   5. Refresh tokens before expiry
 *   6. Revoke tokens on logout
 *
 * No implicit grant. No client secret on mobile (public client).
 */

export interface KeycloakConfig {
  realm: string;
  clientId: string;
  baseUrl: string;
  redirectUri: string;
  scopes?: string[];
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  refresh_expires_in: number;
  token_type: string;
  scope: string;
  id_token?: string;
}

export interface UserInfo {
  sub: string;
  preferred_username: string;
  email?: string;
  given_name?: string;
  family_name?: string;
  realm_access?: { roles: string[] };
  resource_access?: Record<string, { roles: string[] }>;
  /** CPID claim — populated by the cpid Keycloak protocol mapper for citizen users. */
  cpid?: string;
}

/**
 * Generates a cryptographically random code verifier for PKCE (RFC 7636).
 * 43–128 characters from the unreserved character set [A-Z, a-z, 0-9, -, ., _, ~].
 */
export function generateCodeVerifier(): string {
  const bytes = new Uint8Array(32);
  globalThis.crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

/**
 * Derives the code challenge from the verifier using SHA-256.
 */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await globalThis.crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export class KeycloakClient {
  private readonly config: KeycloakConfig;
  private readonly tokenEndpoint: string;
  private readonly authEndpoint: string;
  private readonly logoutEndpoint: string;
  private readonly userInfoEndpoint: string;
  private readonly revocationEndpoint: string;

  constructor(config: KeycloakConfig) {
    this.config = config;
    const base = `${config.baseUrl}/realms/${config.realm}/protocol/openid-connect`;
    this.tokenEndpoint = `${base}/token`;
    this.authEndpoint = `${base}/auth`;
    this.logoutEndpoint = `${base}/logout`;
    this.userInfoEndpoint = `${base}/userinfo`;
    this.revocationEndpoint = `${base}/revoke`;
  }

  /**
   * Builds the authorization URL for the PKCE flow.
   * The app should open this in a system browser / in-app browser tab.
   */
  buildAuthorizationUrl(codeChallenge: string, state: string): string {
    const scopes = this.config.scopes?.join(" ") ?? "openid profile email";
    const params = new URLSearchParams({
      response_type: "code",
      client_id: this.config.clientId,
      redirect_uri: this.config.redirectUri,
      scope: scopes,
      code_challenge: codeChallenge,
      code_challenge_method: "S256",
      state,
    });
    return `${this.authEndpoint}?${params.toString()}`;
  }

  /**
   * Exchanges the authorization code for tokens.
   */
  async exchangeCode(code: string, codeVerifier: string): Promise<TokenResponse> {
    const body = new URLSearchParams({
      grant_type: "authorization_code",
      client_id: this.config.clientId,
      code,
      redirect_uri: this.config.redirectUri,
      code_verifier: codeVerifier,
    });

    const response = await fetch(this.tokenEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new AuthError("TOKEN_EXCHANGE_FAILED", `Token exchange failed: ${error}`);
    }

    return response.json();
  }

  /**
   * Refreshes the access token using the refresh token.
   */
  async refreshToken(refreshToken: string): Promise<TokenResponse> {
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: this.config.clientId,
      refresh_token: refreshToken,
    });

    const response = await fetch(this.tokenEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new AuthError("TOKEN_REFRESH_FAILED", `Token refresh failed: ${error}`);
    }

    return response.json();
  }

  /**
   * Fetches the authenticated user's profile from the UserInfo endpoint.
   */
  async getUserInfo(accessToken: string): Promise<UserInfo> {
    const response = await fetch(this.userInfoEndpoint, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (!response.ok) {
      throw new AuthError("USERINFO_FAILED", "Failed to fetch user info");
    }

    return response.json();
  }

  /**
   * Revokes a token (access or refresh) at the Keycloak revocation endpoint.
   */
  async revokeToken(token: string, tokenTypeHint: "access_token" | "refresh_token"): Promise<void> {
    const body = new URLSearchParams({
      client_id: this.config.clientId,
      token,
      token_type_hint: tokenTypeHint,
    });

    await fetch(this.revocationEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });
  }

  /**
   * Builds the logout URL for RP-initiated logout.
   */
  buildLogoutUrl(idTokenHint?: string): string {
    const params = new URLSearchParams({
      client_id: this.config.clientId,
      post_logout_redirect_uri: this.config.redirectUri,
    });
    if (idTokenHint) {
      params.set("id_token_hint", idTokenHint);
    }
    return `${this.logoutEndpoint}?${params.toString()}`;
  }
}

export class AuthError extends Error {
  public readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "AuthError";
    this.code = code;
  }
}
