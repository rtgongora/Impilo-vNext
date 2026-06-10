import { describe, expect, it } from "vitest";
import {
  loadTrustCatalogueBundles,
  validateRequiredCatalogueCodes,
  findRoleTemplate,
  resolveSessionExperienceContract,
  sessionContractAllowsRoute,
  runAllCatalogueValidators,
  TRUST_CATALOGUE_VERSION,
  findPermission,
  findCatalogueEntryByCode,
} from "@/lib/trust";

describe("trust catalogue bundles", () => {
  it("loads versioned seed bundles", () => {
    const bundles = loadTrustCatalogueBundles();
    expect(bundles.length).toBeGreaterThanOrEqual(8);
    expect(bundles.every((b) => b.version === TRUST_CATALOGUE_VERSION)).toBe(true);
  });

  it("contains all required baseline codes", () => {
    expect(validateRequiredCatalogueCodes()).toEqual([]);
  });

  it("passes full catalogue integrity validation", () => {
    const result = runAllCatalogueValidators();
    expect(result.errors).toEqual([]);
    expect(result.ok).toBe(true);
  });

  it("maps chief_pharmacist to pharmacy approval permissions", () => {
    const chief = findRoleTemplate("chief_pharmacist");
    expect(chief).toBeDefined();
    const perms = (chief?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms).toContain("pharmacy.dispense");
    expect(perms).toContain("pharmacy.stock.adjust.approve");
  });

  it("does not grant chief approval permissions to ordinary pharmacist", () => {
    const pharmacist = findRoleTemplate("pharmacist");
    const perms = (pharmacist?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms).not.toContain("pharmacy.stock.adjust.approve");
  });

  it("facility administrator cannot issue provider registry verification", () => {
    const admin = findRoleTemplate("facility_administrator");
    const perms = (admin?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms).not.toContain("registry.provider.verify");
    expect(perms).toContain("facility.staff.assign");
  });

  it("national platform admin excludes break-glass by default", () => {
    const admin = findRoleTemplate("national_platform_administrator");
    const restricted = (admin?.metadata as { restrictedPermissions?: string[] })?.restrictedPermissions ?? [];
    expect(restricted).toContain("break_glass.authorize");
  });

  it("marketplace organisation admin has no clinical permissions", () => {
    const admin = findRoleTemplate("marketplace_organisation_admin");
    const perms = (admin?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms.some((p) => p.startsWith("clinical."))).toBe(false);
    expect(perms).toContain("marketplace.api.request_sandbox");
  });

  it("seeds all three session tabs with routes", () => {
    for (const code of ["personal", "professional", "work"]) {
      const tab = findCatalogueEntryByCode(code, "session_tab");
      expect(tab?.metadata).toHaveProperty("route");
      expect(tab?.metadata).toHaveProperty("requiredSource");
    }
  });

  it("seeds marketplace pipeline states with policy mappings", () => {
    const sandbox = findCatalogueEntryByCode("sandbox_access_granted", "marketplace_access_pipeline_state");
    const production = findCatalogueEntryByCode("production_access_granted", "marketplace_access_pipeline_state");
    expect(sandbox?.policyMapping).toContain("sandbox");
    expect(production?.policyMapping).toContain("production");
    expect(sandbox?.policyMapping).not.toEqual(production?.policyMapping);
  });

  it("seeds OPA packages referenced by permissions", () => {
    const perm = findPermission("work.context.enter");
    expect(perm?.policyMapping).toBe("impilo.work");
    expect(findCatalogueEntryByCode("impilo.work", "opa_policy_package")).toBeDefined();
  });
});

