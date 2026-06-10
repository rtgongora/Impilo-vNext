import { describe, expect, it } from "vitest";
import { resolveSessionExperienceContract } from "@/lib/trust";
import {
  canAccessAdministrationPath,
  hasAdministrationGovernanceEntry,
  tileEnabled,
} from "../access";
import { MANAGEMENT_TILES, QUICK_ACTION_TILES } from "../tiles";
import { ONBOARD_ACTOR_OPTIONS, ONBOARD_FLOW_STEPS } from "../onboard-options";
import { ADMINISTRATION_GOVERNANCE_ROUTE_COUNT } from "../route-registry";

describe("Administration & Governance entry", () => {
  it("citizen without management workspaces does not see Administration & Governance", () => {
    const citizen = resolveSessionExperienceContract({
      authenticated: true,
      healthId: "H-CITIZEN",
    });
    expect(citizen.tabs.personal.visible).toBe(true);
    expect(hasAdministrationGovernanceEntry(citizen)).toBe(false);
  });

  it("HSC officer sees HSC Workforce Governance tile only within HSC scope", () => {
    const hsc = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-HSC",
      organisation: {
        organisationId: "org-hsc-zw",
        organisationType: "health_service_commission",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "hsc_workforce_governance_officer",
      },
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [
        {
          assignmentId: "A-HSC",
          subjectId: "P-HSC",
          subjectType: "provider_worker",
          contextType: "public_sector_workforce_governance",
          assignmentType: "regulatory_assignment",
          assignmentStatus: "active",
          organisationId: "org-hsc-zw",
          workspaceId: "hsc_workspace",
        },
      ],
    });
    expect(hasAdministrationGovernanceEntry(hsc)).toBe(true);
    const hscTile = MANAGEMENT_TILES.find((t) => t.id === "hsc");
    expect(hscTile).toBeDefined();
    expect(tileEnabled(hsc, hscTile!.requiredWorkspaces)).toBe(true);
    expect(tileEnabled(hsc, MANAGEMENT_TILES.find((t) => t.id === "system_admin")!.requiredWorkspaces)).toBe(false);
  });

  it("municipal admin sees municipal management only", () => {
    const municipal = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-MUNI",
      organisation: {
        organisationId: "org-harare-city-health",
        organisationType: "city_health_department",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "organisation_administrator",
      },
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [
        {
          assignmentId: "A-MUNI",
          subjectId: "P-MUNI",
          subjectType: "provider_worker",
          contextType: "public_health",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          organisationId: "org-harare-city-health",
        },
      ],
    });
    expect(tileEnabled(municipal, MANAGEMENT_TILES.find((t) => t.id === "municipal")!.requiredWorkspaces)).toBe(true);
    expect(tileEnabled(municipal, MANAGEMENT_TILES.find((t) => t.id === "hsc")!.requiredWorkspaces)).toBe(false);
    expect(tileEnabled(municipal, MANAGEMENT_TILES.find((t) => t.id === "system_admin")!.requiredWorkspaces)).toBe(false);
  });

  it("facility admin sees Facility Staff & Access for assigned facility paths", () => {
    const facilityAdmin = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-FAC-ADMIN",
      professionalTruth: { providerWorkerStatus: "active" },
      hasSelectedFacility: true,
      facilityModeActive: true,
      publicSectorEmployment: {
        providerWorkerId: "P-FAC-ADMIN",
        employmentAuthority: "health_service_commission",
        employmentStatus: "active",
        employerOrganisationType: "public_facility",
        currentPostingFacility: "FAC-001",
        verificationStatus: "verified",
      },
      workAssignments: [
        {
          assignmentId: "A-FAC",
          subjectId: "P-FAC-ADMIN",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC-001",
          roleTemplate: "facility_administrator",
          employerOrganisationType: "public_facility",
        },
      ],
      organisation: {
        organisationId: "org-public-fac-1",
        organisationType: "public_facility",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "facility_administrator",
      },
    });
    expect(canAccessAdministrationPath(facilityAdmin, "/work/facility/FAC-001/staff-access")).toBe(true);
    expect(tileEnabled(facilityAdmin, MANAGEMENT_TILES.find((t) => t.id === "facility_staff")!.requiredWorkspaces)).toBe(true);
  });
});

