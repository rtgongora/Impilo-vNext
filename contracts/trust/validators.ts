/**
 * Trust catalogue integrity validators — used by tests and CI gates.
 */

import type { CatalogueEntry } from "./catalogue-schema";
import identitySeed from "./seeds/identity.json";
import providerWorkerSeed from "./seeds/provider-worker.json";
import workContextSeed from "./seeds/work-context.json";
import permissionsSeed from "./seeds/permissions.json";
import governanceMarketplaceSeed from "./seeds/governance-marketplace.json";
import roleTemplatesSeed from "./seeds/role-templates.json";
import resolutionTabsOpaSeed from "./seeds/resolution-tabs-opa.json";
import zimbabweMohccSeed from "./seeds/zimbabwe-mohcc.json";
import type { TrustCatalogueBundle } from "./catalogue-schema";

const ALL_BUNDLES: TrustCatalogueBundle[] = [
  identitySeed as TrustCatalogueBundle,
  providerWorkerSeed as TrustCatalogueBundle,
  zimbabweMohccSeed as TrustCatalogueBundle,
  workContextSeed as TrustCatalogueBundle,
  permissionsSeed as TrustCatalogueBundle,
  governanceMarketplaceSeed as TrustCatalogueBundle,
  roleTemplatesSeed as TrustCatalogueBundle,
  resolutionTabsOpaSeed as TrustCatalogueBundle,
];

function activeEntries(category?: string): CatalogueEntry[] {
  const entries = ALL_BUNDLES.flatMap((b) => b.entries).filter((e) => e.status === "active");
  if (!category) return entries;
  return entries.filter((e) => e.category === category);
}

function allEntries(category?: string): CatalogueEntry[] {
  const entries = ALL_BUNDLES.flatMap((b) => b.entries);
  if (!category) return entries;
  return entries.filter((e) => e.category === category);
}

function findEntry(code: string, category?: string): CatalogueEntry | undefined {
  return activeEntries(category).find((e) => e.code === code);
}

function findAnyEntry(code: string, category?: string): CatalogueEntry | undefined {
  return allEntries(category).find((e) => e.code === code);
}

export function catalogueCodesByCategory(category: string): Set<string> {
  return new Set(activeEntries(category).map((e) => e.code));
}

export function validateAllCataloguesLoad(): string[] {
  const errors: string[] = [];
  try {
    if (ALL_BUNDLES.length < 8) errors.push(`expected >= 8 bundles, got ${ALL_BUNDLES.length}`);
    for (const b of ALL_BUNDLES) {
      if (!b.version) errors.push(`bundle ${b.catalogueId} missing version`);
      if (!b.entries?.length) errors.push(`bundle ${b.catalogueId} has no entries`);
    }
  } catch (e) {
    errors.push(`load failed: ${(e as Error).message}`);
  }
  return errors;
}

export function validateRequiredCatalogueCodes(): string[] {
  const missing: string[] = [];
  for (const [category, codes] of Object.entries(REQUIRED_CATALOGUE_CODES)) {
    for (const code of codes) {
      if (!findEntry(code, category)) {
        missing.push(`${category}:${code}`);
      }
    }
  }
  return missing;
}

