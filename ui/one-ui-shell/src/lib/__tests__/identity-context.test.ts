import { describe, expect, it } from "vitest";
import type { AuthUser } from "@/hooks/useAuthStore";
import {
  resolveIdentityContext,
  isRouteBlockedForCitizen,
} from "@/lib/identity-context";

const citizen: AuthUser = {
  id: "H1",
  email: "c@example.com",
  displayName: "Citizen",
  roles: ["CITIZEN"],
  actorType: "CITIZEN",
  assuranceLevel: "VERIFIED",
  providerActivated: false,
};

const provider: AuthUser = {
  id: "H2",
  email: "p@example.com",
  displayName: "Provider",
  roles: ["CLINICIAN"],
  actorType: "PROVIDER",
  assuranceLevel: "VERIFIED",
  providerActivated: true,
  providerId: "P2",
};

describe("identity-context three-tab doctrine", () => {
  it("citizen sees life only", () => {
    const ctx = resolveIdentityContext({ user: citizen });
    expect(ctx.isCitizenOnly).toBe(true);
    expect(ctx.hasProfessionalAccess).toBe(false);
    expect(ctx.visibleNavZones).toEqual(["life"]);
  });

  it("verified provider without assignment gets professional not work", () => {
    const ctx = resolveIdentityContext({
      user: provider,
      linkedIds: { providerStatus: "ACTIVE", licenceValid: true },
      workAssignments: [],
    });
    expect(ctx.hasProfessionalAccess).toBe(true);
    expect(ctx.hasWorkAccess).toBe(false);
    expect(ctx.visibleNavZones).toEqual(["professional", "life"]);
    expect(ctx.defaultLandingPath).toBe("/professional");
  });

  it("active work assignment unlocks work tab", () => {
    const ctx = resolveIdentityContext({
      user: provider,
      linkedIds: { providerStatus: "ACTIVE", licenceValid: true },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "P2",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "F1",
        },
      ],
    });
    expect(ctx.hasWorkAccess).toBe(true);
    expect(ctx.visibleNavZones).toContain("work");
  });

  it("blocks citizens from work routes", () => {
    const ctx = resolveIdentityContext({ user: citizen });
    expect(isRouteBlockedForCitizen("/clinical", ctx)).toBe(true);
  });
});
