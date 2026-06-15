import { describe, expect, it } from "vitest";
import {
  buildContextGuardRedirect,
  buildPostLoginResolvingPath,
  isSafeReturnTo,
  resolvePostFacilitySelectionPath,
  resolvePostLoginDestination,
} from "@/lib/resolve-post-login-destination";

describe("resolvePostLoginDestination", () => {
  it("routes provider with active assignment to work context", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-provider-1",
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      linkedIds: { providerStatus: "ACTIVE", licenceValid: true },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "PRV-001",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "F1",
        },
      ],
      hasFacility: true,
    });
    expect(result.href).toBe("/provider-workspace");
    expect(result.operationalMode).toBe("facility_work");
  });

  it("routes verified provider without assignment to professional", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-provider-1",
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      linkedIds: { providerStatus: "ACTIVE", licenceValid: true },
      workAssignments: [],
    });
    expect(result.href).toBe("/professional");
    expect(result.operationalMode).toBe("my_professional");
  });

  it("routes citizen to home with my_life mode", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-citizen-1",
        actorType: "CITIZEN",
        roles: ["CITIZEN"],
        providerActivated: false,
      },
    });
    expect(result).toEqual({ href: "/home", operationalMode: "my_life" });
  });

  it("sends linked-but-inactive provider to activation", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-citizen-1",
        actorType: "CITIZEN",
        roles: ["CLINICIAN"],
        providerActivated: false,
      },
      linkedProviderId: "PRV-2024-00001",
    });
    expect(result.href).toBe("/provider/activate?returnTo=%2Fprovider-workspace");
  });

  it("does not replay blocked /work returnTo after no_work resolution", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-admin-1",
        actorType: "SYSTEM",
        roles: ["SYSTEM_ADMIN"],
        providerActivated: false,
      },
      returnTo: "/work/administration-governance",
      resolutionReason: "no_work",
    });
    expect(result.href).toBe("/home");
    expect(result.operationalMode).toBe("my_life");
  });

  it("honors safe returnTo and applies facility guard when needed", () => {
    const result = resolvePostLoginDestination({
      user: {
        id: "user-provider-1",
        actorType: "PROVIDER",
        roles: ["CLINICIAN"],
        providerActivated: true,
        providerId: "PRV-001",
      },
      linkedIds: { providerStatus: "ACTIVE" },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "PRV-001",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
        },
      ],
      hasFacility: false,
      returnTo: "/clinical",
    });
    expect(result.href).toBe("/facility?returnTo=%2Fclinical");
  });

  it("rejects unsafe returnTo values", () => {
    expect(isSafeReturnTo("//evil.example")).toBe(false);
    expect(isSafeReturnTo("/auth/login")).toBe(false);
    expect(isSafeReturnTo("/home")).toBe(true);
  });

  it("buildPostLoginResolvingPath preserves returnTo", () => {
    expect(buildPostLoginResolvingPath("/queue")).toBe(
      "/auth/resolving?returnTo=%2Fqueue",
    );
    expect(buildPostLoginResolvingPath()).toBe("/auth/resolving");
  });

  it("buildContextGuardRedirect preserves attempted path as returnTo", () => {
    expect(buildContextGuardRedirect("/facility", "/clinical")).toBe(
      "/facility?returnTo=%2Fclinical",
    );
    expect(buildContextGuardRedirect("/facility", "/facility")).toBe("/facility");
  });

  it("resolvePostFacilitySelectionPath returns clinical hub when only facility guard applies", () => {
    expect(resolvePostFacilitySelectionPath("/clinical")).toBe("/clinical");
    expect(resolvePostFacilitySelectionPath(null)).toBe("/workspace");
    expect(resolvePostFacilitySelectionPath("/queue/triage")).toBe("/queue/triage");
    expect(resolvePostFacilitySelectionPath("/scheduling")).toBe(
      "/workspace?returnTo=%2Fscheduling",
    );
  });
});