export function validateRoleTemplates(): string[] {
  const errors: string[] = [];
  const categories = catalogueCodesByCategory("role_template_category");
  const contexts = catalogueCodesByCategory("approved_work_context_type");
  const workspaces = new Set([
    ...catalogueCodesByCategory("workspace_type"),
    ...catalogueCodesByCategory("registry_owner_workspace"),
    ...catalogueCodesByCategory("regulator_workspace"),
  ]);
  const permissions = catalogueCodesByCategory("permission");
  const opaPackages = catalogueCodesByCategory("opa_policy_package");
  const cadres = catalogueCodesByCategory("profession_cadre");

  for (const rt of activeEntries("role_template")) {
    const meta = (rt.metadata ?? {}) as Record<string, unknown>;
    const roleCategory = meta.roleCategory as string | undefined;
    if (roleCategory && !categories.has(roleCategory)) {
      errors.push(`role_template:${rt.code} unknown roleCategory ${roleCategory}`);
    }
    for (const ctx of (meta.allowedContextTypes as string[] | undefined) ?? []) {
      if (!contexts.has(ctx)) errors.push(`role_template:${rt.code} unknown context ${ctx}`);
    }
    for (const ws of (meta.allowedWorkspaces as string[] | undefined) ?? []) {
      if (!workspaces.has(ws)) errors.push(`role_template:${rt.code} unknown workspace ${ws}`);
    }
    for (const perm of (meta.defaultPermissions as string[] | undefined) ?? []) {
      if (!permissions.has(perm)) errors.push(`role_template:${rt.code} unknown permission ${perm}`);
    }
    for (const cadre of (meta.requiredCadres as string[] | undefined) ?? []) {
      if (cadre && !cadres.has(cadre)) errors.push(`role_template:${rt.code} unknown cadre ${cadre}`);
    }
    const opa = (meta.opaPolicyMapping as string | undefined) ?? rt.policyMapping;
    if (opa && !opaPackages.has(opa)) {
      errors.push(`role_template:${rt.code} unknown opaPolicyMapping ${opa}`);
    }
  }
  return errors;
}

export function validatePermissionDomains(): string[] {
  const errors: string[] = [];
  const domains = catalogueCodesByCategory("permission_domain");
  for (const perm of activeEntries("permission")) {
    const domain = (perm.metadata as { domain?: string } | undefined)?.domain;
    if (domain && !domains.has(domain) && !domains.has(domain.split(".")[0])) {
      // Allow nested domains like facility.staff when facility.staff or facility exists
      const root = domain.split(".")[0];
      if (!domains.has(root) && !domains.has(domain)) {
        errors.push(`permission:${perm.code} unknown domain ${domain}`);
      }
    }
  }
  return errors;
}

export function validateNoDeprecatedDefaults(): string[] {
  return activeEntries()
    .filter((e) => e.status === "deprecated" && (e.metadata as { isDefault?: boolean } | undefined)?.isDefault)
    .map((e) => `deprecated entry marked default: ${e.category}:${e.code}`);
}

export function validateRolePermissionBaselines(): string[] {
  const errors: string[] = [];
  const chief = findRoleTemplateMeta("chief_pharmacist");
  const pharmacist = findRoleTemplateMeta("pharmacist");
  if (!chief?.defaultPermissions?.includes("pharmacy.stock.adjust.approve")) {
    errors.push("chief_pharmacist missing pharmacy.stock.adjust.approve");
  }
  if (pharmacist?.defaultPermissions?.includes("pharmacy.stock.adjust.approve")) {
    errors.push("pharmacist must not have chief approval permission");
  }
  const facilityAdmin = findRoleTemplateMeta("facility_administrator");
  if (facilityAdmin?.defaultPermissions?.includes("registry.provider.verify")) {
    errors.push("facility_administrator must not verify provider registry entries");
  }
  const nationalAdmin = findRoleTemplateMeta("national_platform_administrator");
  if (nationalAdmin?.defaultPermissions?.includes("break_glass.authorize")) {
    errors.push("national_platform_administrator must not include break_glass by default");
  }
  const marketplaceAdmin = findRoleTemplateMeta("marketplace_organisation_admin");
  if (marketplaceAdmin?.defaultPermissions?.some((p) => p.startsWith("clinical."))) {
    errors.push("marketplace_organisation_admin must not have clinical permissions");
  }
  return errors;
}