describe("Onboard New Actor wizard", () => {
  it("lists thirteen policy-filtered actor flows with steps", () => {
    expect(ONBOARD_ACTOR_OPTIONS).toHaveLength(13);
    for (const option of ONBOARD_ACTOR_OPTIONS) {
      expect(ONBOARD_FLOW_STEPS[option.id]?.steps.length).toBeGreaterThan(0);
      expect(option.href).toContain(`/work/administration-governance/onboard/${option.id}`);
    }
  });

  it("provider onboarding outcome is Professional not Work", () => {
    const provider = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === "provider-worker");
    expect(provider?.outcome.toLowerCase()).toContain("professional");
    expect(provider?.outcome.toLowerCase()).not.toContain("work appears");
  });

  it("HSC onboarding creates workforce governance Work not clinical Work", () => {
    const hsc = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === "hsc-user");
    expect(hsc?.outcome.toLowerCase()).toContain("hsc");
    expect(hsc?.outcome.toLowerCase()).toContain("not clinical");
  });

  it("council onboarding creates regulatory Work", () => {
    const regulator = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === "regulator-user");
    expect(regulator?.outcome.toLowerCase()).toContain("regulatory");
  });

  it("onboarding options filter by required workspaces", () => {
    const citizenContract = resolveSessionExperienceContract({ authenticated: true, healthId: "H1" });
    const visibleForCitizen = ONBOARD_ACTOR_OPTIONS.filter((o) => tileEnabled(citizenContract, o.requiredWorkspaces));
    expect(visibleForCitizen).toHaveLength(0);

    const councilContract = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-REG",
      organisation: {
        organisationId: "org-mdpc",
        organisationType: "statutory_health_council",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "registrar",
      },
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [
        {
          assignmentId: "A-COUNCIL",
          subjectId: "P-REG",
          subjectType: "provider_worker",
          contextType: "regulatory",
          assignmentType: "regulatory_assignment",
          assignmentStatus: "active",
          organisationId: "org-mdpc",
        },
      ],
    });
    const regulatorOption = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === "regulator-user");
    expect(tileEnabled(councilContract, regulatorOption!.requiredWorkspaces)).toBe(true);
    const systemAdminOption = ONBOARD_ACTOR_OPTIONS.find((o) => o.id === "system-admin");
    expect(tileEnabled(councilContract, systemAdminOption!.requiredWorkspaces)).toBe(false);
  });
});

describe("Route guards", () => {
  it("blocks deep links when contract lacks authority", () => {
    const citizen = resolveSessionExperienceContract({ authenticated: true, healthId: "H2" });
    expect(canAccessAdministrationPath(citizen, "/work/administration-governance")).toBe(false);
    expect(canAccessAdministrationPath(citizen, "/work/hsc/users")).toBe(false);
    expect(canAccessAdministrationPath(citizen, "/work/system-admin/users")).toBe(false);
  });

  it("allows governance landing when management workspaces present", () => {
    const municipal = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-M",
      organisation: {
        organisationId: "org-city",
        organisationType: "city_health_department",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
      },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "P-M",
          subjectType: "provider_worker",
          contextType: "public_health",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          organisationId: "org-city",
        },
      ],
    });
    expect(canAccessAdministrationPath(municipal, "/work/administration-governance/onboard")).toBe(true);
  });
});

describe("Management tiles", () => {
  it("quick actions require governance entry not global admin role", () => {
    expect(QUICK_ACTION_TILES.every((t) => t.requiredWorkspaces.includes("*"))).toBe(true);
    const hsc = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-HSC2",
      organisation: {
        organisationId: "org-hsc-zw",
        organisationType: "health_service_commission",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
      },
      workAssignments: [
        {
          assignmentId: "A-HSC2",
          subjectId: "P-HSC2",
          subjectType: "provider_worker",
          contextType: "public_sector_workforce_governance",
          assignmentType: "regulatory_assignment",
          assignmentStatus: "active",
          organisationId: "org-hsc-zw",
        },
      ],
    });
    expect(tileEnabled(hsc, QUICK_ACTION_TILES[0].requiredWorkspaces)).toBe(true);
  });

  it("registers ninety administration-governance routes for parity", () => {
    expect(ADMINISTRATION_GOVERNANCE_ROUTE_COUNT).toBe(90);
  });
});