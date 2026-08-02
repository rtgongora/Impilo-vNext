import { beforeEach, describe, expect, it, vi } from "vitest";

const jwtVerify = vi.fn();
const createRemoteJWKSet = vi.fn(() => ({}));

vi.mock("jose", () => ({
  jwtVerify: (...args: unknown[]) => jwtVerify(...args),
  createRemoteJWKSet: (...args: unknown[]) => createRemoteJWKSet(...args),
}));

describe("KeycloakClient.validateIdToken", () => {
  beforeEach(() => {
    jwtVerify.mockReset();
    createRemoteJWKSet.mockClear();
  });

  it("verifies issuer, audience and required claims including nonce", async () => {
    const { KeycloakClient } = await import("../src/keycloakClient");
    const kc = new KeycloakClient({
      realm: "impilo",
      clientId: "impilo-mobile-citizen",
      baseUrl: "https://impilo.mohcc.gov.zw",
      redirectUri: "impilo-citizen://auth/callback",
      issuer: "https://impilo.mohcc.gov.zw/realms/impilo",
    });
    jwtVerify.mockResolvedValue({ payload: { sub: "u1", nonce: "nonce-ok", iat: 1, exp: 2 } });

    const payload = await kc.validateIdToken("id.token.value", "nonce-ok");
    expect(payload.sub).toBe("u1");
    expect(jwtVerify).toHaveBeenCalledWith(
      "id.token.value",
      expect.anything(),
      expect.objectContaining({
        issuer: "https://impilo.mohcc.gov.zw/realms/impilo",
        audience: "impilo-mobile-citizen",
        requiredClaims: ["sub", "iat", "exp", "nonce"],
      }),
    );
  });

  it("rejects a nonce mismatch even when jose signature verification succeeds", async () => {
    const { KeycloakClient, AuthError } = await import("../src/keycloakClient");
    const kc = new KeycloakClient({
      realm: "impilo",
      clientId: "impilo-mobile-citizen",
      baseUrl: "https://impilo.mohcc.gov.zw",
      redirectUri: "impilo-citizen://auth/callback",
    });
    jwtVerify.mockResolvedValue({ payload: { sub: "u1", nonce: "other-nonce", iat: 1, exp: 2 } });

    await expect(kc.validateIdToken("id.token.value", "expected-nonce"))
      .rejects.toMatchObject<InstanceType<typeof AuthError>>({ code: "NONCE_MISMATCH" });
  });

  it("surfaces issuer/audience failures from jose as thrown errors (fail closed)", async () => {
    const { KeycloakClient } = await import("../src/keycloakClient");
    const kc = new KeycloakClient({
      realm: "impilo",
      clientId: "impilo-mobile-citizen",
      baseUrl: "https://impilo.mohcc.gov.zw",
      redirectUri: "impilo-citizen://auth/callback",
    });
    jwtVerify.mockRejectedValue(new Error('unexpected "iss" claim value'));

    await expect(kc.validateIdToken("id.token.value", "nonce-ok"))
      .rejects.toThrow(/iss|claim/i);
  });
});
