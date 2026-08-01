import { describe, expect, it } from "vitest";
import {
  authUserFromWebSession,
  isConstrainedRecoverySession,
  type WebSessionResponse,
} from "@/lib/auth/web-session";

function session(overrides: Partial<WebSessionResponse["data"]> = {}): WebSessionResponse["data"] {
  return {
    authenticated: true,
    user: { id: "user-1", roles: ["CLINICIAN"] },
    acr: "urn:impilo:aal2",
    amr: ["pwd"],
    ...overrides,
  };
}

describe("web session recovery classification", () => {
  it("flags a constrained recovery session from the recovery marker", () => {
    expect(isConstrainedRecoverySession(session({ recovery: true }))).toBe(true);
  });

  it("treats an ordinary session as non-recovery", () => {
    expect(isConstrainedRecoverySession(session())).toBe(false);
    expect(isConstrainedRecoverySession(session({ recovery: false }))).toBe(false);
  });

  it("does not infer recovery from a high numeric acr alone", () => {
    // A recovery session may still carry acr=aal2 from Keycloak; only the explicit
    // recovery flag decides. This prevents the shell from silently treating a
    // constrained recovery session as ordinary workforce AAL2.
    expect(isConstrainedRecoverySession(session({ acr: "urn:impilo:aal2", recovery: true }))).toBe(true);
  });

  it("still builds an auth user for a recovery session (for the enrollment surface)", () => {
    const user = authUserFromWebSession(session({ recovery: true }));
    expect(user.id).toBe("user-1");
    expect(user.roles).toEqual(["CLINICIAN"]);
  });
});