describe("Zimbabwe-native MoHCC catalogues", () => {
  it("seeds DMO and PMD as primary Zimbabwe roles", () => {
    const dmo = findRoleTemplate("district_medical_officer");
    const pmd = findRoleTemplate("provincial_medical_director");
    expect(dmo?.displayName).toBe("District Medical Officer");
    expect(pmd?.displayName).toBe("Provincial Medical Director");
    expect((dmo?.metadata as { abbreviation?: string })?.abbreviation).toBe("DMO");
    expect((pmd?.metadata as { abbreviation?: string })?.abbreviation).toBe("PMD");
    expect((dmo?.metadata as { primaryZimbabweRole?: boolean })?.primaryZimbabweRole).toBe(true);
    expect((pmd?.metadata as { primaryZimbabweRole?: boolean })?.primaryZimbabweRole).toBe(true);
  });

  it("seeds DNO, PNO, DHIO, PHIO, PEDCO, SIC and SICC", () => {
    for (const [code, abbr] of [
      ["district_nursing_officer", "DNO"],
      ["provincial_nursing_officer", "PNO"],
      ["district_health_information_officer", "DHIO"],
      ["provincial_health_information_officer", "PHIO"],
      ["provincial_epidemiology_disease_control_officer", "PEDCO"],
      ["sister_in_charge", "SIC"],
      ["sister_in_charge_community", "SICC"],
    ] as const) {
      const role = findRoleTemplate(code);
      expect(role, code).toBeDefined();
      expect((role?.metadata as { abbreviation?: string })?.abbreviation, code).toBe(abbr);
      expect((role?.metadata as { primaryZimbabweRole?: boolean })?.primaryZimbabweRole, code).toBe(true);
    }
  });

  it("deprecates generic district/provincial health officer cadres", () => {
    const dho = findCatalogueEntryByCode("district_health_officer", "profession_cadre");
    const pho = findCatalogueEntryByCode("provincial_health_officer", "profession_cadre");
    expect(dho).toBeUndefined();
    const allCadres = loadTrustCatalogueBundles()
      .flatMap((b) => b.entries)
      .filter((e) => e.category === "profession_cadre" && ["district_health_officer", "provincial_health_officer"].includes(e.code));
    expect(allCadres.every((e) => e.status === "deprecated")).toBe(true);
    expect(allCadres.find((e) => e.code === "district_health_officer")?.metadata?.mapsTo).toBe("district_medical_officer");
    expect(allCadres.find((e) => e.code === "provincial_health_officer")?.metadata?.mapsTo).toBe("provincial_medical_director");
  });

  it("scopes above-site roles by administrative level", () => {
    const dmo = findRoleTemplate("district_medical_officer");
    const pmd = findRoleTemplate("provincial_medical_director");
    const national = findRoleTemplate("national_platform_administrator");
    expect((dmo?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("district");
    expect((pmd?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("province");
    expect((national?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("national");
  });

  it("seeds Zimbabwe administrative levels and MoHCC structures", () => {
    expect(findCatalogueEntryByCode("national", "health_administrative_level")).toBeDefined();
    expect(findCatalogueEntryByCode("district", "health_administrative_level")).toBeDefined();
    expect(findCatalogueEntryByCode("mohcc_national_headquarters", "organisational_structure")).toBeDefined();
    expect(findCatalogueEntryByCode("district_medical_office", "organisational_structure")).toBeDefined();
  });

  it("marks role templates as extensible governed baseline entries", () => {
    const dmo = findRoleTemplate("district_medical_officer");
    expect((dmo?.metadata as { extensible?: boolean })?.extensible).toBe(true);
    expect((dmo?.metadata as { approvalStatus?: string })?.approvalStatus).toBe("approved_baseline");
  });
});

describe("MoHCC organogram-aware seed catalogues", () => {
  it("grounds catalogues in the HSC-approved organogram source", () => {
    const source = findCatalogueEntryByCode("mohcc_organogram_hsc_2025_03_05", "organisational_structure");
    expect(source?.metadata).toMatchObject({
      approvedBy: "Health Service Commission",
      approvedDate: "2025-03-05",
      organogramGrounding: true,
    });
  });

  it("seeds directorate and provincial medical office structures", () => {
    expect(findCatalogueEntryByCode("directorate_curative_services", "organisational_structure")).toBeDefined();
    expect(findCatalogueEntryByCode("directorate_ict_digital_health_information", "organisational_structure")).toBeDefined();
    expect(findCatalogueEntryByCode("provincial_medical_directorate", "organisational_structure")).toBeDefined();
    expect(findCatalogueEntryByCode("central_hospital_executive", "organisational_structure")).toBeDefined();
  });

  it("seeds national leadership and audit roles without default clinical access", () => {
    for (const code of ["minister_of_health", "permanent_secretary", "chief_director_public_health"]) {
      const role = findRoleTemplate(code);
      expect(role, code).toBeDefined();
      const perms = (role?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
      expect(perms.some((p) => p.startsWith("clinical.")), code).toBe(false);
    }
    const auditor = findRoleTemplate("internal_auditor");
    const auditorPerms = (auditor?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(auditorPerms).toContain("audit.logs.view");
    expect(auditorPerms.some((p) => p.startsWith("clinical."))).toBe(false);
  });

  it("maps DMO and PMD to organogram workspaces with Zimbabwe-native aliases", () => {
    const dmo = findRoleTemplate("district_medical_officer");
    const pmd = findRoleTemplate("provincial_medical_director");
    expect((dmo?.metadata as { allowedWorkspaces?: string[] })?.allowedWorkspaces).toContain("district_dashboard");
    expect((pmd?.metadata as { allowedWorkspaces?: string[] })?.allowedWorkspaces).toContain("provincial_dashboard");
    expect((dmo?.metadata as { aliases?: string[] })?.aliases).toEqual(
      expect.arrayContaining(["District Health Officer", "District Health Manager"]),
    );
    expect((pmd?.metadata as { aliases?: string[] })?.aliases).toEqual(
      expect.arrayContaining(["Provincial Health Officer"]),
    );
  });

  it("seeds curative, public health, digital health and support services role templates", () => {
    for (const code of [
      "director_nursing_services",
      "deputy_director_epidemiology",
      "impilo_platform_engineer",
      "interoperability_officer",
      "ehr_support_officer",
      "auditor",
      "director_human_resources",
      "chief_executive_officer",
      "district_environmental_health_officer",
      "nurse_in_charge",
    ]) {
      expect(findRoleTemplate(code), code).toBeDefined();
    }
  });

  it("maps section-13 context workspaces for SIC, matron and facility ICT support", () => {
    const sic = findRoleTemplate("sister_in_charge");
    const matron = findRoleTemplate("matron");
    const ict = findRoleTemplate("facility_ict_support");
    expect((sic?.metadata as { allowedWorkspaces?: string[] })?.allowedWorkspaces).toEqual(
      expect.arrayContaining(["queue", "staff_roster", "client_registration"]),
    );
    expect((matron?.metadata as { allowedContextTypes?: string[] })?.allowedContextTypes).toContain("nursing");
    expect((ict?.metadata as { allowedWorkspaces?: string[] })?.allowedWorkspaces).toContain("facility_support");
    const ictPerms = (ict?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(ictPerms.some((p) => p.startsWith("clinical."))).toBe(false);
  });
});

describe("three-tab session experience contract", () => {
  it("citizen sees Personal only", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      loginMethod: "health_id",
      healthId: "H1",
    });
    expect(c.tabs.personal.visible).toBe(true);
    expect(c.tabs.professional.visible).toBe(false);
    expect(c.tabs.work.visible).toBe(false);
  });

  it("verified provider without assignment sees Professional not Work", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      loginMethod: "provider_id",
      healthId: "H2",
      providerWorkerId: "P2",
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [],
    });
    expect(c.tabs.professional.visible).toBe(true);
    expect(c.tabs.professional.default).toBe(true);
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("no_active_work_assignment");
  });

  it("active assignment creates Work tab", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P3",
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "P3",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC1",
        },
      ],
    });
    expect(c.tabs.work.visible).toBe(true);
  });

  it("health ID login defaults to Personal tab", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      loginMethod: "health_id",
      healthId: "H4",
      providerWorkerId: "P4",
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [],
    });
    expect(c.tabs.personal.default).toBe(true);
  });

  it("blocks citizens from work routes", () => {
    const citizen = resolveSessionExperienceContract({ authenticated: true, healthId: "H5" });
    expect(sessionContractAllowsRoute(citizen, "/clinical")).toBe(false);
  });

  it("marketplace actor with sandbox pipeline gets sandbox friendly state", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      loginMethod: "email",
      organisationId: "ORG1",
      identityType: "marketplace_actor",
      marketplacePipelineState: "sandbox_access_granted",
      workAssignments: [
        {
          assignmentId: "M1",
          subjectId: "U1",
          subjectType: "marketplace_actor",
          contextType: "marketplace_operations",
          assignmentType: "marketplace_organisation_assignment",
          assignmentStatus: "active",
          organisationId: "ORG1",
        },
      ],
    });
    expect(c.tabs.personal.visible).toBe(false);
    expect(c.tabs.work.visible).toBe(true);
  });
});