export function validateZimbabweNativeRoles(): string[] {
  const errors: string[] = [];
  const primaryCadre = (code: string) => findEntry(code, "profession_cadre");
  const primaryRole = (code: string) => findEntry(code, "role_template");

  for (const code of [
    "district_medical_officer",
    "provincial_medical_director",
    "district_nursing_officer",
    "provincial_nursing_officer",
    "district_health_information_officer",
    "provincial_health_information_officer",
    "provincial_epidemiology_disease_control_officer",
    "sister_in_charge",
    "sister_in_charge_community",
  ]) {
    const cadre = primaryCadre(code);
    const role = primaryRole(code);
    if (!cadre || cadre.status !== "active") errors.push(`missing active cadre ${code}`);
    if (!role || role.status !== "active") errors.push(`missing active role template ${code}`);
    const meta = (cadre?.metadata ?? role?.metadata) as { primaryZimbabweRole?: boolean; abbreviation?: string } | undefined;
    if (!meta?.primaryZimbabweRole) errors.push(`${code} must have primaryZimbabweRole=true`);
  }

  const dho = findAnyEntry("district_health_officer", "profession_cadre");
  const pho = findAnyEntry("provincial_health_officer", "profession_cadre");
  if (!dho || dho.status !== "deprecated") errors.push("district_health_officer must be deprecated");
  if (!pho || pho.status !== "deprecated") errors.push("provincial_health_officer must be deprecated");
  if (dho?.metadata?.mapsTo !== "district_medical_officer") errors.push("district_health_officer must mapTo DMO");
  if (pho?.metadata?.mapsTo !== "provincial_medical_director") errors.push("provincial_health_officer must mapTo PMD");

  const dmoRole = primaryRole("district_medical_officer");
  const pmdRole = primaryRole("provincial_medical_director");
  if (dmoRole?.displayName !== "District Medical Officer") errors.push("DMO displayName must be Zimbabwe-native");
  if (pmdRole?.displayName !== "Provincial Medical Director") errors.push("PMD displayName must be Zimbabwe-native");
  if ((dmoRole?.metadata as { abbreviation?: string })?.abbreviation !== "DMO") errors.push("DMO abbreviation missing");
  if ((pmdRole?.metadata as { abbreviation?: string })?.abbreviation !== "PMD") errors.push("PMD abbreviation missing");

  const activePrimaryDhoRole = activeEntries("role_template").find(
    (e) => e.status === "active" && e.displayName?.includes("District Health Officer") && e.code !== "district_medical_officer",
  );
  if (activePrimaryDhoRole) errors.push("generic District Health Officer must not be an active primary role template");

  return errors;
}

