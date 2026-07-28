import previewVashandiFixtures from "../../../../../../contracts/trust/seeds/preview-vashandi-session-fixtures.json";
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
  resolveManagementWorkspacesForOrganisation,
  organisationBlocksWork,
  publicSectorEmploymentBlocksWork,
  resolveHscManagementWorkspaces,
  isHscOrganisationType,
} from "@/lib/trust";

describe("trust catalogue bundles", () => {
  it("loads versioned seed bundles", () => {
    const bundles = loadTrustCatalogueBundles();
    expect(bundles.length).toBeGreaterThanOrEqual(11);
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

  it("bootstrap administrator is time-limited and not a permanent superuser", () => {
    const bootstrap = findRoleTemplate("national_bootstrap_administrator");
    expect(bootstrap).toBeDefined();
    const meta = bootstrap?.metadata as {
      bootstrapOnly?: boolean;
      expiresAfterBootstrapClosure?: boolean;
      notPermanentSuperuser?: boolean;
      restrictedPermissions?: string[];
    };
    expect(meta?.bootstrapOnly).toBe(true);
    expect(meta?.expiresAfterBootstrapClosure).toBe(true);
    expect(meta?.notPermanentSuperuser).toBe(true);
    expect(meta?.restrictedPermissions).toContain("break_glass.authorize");
  });

  it("organisation data uploader is staged import only", () => {
    const uploader = findRoleTemplate("organisation_data_uploader");
    const meta = uploader?.metadata as { stagedImportOnly?: boolean; organisationScopedOnly?: boolean };
    expect(meta?.stagedImportOnly).toBe(true);
    expect(meta?.organisationScopedOnly).toBe(true);
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

describe("Regulator, municipality and Madi seed catalogues", () => {
  it("seeds nine real Zimbabwe regulatory organisations", () => {
    for (const code of [
      "health_professions_authority",
      "medical_laboratories_clinical_scientists_council",
      "medical_dental_practitioners_council",
      "allied_health_practitioners_council",
      "pharmacists_council",
      "nurses_council",
      "environmental_health_practitioners_council",
      "medical_rehabilitation_practitioners_council",
      "natural_therapists_council",
    ]) {
      const org = findCatalogueEntryByCode(code, "regulatory_organisation");
      expect(org, code).toBeDefined();
      expect((org?.metadata as { isRealZimbabweInstitution?: boolean })?.isRealZimbabweInstitution).toBe(true);
    }
  });

  it("deprecates generic professional council regulator role", () => {
    const all = loadTrustCatalogueBundles().flatMap((b) => b.entries);
    const genericRegRole = all.find((e) => e.code === "professional_council_regulator" && e.category === "regulator_role");
    const genericTemplate = all.find((e) => e.code === "professional_council_regulator" && e.category === "role_template");
    expect(genericRegRole?.status).toBe("deprecated");
    expect(genericTemplate?.status).toBe("deprecated");
  });

  it("scopes council roles as non-clinical by default", () => {
    for (const code of ["mdpc_reviewer", "nc_registrar", "hpa_administrator", "ntc_reviewer"]) {
      const role = findRoleTemplate(code);
      const perms = (role?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
      expect(perms.some((p) => p.startsWith("clinical.")), code).toBe(false);
      expect(perms).toContain("regulator.review");
    }
  });

  it("scopes municipal roles locally without national registry powers", () => {
    const mmo = findRoleTemplate("municipal_medical_officer");
    expect((mmo?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("local_authority");
    const perms = (mmo?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms).not.toContain("registry.provider.approve");
    expect(findCatalogueEntryByCode("city_health_department_dashboard", "workspace_type")).toBeDefined();
  });

  it("seeds Madi roles with blood-domain permissions not general clinical access", () => {
    const collector = findRoleTemplate("blood_collection_officer");
    const perms = (collector?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms.some((p) => p.startsWith("madi."))).toBe(true);
    expect(perms.some((p) => p.startsWith("clinical."))).toBe(false);
    expect(findPermission("madi.donor.view")).toBeDefined();
    expect(findCatalogueEntryByCode("madi_blood_service_dashboard", "workspace_type")).toBeDefined();
  });

  it("seeds scoped management workspaces instead of generic user management", () => {
    for (const [code, label] of [
      ["council_user_management", "Council User Management"],
      ["facility_work_assignment", "Facility Work Assignment"],
      ["blood_service_user_management", "Blood Service User Management"],
      ["marketplace_organisation_user_management", "Marketplace Organisation Users"],
    ] as const) {
      const ws = findCatalogueEntryByCode(code, "management_workspace");
      expect(ws, code).toBeDefined();
      expect(ws?.displayName).toBe(label);
    }
  });

  it("requires active work assignment for Work tab", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P9",
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [],
    });
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("no_active_work_assignment");
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

  it("blocks citizens from /work (Work Home, Phase F) exactly like the other work prefixes", () => {
    const citizen = resolveSessionExperienceContract({ authenticated: true, healthId: "H5" });
    expect(sessionContractAllowsRoute(citizen, "/work")).toBe(false);
    expect(sessionContractAllowsRoute(citizen, "/work/vashandi")).toBe(false);
  });

  it("allows /work for a provider with an active work assignment", () => {
    const provider = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P9",
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [
        {
          assignmentId: "A9",
          subjectId: "P9",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC9",
        },
      ],
    });
    expect(sessionContractAllowsRoute(provider, "/work")).toBe(true);
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

describe("Multi-organisation user management and external actor governance", () => {
  it("loads organisation types, trust tiers, lifecycle states, environments and user types", () => {
    expect(findCatalogueEntryByCode("sovereign_public_owner", "organisation_type")).toBeDefined();
    expect(findCatalogueEntryByCode("private_hospital", "organisation_type")).toBeDefined();
    expect(findCatalogueEntryByCode("tier_6_sovereign_public_operator", "organisation_trust_tier")).toBeDefined();
    expect(findCatalogueEntryByCode("tier_0_unverified", "organisation_trust_tier")).toBeDefined();
    expect(findCatalogueEntryByCode("sandbox_only", "organisation_lifecycle_state")).toBeDefined();
    expect(findCatalogueEntryByCode("deidentified_only", "organisation_access_environment")).toBeDefined();
    expect(findCatalogueEntryByCode("organisation_administrator", "organisation_linked_user_type")).toBeDefined();
    expect(findCatalogueEntryByCode("external_researcher", "organisation_linked_user_type")).toBeDefined();
  });

  it("seeds exemplar organisation registry records with trust and scope metadata", () => {
    const mohcc = findCatalogueEntryByCode("mohcc_zimbabwe", "organisation_registry_record");
    expect((mohcc?.metadata as { trustTier?: string })?.trustTier).toBe("tier_6_sovereign_public_operator");
    expect(findCatalogueEntryByCode("harare_city_health", "organisation_registry_record")).toBeDefined();
    expect(findCatalogueEntryByCode("avenues_private_hospital", "organisation_registry_record")).toBeDefined();
  });

  it("scopes MoHCC roles without universal superuser access", () => {
    const dmo = findRoleTemplate("district_medical_officer");
    const pmd = findRoleTemplate("provincial_medical_director");
    expect((dmo?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("district");
    expect((pmd?.metadata as { policyScopeLevel?: string })?.policyScopeLevel).toBe("province");
    const perms = (dmo?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms).not.toContain("system.admin.full");
    expect(perms).not.toContain("registry.provider.approve");
  });

  it("municipal user manages municipal staff only and not MoHCC national tools", () => {
    const ws = resolveManagementWorkspacesForOrganisation({
      organisationType: "city_health_department",
      organisationLifecycleStatus: "active",
      userOrganisationRole: "organisation_administrator",
    });
    expect(ws.visible).toContain("municipal_user_management");
    expect(ws.blocked).toContain("mohcc_head_office_user_management");
    expect(ws.blocked).toContain("national_platform_user_administration");
  });

  it("private facility admin manages own users but not public facility staff", () => {
    const ws = resolveManagementWorkspacesForOrganisation({
      organisationType: "private_hospital",
      organisationLifecycleStatus: "active",
      userOrganisationRole: "organisation_administrator",
    });
    expect(ws.visible).toContain("private_facility_user_management");
    expect(ws.visible).not.toContain("public_facility_staff_management");
    expect(ws.blocked).toContain("mohcc_head_office_user_management");
  });

  it("private clinician still requires provider verification and assignment for Work", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-PRIV",
      organisation: {
        organisationId: "org-private-hospital",
        organisationType: "private_hospital",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "clinical_staff",
      },
      professionalTruth: { providerWorkerStatus: "active" },
      workAssignments: [],
    });
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("no_active_work_assignment");
  });

  it("private unverified provider cannot access Work even with organisation assignment", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-PRIV",
      organisation: {
        organisationId: "org-private-hospital",
        organisationType: "private_hospital",
        organisationLifecycleStatus: "active",
        activeOrganisationAssignment: true,
        userOrganisationRole: "clinical_staff",
      },
      professionalTruth: { providerWorkerStatus: "pending_verification" },
      workAssignments: [
        {
          assignmentId: "A-PRIV",
          subjectId: "P-PRIV",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          organisationId: "org-private-hospital",
        },
      ],
    });
    expect(c.tabs.work.visible).toBe(false);
  });

  it("council user acts within council domain and cannot assign facility Work by default", () => {
    const ws = resolveManagementWorkspacesForOrganisation({
      organisationType: "statutory_health_council",
      organisationLifecycleStatus: "active",
      userOrganisationRole: "regulatory_reviewer",
    });
    expect(ws.visible).toContain("council_user_management");
    expect(ws.visible).not.toContain("facility_work_assignment");
    const mdpc = findRoleTemplate("mdpc_reviewer");
    const perms = (mdpc?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms.some((p) => p.startsWith("clinical."))).toBe(false);
  });

  it("vendor sandbox user cannot access production and has no clinical permissions", () => {
    expect(
      organisationBlocksWork({
        organisationType: "software_vendor",
        organisationAccessEnvironment: "sandbox",
        requiresProduction: true,
        activeOrganisationAssignment: true,
        identityType: "marketplace_actor",
      }),
    ).toBe("sandbox_only_access");
    const vendorRole = findRoleTemplate("marketplace_api_developer");
    const perms = (vendorRole?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(perms.some((p) => p.startsWith("clinical."))).toBe(false);
  });

  it("organisation suspension removes Work access", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-SUSP",
      professionalTruth: { providerWorkerStatus: "active" },
      organisation: {
        organisationId: "org-suspended",
        organisationType: "private_hospital",
        organisationLifecycleStatus: "suspended",
        activeOrganisationAssignment: true,
      },
      workAssignments: [
        {
          assignmentId: "A-SUSP",
          subjectId: "P-SUSP",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          organisationId: "org-suspended",
        },
      ],
    });
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("organisation_suspended");
  });

  it("payer user has claims scope only and not clinical browsing", () => {
    const ws = resolveManagementWorkspacesForOrganisation({
      organisationType: "insurer",
      organisationLifecycleStatus: "active",
      userOrganisationRole: "claims_user",
    });
    expect(ws.visible).toContain("claims_user_assignment");
    expect(ws.visible).not.toContain("public_facility_staff_management");
    const payerRole = findRoleTemplate("claims_processor_user");
    if (payerRole) {
      const perms = (payerRole.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
      expect(perms.some((p) => p.startsWith("clinical.record"))).toBe(false);
    }
  });

  it("external research partner defaults to deidentified access and requires project approval", () => {
    expect(
      organisationBlocksWork({
        organisationType: "foreign_research_partner",
        organisationAccessEnvironment: "deidentified_only",
        activeOrganisationAssignment: true,
        identityType: "marketplace_actor",
        researchProjectAssignment: false,
      }),
    ).toBe("research_project_approval_required");
    const ws = resolveManagementWorkspacesForOrganisation({
      organisationType: "foreign_research_partner",
      organisationLifecycleStatus: "active",
      userOrganisationRole: "external_researcher",
      approvedWorkspaces: ["deidentified_dataset_access"],
    });
    expect(ws.visible).toContain("deidentified_dataset_access");
    expect(ws.blocked).toContain("private_facility_user_management");
  });

  it("cross-border identified access blocked unless explicitly approved", () => {
    expect(
      organisationBlocksWork({
        organisationType: "non_zimbabwean_partner",
        crossBorderAccessFlag: true,
        crossBorderApproved: false,
        activeOrganisationAssignment: true,
        identityType: "marketplace_actor",
      }),
    ).toBe("cross_border_access_not_approved");
  });

  it("session contract includes organisation context and scoped management workspaces", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-MUNI",
      professionalTruth: { providerWorkerStatus: "active" },
      organisation: {
        organisationId: "org-harare-city-health",
        organisationType: "city_health_department",
        organisationName: "City of Harare Health Department",
        organisationTrustTier: "tier_4_regulated_service_provider",
        organisationLifecycleStatus: "active",
        organisationAccessEnvironment: "production_limited",
        organisationCountry: "ZW",
        activeOrganisationAssignment: true,
        userOrganisationRole: "organisation_administrator",
      },
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
    expect(c.organisation?.organisationType).toBe("city_health_department");
    expect(c.visibleManagementWorkspaces).toContain("municipal_user_management");
    expect(c.blockedManagementWorkspaces).toContain("mohcc_head_office_user_management");
  });
});

describe("Health Service Commission workforce governance", () => {
  it("seeds HSC as real Zimbabwe institution distinct from MoHCC and councils", () => {
    const hsc = findCatalogueEntryByCode("health_service_commission", "organisation_registry_record");
    expect(hsc).toBeDefined();
    expect((hsc?.metadata as { organisationType?: string })?.organisationType).toBe("health_service_commission");
    expect((hsc?.metadata as { isRealZimbabweInstitution?: boolean })?.isRealZimbabweInstitution).toBe(true);
    expect((hsc?.metadata as { trustTier?: string })?.trustTier).toBe("tier_7_high_trust_public_workforce_authority");
    expect(findCatalogueEntryByCode("health_service_commission", "organisation_type")).toBeDefined();
    expect(findCatalogueEntryByCode("health_service_commission", "hsc_organisation")).toBeDefined();
    const mohcc = findCatalogueEntryByCode("mohcc_zimbabwe", "organisation_registry_record");
    expect((mohcc?.metadata as { organisationType?: string })?.organisationType).toBe("sovereign_public_owner");
    expect(findCatalogueEntryByCode("mdpc_zimbabwe", "organisation_registry_record")).toBeDefined();
  });

  it("scopes HSC roles as non-clinical workforce governance without system superuser", () => {
    for (const code of ["hsc_administrator", "hsc_workforce_governance_officer", "hsc_appointments_officer"]) {
      const role = findRoleTemplate(code);
      expect(role, code).toBeDefined();
      const perms = (role?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
      expect(perms.some((p) => p.startsWith("clinical.")), code).toBe(false);
      expect(perms.some((p) => p.startsWith("hsc.")), code).toBe(true);
      expect(perms).not.toContain("system.roles.manage");
      expect(perms).not.toContain("registry.provider.verify");
    }
  });

  it("seeds HSC workspaces and management workspaces", () => {
    expect(findCatalogueEntryByCode("hsc_workspace", "workspace_type")).toBeDefined();
    expect(findCatalogueEntryByCode("hsc_establishment_control", "workspace_type")).toBeDefined();
    expect(findCatalogueEntryByCode("hsc_user_management", "management_workspace")).toBeDefined();
    expect(findCatalogueEntryByCode("hsc_public_sector_post_management", "management_workspace")).toBeDefined();
  });

  it("seeds public-sector employment statuses separately from provider worker status", () => {
    expect(findCatalogueEntryByCode("active", "public_sector_employment_status")).toBeDefined();
    expect(findCatalogueEntryByCode("suspended_from_employment", "public_sector_employment_status")).toBeDefined();
    expect(findCatalogueEntryByCode("verified", "provider_worker_status")).toBeDefined();
  });

  it("HSC officer Work requires organisation assignment and does not inherit clinical access", () => {
    const c = resolveSessionExperienceContract({
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
    expect(c.tabs.work.visible).toBe(true);
    expect(c.visibleManagementWorkspaces).toContain("hsc_user_management");
    expect(c.blockedManagementWorkspaces).toContain("mohcc_head_office_user_management");
    expect(c.blockedActions.some((a) => a.startsWith("clinical."))).toBe(true);
  });

  it("HSC employment alone does not create public-sector clinical Work without workplace assignment", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      healthId: "H-NURSE",
      providerWorkerId: "P-NURSE",
      professionalTruth: { providerWorkerStatus: "active", registrationBody: "nurses_council" },
      publicSectorEmployment: {
        providerWorkerId: "P-NURSE",
        employmentAuthority: "health_service_commission",
        employmentStatus: "active",
        employerOrganisationType: "public_facility",
        currentPostingFacility: "FAC-PUBLIC-1",
        verificationStatus: "verified",
      },
      workAssignments: [],
    });
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("no_active_work_assignment");
  });

  it("HSC suspension blocks public-sector Work even with facility assignment", () => {
    const c = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-SUSP-HSC",
      professionalTruth: { providerWorkerStatus: "active" },
      publicSectorEmployment: {
        providerWorkerId: "P-SUSP-HSC",
        employmentAuthority: "health_service_commission",
        employmentStatus: "suspended_from_employment",
        employerOrganisationType: "public_facility",
        currentPostingFacility: "FAC-PUBLIC-2",
      },
      workAssignments: [
        {
          assignmentId: "A-FAC",
          subjectId: "P-SUSP-HSC",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC-PUBLIC-2",
          employerOrganisationType: "public_facility",
        },
      ],
    });
    expect(c.tabs.work.visible).toBe(false);
    expect(c.friendlyResolutionState).toBe("hsc_employment_suspended");
    expect(c.publicSectorEmployment?.employmentFriendlyResolutionState).toBe("hsc_employment_suspended");
  });

  it("public-sector provider Work requires professional status, HSC employment, assignment and posting", () => {
    const blocked = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-NURSE2",
      professionalTruth: { providerWorkerStatus: "active" },
      publicSectorEmployment: {
        providerWorkerId: "P-NURSE2",
        employmentAuthority: "health_service_commission",
        employmentStatus: "transferred_pending_assumption",
        employerOrganisationType: "public_facility",
      },
      workAssignments: [
        {
          assignmentId: "A-N2",
          subjectId: "P-NURSE2",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC-X",
          employerOrganisationType: "public_facility",
        },
      ],
    });
    expect(blocked.tabs.work.visible).toBe(false);
    expect(blocked.friendlyResolutionState).toBe("hsc_transfer_pending_assumption");

    const allowed = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-NURSE3",
      professionalTruth: { providerWorkerStatus: "active" },
      publicSectorEmployment: {
        providerWorkerId: "P-NURSE3",
        employmentAuthority: "health_service_commission",
        employmentStatus: "active",
        employerOrganisationType: "public_facility",
        currentPostingFacility: "FAC-Y",
        verificationStatus: "verified",
      },
      workAssignments: [
        {
          assignmentId: "A-N3",
          subjectId: "P-NURSE3",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "FAC-Y",
          employerOrganisationType: "public_facility",
        },
      ],
    });
    expect(allowed.tabs.work.visible).toBe(true);
    expect(allowed.publicSectorEmployment?.publicSectorEmploymentStatus).toBe("active");
  });

  it("council professional status and HSC employment remain separate inputs", () => {
    expect(
      publicSectorEmploymentBlocksWork(
        {
          providerWorkerId: "P1",
          employmentAuthority: "health_service_commission",
          employmentStatus: "active",
          employerOrganisationType: "public_facility",
          currentPostingFacility: "FAC1",
        },
        { isPublicSectorAssignment: true },
      ),
    ).toBeUndefined();
    const councilRole = findRoleTemplate("nc_reviewer");
    const hscRole = findRoleTemplate("hsc_appointments_officer");
    const councilPerms = (councilRole?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    const hscPerms = (hscRole?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
    expect(councilPerms.some((p) => p.startsWith("regulator."))).toBe(true);
    expect(hscPerms.some((p) => p.startsWith("hsc."))).toBe(true);
    expect(hscPerms.some((p) => p.startsWith("regulator."))).toBe(false);
  });

  it("Vashandi role templates map to impilo.vashandi policy", () => {
    const selfService = findRoleTemplate("vashandi_self_service_worker");
    expect(selfService).toBeDefined();
    const meta = selfService?.metadata as {
      opaPolicyMapping?: string;
      allowedWorkspaces?: string[];
    };
    expect(meta?.opaPolicyMapping).toBe("impilo.vashandi");
    expect(meta?.allowedWorkspaces).toContain("vashandi.my_roster");
    expect(meta?.allowedWorkspaces).toContain("vashandi.my_attendance");
  });

  it("council user does not receive Vashandi facility assignment workspaces", () => {
    const contract = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-COUNCIL",
      organisation: {
        organisationId: "org-hpa",
        organisationType: "health_professions_authority",
        organisationName: "HPA",
      },
      workAssignments: [
        {
          assignmentId: "A-C",
          subjectId: "P-COUNCIL",
          subjectType: "provider_worker",
          contextType: "regulatory",
          assignmentType: "regulator_assignment",
          assignmentStatus: "active",
          roleTemplateId: "nc_reviewer",
        },
      ],
    });
    expect(contract.visibleVashandiWorkspaces ?? []).not.toContain("vashandi.assignments");
    expect(contract.blockedVashandiWorkspaces ?? []).toContain("vashandi.assignments");
  });

  it("ordinary provider sees self-service Vashandi workspaces only", () => {
    const contract = resolveSessionExperienceContract({
      authenticated: true,
      providerWorkerId: "P-NURSE",
      workAssignments: [
        {
          assignmentId: "A-N",
          subjectId: "P-NURSE",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          roleTemplateId: "vashandi_self_service_worker",
        },
      ],
    });
    const visible = contract.visibleVashandiWorkspaces ?? [];
    expect(visible).toContain("vashandi.my_roster");
    expect(visible).toContain("vashandi.my_attendance");
    expect(visible).not.toContain("vashandi.access_review");
  });

  it("preview Vashandi session fixtures resolve expected workspaces", () => {
    const fixtures = previewVashandiFixtures as {
      personas: Array<{
        label: string;
        healthId: string;
        providerPublicId: string;
        roleTemplateId: string;
        wgvOrganisationCode: string;
        expectedVisibleVashandiWorkspaces: string[];
      }>;
      negativeControl: {
        healthId: string;
        expectedVisibleVashandiWorkspaces: string[];
      };
    };

    const orgTypeByCode: Record<string, string> = {
      "MOHCC-NATIONAL": "sovereign_public_owner",
      "MOHCC-HCH": "public_facility",
      "MOHCC-HSC": "health_service_commission",
    };

    for (const persona of fixtures.personas) {
      const organisationType = orgTypeByCode[persona.wgvOrganisationCode];
      const contract = resolveSessionExperienceContract({
        authenticated: true,
        healthId: persona.healthId,
        providerWorkerId: persona.providerPublicId,
        professionalTruth: { providerWorkerStatus: "active" },
        publicSectorEmployment: {
          employmentStatus: "active",
          employerOrganisationType: organisationType,
        },
        organisation: {
          organisationId: `org-${persona.wgvOrganisationCode}`,
          organisationType,
          organisationName: persona.wgvOrganisationCode,
          activeOrganisationAssignment: true,
        },
        workAssignments: [
          {
            assignmentId: `A-${persona.providerPublicId}`,
            subjectId: persona.providerPublicId,
            subjectType: "provider_worker",
            contextType: "facility_clinical",
            assignmentType: "facility_assignment",
            assignmentStatus: "active",
            roleTemplateId: persona.roleTemplateId,
          },
        ],
      });
      for (const workspace of persona.expectedVisibleVashandiWorkspaces) {
        expect(contract.visibleVashandiWorkspaces ?? [], persona.label).toContain(workspace);
      }
    }

    const citizen = resolveSessionExperienceContract({
      authenticated: true,
      healthId: fixtures.negativeControl.healthId,
    });
    expect(citizen.visibleVashandiWorkspaces ?? []).toEqual([]);
  });
});
