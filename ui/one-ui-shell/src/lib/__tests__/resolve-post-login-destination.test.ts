import { describe, expect, it } from "vitest";
import {
  buildPostLoginResolvingPath,
  isSafeReturnTo,
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
});
