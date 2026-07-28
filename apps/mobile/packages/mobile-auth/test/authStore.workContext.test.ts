import { describe, it, expect, beforeEach } from "vitest";
import { authStore } from "../src/authStore";
import type { SessionContext } from "@impilo/mobile-trust";

const BASE_SESSION: SessionContext = {
  tenantId: "moh-zw",
  podId: "national-spine",
  actorId: "user-123",
  actorType: "PROVIDER",
  accessToken: "tok-abc",
  expiresAt: Date.now() + 3600_000,
  purposeOfUse: "TREATMENT",
  facilityId: "fac-1",
  workspaceId: "ws-1",
};

describe("authStore — work context (Phase G1)", () => {
  beforeEach(() => {
    authStore.setState({ session: { ...BASE_SESSION } });
  });

  it("setWorkContext merges the minted token onto the existing session", () => {
    authStore.getState().setWorkContext({
      token: "wct-jwt",
      jti: "jti-1",
      expiresAt: 1234,
      contextId: "ctx-1",
      workMode: "CLINICAL_CARE",
    });

    const session = authStore.getState().session;
    expect(session?.workContextToken).toBe("wct-jwt");
    expect(session?.workContextJti).toBe("jti-1");
    expect(session?.workContextExpiresAt).toBe(1234);
    expect(session?.workContextId).toBe("ctx-1");
    expect(session?.workMode).toBe("CLINICAL_CARE");
    // Unrelated session fields untouched.
    expect(session?.facilityId).toBe("fac-1");
    expect(session?.accessToken).toBe("tok-abc");
  });

  it("setWorkContext is a no-op when there is no active session", () => {
    authStore.setState({ session: null });
    authStore.getState().setWorkContext({
      token: "wct-jwt",
      jti: "jti-1",
      expiresAt: 1234,
      contextId: "ctx-1",
      workMode: "CLINICAL_CARE",
    });
    expect(authStore.getState().session).toBeNull();
  });

  it("clearWorkContext removes the minted fields but keeps the rest of the session", () => {
    authStore.getState().setWorkContext({
      token: "wct-jwt",
      jti: "jti-1",
      expiresAt: 1234,
      contextId: "ctx-1",
      workMode: "CLINICAL_CARE",
    });
    authStore.getState().clearWorkContext();

    const session = authStore.getState().session;
    expect(session?.workContextToken).toBeUndefined();
    expect(session?.workContextJti).toBeUndefined();
    expect(session?.workContextExpiresAt).toBeUndefined();
    expect(session?.workContextId).toBeUndefined();
    expect(session?.workMode).toBeUndefined();
    expect(session?.facilityId).toBe("fac-1");
  });
});
