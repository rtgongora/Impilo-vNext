import { describe, it, expect } from "vitest";
import { KeycloakClient, generateCodeVerifier, generateCodeChallenge, AuthError } from "../src/keycloakClient";

describe("KeycloakClient", () => {
  const config = {
    realm: "impilo",
    clientId: "mobile-app",
    baseUrl: "http://localhost:8080",
    redirectUri: "impilo://callback",
  };

  it("builds authorization URL with PKCE parameters", () => {
    const kc = new KeycloakClient(config);
    const url = kc.buildAuthorizationUrl("challenge-abc", "state-123");
    expect(url).toContain("response_type=code");
    expect(url).toContain("client_id=mobile-app");
    expect(url).toContain("code_challenge=challenge-abc");
    expect(url).toContain("code_challenge_method=S256");
    expect(url).toContain("state=state-123");
    expect(url).toContain("redirect_uri=");
    expect(url).toContain("/realms/impilo/protocol/openid-connect/auth");
  });

  it("builds logout URL", () => {
    const kc = new KeycloakClient(config);
    const url = kc.buildLogoutUrl("id-token-hint");
    expect(url).toContain("/realms/impilo/protocol/openid-connect/logout");
    expect(url).toContain("client_id=mobile-app");
    expect(url).toContain("id_token_hint=id-token-hint");
  });
});

describe("PKCE", () => {
  it("generateCodeVerifier returns a URL-safe base64 string", () => {
    const verifier = generateCodeVerifier();
    expect(verifier.length).toBeGreaterThanOrEqual(32);
    expect(verifier).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it("generateCodeChallenge derives SHA-256 challenge", async () => {
    const verifier = generateCodeVerifier();
    const challenge = await generateCodeChallenge(verifier);
    expect(challenge).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(challenge).not.toBe(verifier);
  });

  it("same verifier produces same challenge", async () => {
    const verifier = generateCodeVerifier();
    const c1 = await generateCodeChallenge(verifier);
    const c2 = await generateCodeChallenge(verifier);
    expect(c1).toBe(c2);
  });
});

describe("AuthError", () => {
  it("has code and message", () => {
    const err = new AuthError("TEST_CODE", "test message");
    expect(err.code).toBe("TEST_CODE");
    expect(err.message).toBe("test message");
    expect(err.name).toBe("AuthError");
    expect(err).toBeInstanceOf(Error);
  });
});
