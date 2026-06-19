import { describe, expect, it } from "vitest";
import { resolveSessionExperienceContract } from "@/lib/trust";
import { canAccessAdministrationPath } from "@/lib/administration-governance/access";
import { MANAGEMENT_TILES } from "@/lib/administration-governance/tiles";
import {
  canAccessVashandiPath,
  hasVashandiEntry,
  requiredWorkspacesForPath,
  vashandiWorkspaceAllowed,
} from "@/lib/vashandi/access";
import {
  VASHANDI_BASE_PATH,
  isUpstreamUnavailable,
  isVashandiActionResponse,
  upstreamUnavailableResponse,
} from "@/lib/vashandi/api/client";
import { isVashandiEmptyList } from "@/hooks/useVashandi";

describe("Vashandi access gating", () => {
  it("denies Vashandi paths when no visibleVashandiWorkspaces", () => {
    const citizen = resolveSessionExperienceContract({
      authenticated: true,
      healthId: "H-CITIZEN",
    });
    expect(hasVashandiEntry(citizen)).toBe(false);
    expect(canAccessVashandiPath(citizen, "/work/vashandi")).toBe(false);
    expect(canAccessAdministrationPath(citizen, "/work/vashandi/workforce")).toBe(false);
  });

  it("allows dashboard when vashandi.dashboard is visible", () => {
    const manager = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-FAC-MGR",
      visibleVashandiWorkspaces: ["vashandi.dashboard", "vashandi.facility_staff", "vashandi.assignments"],
      workAssignments: [
        {
          assignmentId: "A-FAC",
          subjectId: "P-FAC-MGR",
          subjectType: "provider_worker",
          contextType: "facility_workforce",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
        },
      ],
    });
    expect(hasVashandiEntry(manager)).toBe(true);
    expect(vashandiWorkspaceAllowed(manager, "vashandi.dashboard")).toBe(true);
    expect(canAccessVashandiPath(manager, "/work/vashandi")).toBe(true);
    expect(canAccessVashandiPath(manager, "/work/vashandi/assignments")).toBe(true);
    expect(canAccessVashandiPath(manager, "/work/vashandi/access-review")).toBe(false);
  });

  it("maps paths to required workspaces", () => {
    expect(requiredWorkspacesForPath("/work/vashandi/rosters")).toContain("vashandi.rosters");
    expect(requiredWorkspacesForPath("/work/vashandi/analytics")).toContain("vashandi.analytics");
  });

  it("registers Vashandi hub tile with dashboard workspace", () => {
    const tile = MANAGEMENT_TILES.find((entry) => entry.id === "vashandi");
    expect(tile).toBeDefined();
    expect(tile!.requiredWorkspaces).toContain("vashandi.dashboard");
    expect(tile!.href).toBe("/work/vashandi");
  });
});

describe("Vashandi API client shape", () => {
  it("uses BFF base path /internal/v1/vashandi", () => {
    expect(VASHANDI_BASE_PATH).toBe("/internal/v1/vashandi");
  });

  it("recognises action response envelope", () => {
    const response = upstreamUnavailableResponse("Unavailable", "Service down");
    expect(isVashandiActionResponse(response)).toBe(true);
    expect(isUpstreamUnavailable(response)).toBe(true);
    expect(response.success).toBe(false);
    expect(response.integrationStatus).toBe("UPSTREAM_UNAVAILABLE");
  });

  it("does not claim success on upstream unavailable", () => {
    const response = upstreamUnavailableResponse("Unavailable", "Service down");
    expect(response.status).toBe("upstream_unavailable");
    expect(response.policyDecision?.allowed).toBe(false);
  });
});

describe("Vashandi empty states", () => {
  it("treats successful empty list as honest empty state", () => {
    expect(
      isVashandiEmptyList([], {
        success: true,
        status: "completed",
      }),
    ).toBe(true);
  });

  it("does not treat unavailable response as empty list", () => {
    expect(
      isVashandiEmptyList([], upstreamUnavailableResponse("Unavailable", "Service down")),
    ).toBe(false);
  });
});
