import { describe, it, expect } from "vitest";
import { KeycloakClient, generateCodeVerifier, generateCodeChallenge, AuthError } from "../src/keycloakClient";
import { resolveActorId } from "../src/authStore";

describe("KeycloakClient", () => {
  const config = {
    realm: "impilo",
    clientId: "mobile-app",
    baseUrl: "http://localhost:8080",
    redirectUri: "impilo://callback",
  };

  it("builds authorization URL with PKCE parameters", () => {
    const kc = new KeycloakClient(config);
    const url = kc.buildAuthorizationUrl("challenge-abc", "state-123", "nonce-456");
    expect(url).toContain("response_type=code");
    expect(url).toContain("client_id=mobile-app");
    expect(url).toContain("code_challenge=challenge-abc");
    expect(url).toContain("code_challenge_method=S256");
    expect(url).toContain("state=state-123");
    expect(url).toContain("nonce=nonce-456");
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

describe("resolveActorId — CPID claim contract", () => {
  const base = {
    sub: "keycloak-uuid-001",
    preferred_username: "citizen.moyo",
    realm_access: { roles: ["CITIZEN"] as string[] },
  };

  it("uses CPID as actorId for a citizen when the claim is present", () => {
    const userInfo = { ...base, cpid: "CPID-ZW-00001" };
    expect(resolveActorId(userInfo)).toBe("CPID-ZW-00001");
  });

  it("falls back to sub when citizen has no CPID claim", () => {
    expect(resolveActorId(base)).toBe("keycloak-uuid-001");
  });

  it("uses sub for a CLINICIAN even if cpid is present", () => {
    const userInfo = {
      ...base,
      cpid: "CPID-ZW-00001",
      realm_access: { roles: ["CLINICIAN"] },
    };
    expect(resolveActorId(userInfo)).toBe("keycloak-uuid-001");
  });

  it("uses sub for an OPERATOR even if cpid is present", () => {
    const userInfo = {
      ...base,
      cpid: "CPID-ZW-00001",
      realm_access: { roles: ["SYSTEM_ADMIN"] },
    };
    expect(resolveActorId(userInfo)).toBe("keycloak-uuid-001");
  });
});
