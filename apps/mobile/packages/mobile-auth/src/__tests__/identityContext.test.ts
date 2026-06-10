import { describe, expect, it } from "vitest";
import { resolveMobileIdentityContext } from "./identityContext";

describe("mobile identityContext parity", () => {
  it("citizen without professional access", () => {
    const ctx = resolveMobileIdentityContext({ user: { id: "H1" } });
    expect(ctx.isCitizenOnly).toBe(true);
    expect(ctx.hasWorkAccess).toBe(false);
  });

  it("provider without assignment gets professional not work", () => {
    const ctx = resolveMobileIdentityContext({
      user: {
        id: "H2",
        providerId: "P2",
        providerStatus: "active",
        loginMethod: "provider_id",
      },
      workAssignments: [],
    });
    expect(ctx.hasProfessionalAccess).toBe(true);
    expect(ctx.hasWorkAccess).toBe(false);
    expect(ctx.defaultLandingPath).toBe("/professional");
  });

  it("active assignment unlocks work", () => {
    const ctx = resolveMobileIdentityContext({
      user: { id: "H3", providerId: "P3", providerStatus: "active" },
      workAssignments: [{ assignmentId: "A1", assignmentStatus: "active" }],
    });
    expect(ctx.hasWorkAccess).toBe(true);
  });
});