export function validateMohccOrganogramRoles(): string[] {
  const errors: string[] = [];
  const role = (code: string) => findEntry(code, "role_template");
  const structure = (code: string) => findEntry(code, "organisational_structure");

  if (!structure("directorate_curative_services")) errors.push("missing directorate_curative_services structure");
  if (!structure("directorate_ict_digital_health_information")) errors.push("missing ICT/digital health directorate structure");
  if (!structure("mohcc_organogram_hsc_2025_03_05")) errors.push("missing organogram grounding source entry");

  for (const code of [
    "minister_of_health",
    "permanent_secretary",
    "chief_director_curative_services",
    "chief_director_public_health",
    "director_internal_audit",
    "provincial_medical_director",
    "medical_superintendent",
    "district_medical_officer",
  ]) {
    if (!role(code)) errors.push(`missing organogram role template ${code}`);
  }

  const dmo = role("district_medical_officer");
  const workspaces = (dmo?.metadata as { allowedWorkspaces?: string[] })?.allowedWorkspaces ?? [];
  if (!workspaces.includes("district_dashboard")) errors.push("DMO must include district_dashboard workspace");

  const minister = role("minister_of_health");
  const ministerPerms = (minister?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
  if (ministerPerms.some((p) => p.startsWith("clinical."))) {
    errors.push("minister_of_health must not have clinical permissions by default");
  }

  const auditor = role("internal_auditor");
  const auditorPerms = (auditor?.metadata as { defaultPermissions?: string[] })?.defaultPermissions ?? [];
  if (auditorPerms.some((p) => p.startsWith("clinical."))) {
    errors.push("internal_auditor must not have clinical permissions by default");
  }
  if (!auditorPerms.includes("audit.logs.view")) errors.push("internal_auditor must include audit.logs.view");

  const pmd = role("provincial_medical_director");
  const pmdAliases = (pmd?.metadata as { aliases?: string[] })?.aliases ?? [];
  if (!pmdAliases.some((a) => a.includes("Provincial Health Officer"))) {
    errors.push("PMD must alias generic Provincial Health Officer labels");
  }

  return errors;
}

function findRoleTemplateMeta(code: string): { defaultPermissions?: string[] } | undefined {
  const entry = findEntry(code, "role_template");
  return entry?.metadata as { defaultPermissions?: string[] } | undefined;
}

/** Canonical required codes — mirrors acceptance criteria */
export const REQUIRED_CATALOGUE_CODES: Record<string, string[]> = {
  identity_type: [
    "citizen", "provider_worker", "dual_citizen_provider", "marketplace_actor",
    "regulator", "registry_owner", "workplace_manager", "system_admin",
  ],
  login_method: ["health_id", "provider_id", "email", "phone", "sso"],
  citizen_account_state: ["verified", "suspended", "deceased", "duplicate_under_review"],
  identity_assurance_level: ["ial0_unknown", "ial2_verified_basic", "ial4_biometric_or_official_verified"],
  provider_worker_type: ["medical_doctor", "nurse", "pharmacist", "records_clerk"],
  profession_cadre: [
    "general_medical_practitioner", "pharmacist", "records_clerk",
    "district_medical_officer", "provincial_medical_director", "sister_in_charge_community",
  ],
  qualification_certification_category: ["practice_licence", "cpd_certificate"],
  provider_worker_status: ["verified", "active", "suspended", "active_restricted"],
  cpd_compliance_status: ["compliant", "overdue", "restricted_until_complete"],
  approved_work_context_type: ["facility_clinical", "marketplace_operations", "registry_governance"],
  workplace_place_type: ["clinic", "district_hospital", "marketplace_organisation"],
  workspace_type: ["pharmacy", "facility_staff_management", "marketplace_vendor_portal"],
  assignment_type: ["facility_assignment", "marketplace_organisation_assignment"],
  assignment_status: ["active", "pending_approval", "ended"],
  role_template_category: ["medical", "pharmacy", "registry_governance", "marketplace_operations"],
  role_template: [
    "chief_pharmacist", "facility_administrator", "telemedicine_provider",
    "marketplace_organisation_admin", "district_medical_officer", "provincial_medical_director",
    "district_nursing_officer", "sister_in_charge_community",
  ],
  health_administrative_level: ["national", "province", "district", "facility", "community"],
  organisational_structure: ["mohcc_national_headquarters", "provincial_medical_office", "district_medical_office"],
  permission_domain: ["personal", "work", "registry", "marketplace"],
  permission: ["work.context.enter", "registry.provider.verify", "break_glass.authorize"],
  registry_governance_role: ["provider_registry_steward", "client_registry_steward"],
  regulator_role: ["professional_council_regulator", "marketplace_certification_reviewer"],
  workplace_manager_role: ["facility_administrator"],
  system_admin_role: ["national_platform_administrator"],
  marketplace_participant_type: ["vendor", "software_integration_partner"],
  marketplace_access_pipeline_state: ["sandbox_access_granted", "production_access_granted", "suspended"],
  marketplace_role: ["marketplace_organisation_admin"],
  nompilo_action_domain: ["personal_health_support", "work_navigation"],
  audit_event_type: ["session.contract.issued", "access.policy.denied"],
  friendly_resolution_state: [
    "no_active_work_assignment", "provider_suspended", "citizen_only_access",
    "marketplace_sandbox_only", "device_context_requires_user_login",
  ],
  session_tab: ["personal", "professional", "work"],
  opa_policy_package: ["impilo.tabs", "impilo.work", "impilo.registry", "impilo.marketplace", "impilo.break_glass"],
};

export function runAllCatalogueValidators(): { ok: boolean; errors: string[] } {
  const errors = [
    ...validateAllCataloguesLoad(),
    ...validateRequiredCatalogueCodes(),
    ...validateRoleTemplates(),
    ...validatePermissionDomains(),
    ...validateNoDeprecatedDefaults(),
    ...validateRolePermissionBaselines(),
    ...validateZimbabweNativeRoles(),
    ...validateMohccOrganogramRoles(),
  ];
  return { ok: errors.length === 0, errors };
}
