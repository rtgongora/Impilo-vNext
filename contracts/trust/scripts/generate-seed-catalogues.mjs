#!/usr/bin/env node
/**
 * Generates versioned trust seed JSON bundles from canonical Impilo vocabulary.
 * Run: node contracts/trust/scripts/generate-seed-catalogues.mjs
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  CATALOGUE_DATA_VERSION,
  ZW_ADMIN_LEVELS,
  ZW_ORG_STRUCTURES,
  ZW_CADRE_PROFILES,
  ZW_ROLE_TEMPLATE_SPECS,
  ZW_ROLE_PROFILES,
  ROLE_PERMISSION_BASELINES,
  DEPRECATED_ROLE_TEMPLATES,
  ORGANOGRAM_SOURCE,
  ORGANOGRAM_WORKSPACES,
} from "./zimbabwe-catalogue-data.mjs";
import {
  ZIMBABWE_REGULATORY_ORGANISATIONS,
  REGULATOR_ORGANISATION_TYPES,
  DEPRECATED_FAKE_REGULATOR_CODES,
  DEPRECATED_GENERIC_REGULATOR_ROLES,
  DEPRECATED_GENERIC_REGULATOR_ROLE_MAPS,
  MUNICIPAL_ORGANISATION_TYPES,
  MUNICIPAL_WORKSPACES,
  MADI_ORGANISATION_TYPES,
  MADI_WORKSPACES,
  MADI_PERMISSION_DOMAINS,
  MADI_PERMISSIONS,
  REGULATOR_WORKSPACES,
  MANAGEMENT_WORKSPACES,
  MUNICIPAL_ROLES,
  MADI_ROLES,
} from "./regulator-municipality-madi-data.mjs";
import {
  ORGANISATION_TYPES,
  ORGANISATION_TRUST_TIERS,
  ORGANISATION_LIFECYCLE_STATES,
  ORGANISATION_ACCESS_ENVIRONMENTS,
  ORGANISATION_LINKED_USER_TYPES,
  ORGANISATION_REGISTRY_FIELDS,
  MULTI_ORG_MANAGEMENT_WORKSPACES,
  EXEMPLAR_ORGANISATIONS,
  ORGANISATION_FRIENDLY_RESOLUTION_STATES,
} from "./multi-organisation-governance-data.mjs";
import {
  HSC_ORGANISATION,
  HSC_WORKSPACES,
  HSC_MANAGEMENT_WORKSPACES,
  HSC_PERMISSIONS,
  HSC_PERMISSION_BASELINE,
  HSC_DENIED_ACTIONS,
  HSC_ROLE_DEFINITIONS,
  HSC_WORKFORCE_GOVERNANCE_ROLES,
  PUBLIC_SECTOR_EMPLOYMENT_STATUSES,
  HSC_FRIENDLY_RESOLUTION_STATES,
} from "./hsc-workforce-governance-data.mjs";
import {
  WORKFORCE_STATUSES,
  ASSIGNMENT_TYPES as VASHANDI_ASSIGNMENT_TYPES,
  ASSIGNMENT_STATUSES as VASHANDI_ASSIGNMENT_STATUSES,
  ROSTER_TYPES,
  SHIFT_STATUSES,
  ATTENDANCE_EVENT_TYPES,
  CHECK_IN_MODES,
  LEAVE_TYPES,
  ACCESS_RISK_TYPES,
  VASHANDI_WORKSPACES,
  VASHANDI_PERMISSIONS,
  VASHANDI_PERMISSION_BASELINE,
  VASHANDI_DENIED_ACTIONS,
  VASHANDI_ROLE_DEFINITIONS,
  VASHANDI_ROLE_TEMPLATE_SPECS,
  VASHANDI_WORKFORCE_ROLES,
  VASHANDI_FRIENDLY_RESOLUTION_STATES,
} from "./vashandi-workforce-data.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SEEDS_DIR = join(__dirname, "..", "seeds");
const VERSION = CATALOGUE_DATA_VERSION;

function titleCase(s) {
  return s.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

mkdirSync(SEEDS_DIR, { recursive: true });

function entry(partial) {
  return {
    status: "active",
    auditSensitivity: partial.auditSensitivity ?? "medium",
    ...partial,
  };
}

function bundle(catalogueId, displayName, entries) {
  return { version: VERSION, catalogueId, displayName, entries };
}

function writeSeed(name, data) {
  writeFileSync(join(SEEDS_DIR, `${name}.json`), JSON.stringify(data, null, 2) + "\n");
  console.log(`wrote ${name}.json (${data.entries.length} entries)`);
}

// ── Identity ────────────────────────────────────────────────────────────────
const identityTypes = [
  "citizen", "provider_worker", "dual_citizen_provider", "marketplace_actor",
  "regulator", "registry_owner", "workplace_manager", "system_admin",
  "support_user", "service_account", "device_account",
];
const loginMethods = [
  "health_id", "provider_id", "email", "phone", "sso",
  "assisted_login", "device_bound_login", "service_account_token",
];
const citizenStates = [
  "provisional", "self_registered", "assisted_registered", "verified",
  "strongly_verified", "biometrically_verified", "guardian_managed",
  "dependant_linked", "restricted", "locked", "suspended", "deceased",
  "duplicate_under_review", "merged",
];
const ialLevels = [
  "ial0_unknown", "ial1_self_asserted", "ial2_verified_basic",
  "ial3_strongly_verified", "ial4_biometric_or_official_verified",
];

const identityEntries = [
  ...identityTypes.map((code) =>
    entry({
      id: `idt-${code.replace(/_/g, "-")}`,
      code,
      displayName: code.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
      category: "identity_type",
      ownerService: code.includes("marketplace") ? "msika" : "tshepo",
      standardMapping: { openHie: code === "citizen" ? "ClientRegistry" : undefined, fhir: code === "citizen" ? "Patient" : code.includes("provider") ? "Practitioner" : undefined },
    }),
  ),
  ...loginMethods.map((code) =>
    entry({
      id: `lm-${code.replace(/_/g, "-")}`,
      code,
      displayName: code.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
      category: "login_method",
      ownerService: ["health_id", "provider_id", "assisted_login", "device_bound_login", "service_account_token"].includes(code) ? "tshepo" : "keycloak",
      policyMapping: code === "health_id" ? "impilo.personal" : code === "provider_id" ? "impilo.professional" : undefined,
    }),
  ),
  ...citizenStates.map((code) =>
    entry({
      id: `cas-${code.replace(/_/g, "-")}`,
      code,
      displayName: code.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
      category: "citizen_account_state",
      ownerService: "vito",
      standardMapping: { fhir: "Patient" },
    }),
  ),
  ...ialLevels.map((code) =>
    entry({
      id: code.replace(/_/g, "-"),
      code,
      displayName: code.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
      category: "identity_assurance_level",
      ownerService: "tshepo",
      standardMapping: { nist: code },
    }),
  ),
];

writeSeed("identity", bundle("identity", "Identity Types, Login Methods, Citizen States, IAL", identityEntries));

// ── Provider / worker ───────────────────────────────────────────────────────
const clinicalProviderTypes = [
  "medical_doctor", "specialist_doctor", "dentist", "nurse", "midwife", "nurse_aide",
  "clinical_officer", "pharmacist", "pharmacy_technician", "laboratory_scientist",
  "laboratory_technician", "radiographer", "sonographer", "physiotherapist",
  "occupational_therapist", "nutritionist", "dietician", "counsellor", "psychologist",
  "social_worker", "environmental_health_officer", "community_health_worker",
  "emergency_medical_provider",
];
const adminProviderTypes = [
  "records_clerk", "health_information_officer", "data_clerk", "billing_officer",
  "cashier", "facility_administrator", "hospital_manager", "department_administrator",
  "ict_support_officer", "helpdesk_agent", "supply_chain_officer", "procurement_officer",
  "logistics_officer", "programme_officer", "monitoring_and_evaluation_officer",
  "trainer", "mentor", "supervisor", "inspector", "auditor",
];
const providerStatuses = [
  "not_started", "draft", "submitted", "pending_verification", "verified", "active",
  "active_restricted", "suspended", "expired", "revoked", "retired", "deceased",
  "duplicate_under_review", "merged", "requires_update", "under_investigation",
];
const cpdStatuses = [
  "not_required", "compliant", "due_soon", "overdue", "incomplete", "expired",
  "exempted", "pending_verification", "failed_assessment", "restricted_until_complete",
];
const qualCategories = [
  "professional_degree", "diploma", "certificate", "postgraduate_degree",
  "specialist_training", "registration_certificate", "practice_licence",
  "temporary_practice_authorisation", "internship_completion", "board_certification",
  "council_registration", "mandatory_training_certificate", "cpd_certificate",
  "telemedicine_certification", "digital_health_system_training",
  "infection_prevention_control_training", "emergency_response_training",
  "public_health_inspection_authorisation", "dispensing_authorisation",
  "laboratory_accreditation_training", "radiology_safety_training",
  "data_protection_training", "information_security_training",
  "marketplace_integration_certification", "api_sandbox_certification",
];

const providerWorkerEntries = [
  ...clinicalProviderTypes.map((code) =>
    entry({ id: `pwt-${code}`, code, displayName: titleCase(code), category: "provider_worker_type", ownerService: "varapi", standardMapping: { fhir: "Practitioner", openHie: "ProviderRegistry" } }),
  ),
  ...adminProviderTypes.map((code) =>
    entry({ id: `pwt-${code}`, code, displayName: titleCase(code), category: "provider_worker_type", ownerService: "varapi", standardMapping: { fhir: "Practitioner" } }),
  ),
  ...providerStatuses.map((code) => {
    let policyMapping = "impilo.provider_worker.block_work";
    if (["verified", "active"].includes(code)) policyMapping = "impilo.professional.allow";
    if (code === "active_restricted") policyMapping = "impilo.work.limited";
    if (["pending_verification", "requires_update"].includes(code)) policyMapping = "impilo.provider_worker.status_resolution";
    return entry({ id: `pws-${code}`, code, displayName: titleCase(code), category: "provider_worker_status", ownerService: "varapi", policyMapping });
  }),
  ...cpdStatuses.map((code) =>
    entry({
      id: `cpd-${code}`,
      code,
      displayName: titleCase(code),
      category: "cpd_compliance_status",
      ownerService: "varapi",
      policyMapping: ["overdue", "restricted_until_complete", "failed_assessment"].includes(code) ? "impilo.work.block_training" : undefined,
    }),
  ),
  ...qualCategories.map((code) =>
    entry({ id: `qc-${code}`, code, displayName: titleCase(code), category: "qualification_certification_category", ownerService: "varapi", standardMapping: { fhir: "Qualification" } }),
  ),
  ...Object.entries(ZW_CADRE_PROFILES).map(([code, c]) =>
    entry({
      id: `cadre-${code}`,
      code,
      displayName: c.displayName ?? titleCase(code),
      category: "profession_cadre",
      status: c.status ?? "active",
      ownerService: "varapi",
      description: c.mapsTo ? `Deprecated alias; use ${c.mapsTo}` : undefined,
      metadata: {
        clinicalOrNonClinical: c.clinical,
        defaultEligibleContextTypes: c.contexts ?? [],
        defaultRoleTemplates: c.roles ?? [],
        abbreviation: c.abbreviation,
        aliases: c.aliases,
        primaryZimbabweRole: c.primaryZimbabweRole ?? false,
        mapsTo: c.mapsTo,
      },
    }),
  ),
];

writeSeed("provider-worker", bundle("provider-worker", "Provider Worker Types, Statuses, CPD, Cadres", providerWorkerEntries));

const zimbabweMohccEntries = [
  entry({
    id: "organogram-source-mohcc-hsc-2025",
    code: "mohcc_organogram_hsc_2025_03_05",
    displayName: ORGANOGRAM_SOURCE.title,
    description: `Approved by ${ORGANOGRAM_SOURCE.approvedBy} on ${ORGANOGRAM_SOURCE.approvedDate}`,
    category: "organisational_structure",
    ownerService: "tuso",
    metadata: {
      level: "national",
      parentStructure: null,
      primaryZimbabweStructure: true,
      organogramGrounding: true,
      approvedBy: ORGANOGRAM_SOURCE.approvedBy,
      approvedDate: ORGANOGRAM_SOURCE.approvedDate,
      organogramVersion: ORGANOGRAM_SOURCE.version,
    },
  }),
  ...ZW_ADMIN_LEVELS.map((level) =>
    entry({
      id: `zal-${level.code}`,
      code: level.code,
      displayName: level.displayName,
      description: level.description,
      category: "health_administrative_level",
      ownerService: "tshepo",
      policyMapping: level.policyScope,
      metadata: {
        parentLevel: level.parentLevel,
        allowedRoleTemplates: level.allowedRoleTemplates,
        allowedAssignmentTypes: level.allowedAssignmentTypes,
        defaultDashboards: level.defaultDashboards,
        policyScope: level.policyScope,
        primaryZimbabweStructure: true,
      },
    }),
  ),
  ...ZW_ORG_STRUCTURES.map((org) =>
    entry({
      id: `zos-${org.code}`,
      code: org.code,
      displayName: org.displayName,
      description: org.description,
      category: "organisational_structure",
      ownerService: org.level === "marketplace" ? "msika" : org.level === "registry" ? "tshepo" : "tuso",
      metadata: {
        level: org.level,
        parentStructure: org.parentStructure,
        primaryZimbabweStructure: true,
      },
    }),
  ),
];

writeSeed("zimbabwe-mohcc", bundle("zimbabwe-mohcc", "Zimbabwe MoHCC Administrative Levels and Organisational Structures", zimbabweMohccEntries));

const regulatoryInstitutionEntries = [
  ...REGULATOR_ORGANISATION_TYPES.map((code) =>
    entry({
      id: `rot-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "regulator_organisation_type",
      ownerService: "tshepo",
      metadata: { genericTypeOnly: code === "professional_council", notRealInstitution: code === "professional_council" },
    }),
  ),
  ...ZIMBABWE_REGULATORY_ORGANISATIONS.map((org) =>
    entry({
      id: `rio-${org.code}`,
      code: org.code,
      displayName: org.officialName,
      description: `${org.abbreviation} — real Zimbabwe health professions regulatory body`,
      category: "regulatory_organisation",
      ownerService: "tshepo",
      auditSensitivity: org.auditSensitivity,
      policyMapping: org.policyMapping,
      metadata: {
        organisationType: org.organisationType,
        officialName: org.officialName,
        abbreviation: org.abbreviation,
        aliases: org.aliases,
        regulatoryDomain: org.regulatoryDomain,
        registryOrWorkflowImpacted: org.registryOrWorkflowImpacted,
        allowedWorkspaces: org.defaultWorkspaces,
        defaultRoles: org.defaultRoles,
        scopeType: org.scopeType,
        dataAccessLimits: org.dataAccessLimits,
        isRealZimbabweInstitution: true,
      },
    }),
  ),
  ...DEPRECATED_FAKE_REGULATOR_CODES.map((code) =>
    entry({
      id: `rio-deprecated-${code}`,
      code,
      displayName: titleCase(code),
      category: "regulatory_organisation",
      status: "deprecated",
      ownerService: "tshepo",
      metadata: { deprecationReason: "Not a real Zimbabwe institution; use Medical Rehabilitation Practitioners Council (MRPC) or statutory council seeds", isRealZimbabweInstitution: false },
    }),
  ),
];

writeSeed("regulatory-institutions", bundle("regulatory-institutions", "Zimbabwe Health Professions Regulatory Bodies", regulatoryInstitutionEntries));

const organisationGovernanceEntries = [
  ...ORGANISATION_TYPES.map((code) =>
    entry({
      id: `ot-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "organisation_type",
      ownerService: code.includes("marketplace") || code.includes("vendor") || code.includes("supplier") ? "msika" : "tuso",
      metadata: { multiOrganisationGovernance: true },
    }),
  ),
  ...ORGANISATION_TRUST_TIERS.map((tier) =>
    entry({
      id: `ott-${tier.code.replace(/_/g, "-")}`,
      code: tier.code,
      displayName: tier.displayName,
      category: "organisation_trust_tier",
      ownerService: "tshepo",
      auditSensitivity: tier.level >= 7 ? "critical" : tier.level >= 4 ? "high" : "medium",
      metadata: { tierLevel: tier.level },
    }),
  ),
  ...ORGANISATION_LIFECYCLE_STATES.map((code) => {
    let policyMapping = "impilo.organisation.active";
    if (["suspended", "revoked", "blacklisted", "offboarded"].includes(code)) policyMapping = "impilo.organisation.block";
    if (code === "sandbox_only") policyMapping = "impilo.organisation.sandbox_only";
    if (["production_access_pending", "contract_pending", "data_sharing_pending"].includes(code)) policyMapping = "impilo.organisation.pending";
    return entry({
      id: `ols-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "organisation_lifecycle_state",
      ownerService: "tuso",
      policyMapping,
    });
  }),
  ...ORGANISATION_ACCESS_ENVIRONMENTS.map((code) =>
    entry({
      id: `oae-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "organisation_access_environment",
      ownerService: "tshepo",
      policyMapping: code === "sandbox" ? "impilo.organisation.sandbox_only" : code.includes("production") ? "impilo.organisation.production" : "impilo.organisation.environment",
    }),
  ),
  ...ORGANISATION_LINKED_USER_TYPES.map((code) =>
    entry({
      id: `olut-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "organisation_linked_user_type",
      ownerService: "workforce-governance",
      metadata: { requiresOrganisationAssignment: code !== "organisation_owner" },
    }),
  ),
  entry({
    id: "org-registry-schema-v1",
    code: "organisation_registry_schema_v1",
    displayName: "Organisation Registry Schema v1",
    category: "organisation_registry_schema",
    ownerService: "tuso",
    metadata: { fields: ORGANISATION_REGISTRY_FIELDS, version: "1.0.0" },
  }),
  ...EXEMPLAR_ORGANISATIONS.map((org) =>
    entry({
      id: `org-${org.code}`,
      code: org.code,
      displayName: org.legalName,
      description: org.tradingName ?? org.legalName,
      category: "organisation_registry_record",
      ownerService: org.sourceRegistry ?? "tuso",
      auditSensitivity: org.auditSensitivity ?? "high",
      policyMapping: "impilo.organisation",
      metadata: {
        ...org,
        registrySchema: "organisation_registry_schema_v1",
        multiOrganisationGovernance: true,
      },
    }),
  ),
];

writeSeed("organisation-governance", bundle("organisation-governance", "Multi-Organisation Registry, Trust Tiers and External Actor Governance", organisationGovernanceEntries));

const hscWorkforceEntries = [
  entry({
    id: "hsc-org-record",
    code: HSC_ORGANISATION.code,
    displayName: HSC_ORGANISATION.officialName,
    description: `${HSC_ORGANISATION.abbreviation} — public-sector workforce governance authority`,
    category: "hsc_organisation",
    ownerService: "workforce-governance",
    auditSensitivity: "critical",
    policyMapping: "impilo.hsc",
    metadata: {
      ...HSC_ORGANISATION,
      isRealZimbabweInstitution: true,
      notMoHcc: true,
      notProfessionalCouncil: true,
      notGenericRegulator: true,
    },
  }),
  ...PUBLIC_SECTOR_EMPLOYMENT_STATUSES.map((code) =>
    entry({
      id: `psem-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "public_sector_employment_status",
      ownerService: "workforce-governance",
      policyMapping: ["suspended_from_employment", "dismissed", "not_employed_public_sector", "under_disciplinary_review"].includes(code)
        ? "impilo.hsc.block"
        : "impilo.hsc",
    }),
  ),
  ...HSC_WORKFORCE_GOVERNANCE_ROLES.map((code) =>
    entry({
      id: `hsc-role-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "hsc_workforce_governance_role",
      ownerService: "workforce-governance",
      policyMapping: "impilo.hsc",
      auditSensitivity: code.includes("administrator") ? "critical" : "high",
      metadata: {
        hscOrganisation: HSC_ORGANISATION.code,
        isRealZimbabweInstitutionRole: true,
        notClinicalByDefault: true,
        notSystemSuperuser: true,
      },
    }),
  ),
];

writeSeed("hsc-workforce-governance", bundle("hsc-workforce-governance", "Health Service Commission Workforce Governance", hscWorkforceEntries));

const vashandiWorkforceEntries = [
  ...WORKFORCE_STATUSES.map((code) =>
    entry({
      id: `vws-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "workforce_status",
      ownerService: "vashandi-workforce",
      policyMapping: ["suspended", "dismissed", "offboarded", "under_review"].includes(code)
        ? "impilo.vashandi.block"
        : "impilo.vashandi",
    }),
  ),
  ...VASHANDI_ASSIGNMENT_TYPES.map((code) =>
    entry({
      id: `vat-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "vashandi_assignment_type",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...VASHANDI_ASSIGNMENT_STATUSES.map((code) => {
    const allow = ["active", "approved"].includes(code);
    return entry({
      id: `vas-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "vashandi_assignment_status",
      ownerService: "vashandi-workforce",
      policyMapping: allow ? "impilo.vashandi.allow" : "impilo.vashandi.block",
    });
  }),
  ...ROSTER_TYPES.map((code) =>
    entry({
      id: `vrt-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "roster_type",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...SHIFT_STATUSES.map((code) =>
    entry({
      id: `vss-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "shift_status",
      ownerService: "vashandi-workforce",
      policyMapping: ["checked_in", "confirmed", "scheduled"].includes(code) ? "impilo.vashandi.allow" : "impilo.vashandi",
    }),
  ),
  ...ATTENDANCE_EVENT_TYPES.map((code) =>
    entry({
      id: `vaet-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "attendance_event_type",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...CHECK_IN_MODES.map((code) =>
    entry({
      id: `vcm-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "check_in_mode",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...LEAVE_TYPES.map((code) =>
    entry({
      id: `vlt-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "leave_type",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...ACCESS_RISK_TYPES.map((code) =>
    entry({
      id: `vart-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "access_risk_type",
      ownerService: "vashandi-workforce",
      auditSensitivity: "high",
      policyMapping: "impilo.vashandi",
    }),
  ),
  ...VASHANDI_WORKFORCE_ROLES.map((code) =>
    entry({
      id: `vrole-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "vashandi_workforce_role",
      ownerService: "vashandi-workforce",
      policyMapping: "impilo.vashandi",
      auditSensitivity: code.includes("admin") ? "critical" : "high",
      metadata: {
        operationalWorkforceRole: true,
        notClinicalByDefault: true,
        notSystemSuperuser: true,
      },
    }),
  ),
];

writeSeed("vashandi-workforce", bundle("vashandi-workforce", "Vashandi Operational Workforce", vashandiWorkforceEntries));

// ── Work context ──────────────────────────────────────────────────────────────
const workContextTypes = [
  "facility_clinical", "facility_administration", "facility_operations", "virtual_care",
  "telemedicine_triage", "community_outreach", "mobile_clinic", "public_health", "public_health_inspection",
  "programme_support", "above_facility_management", "above_facility_support", "nursing",
  "training_mentorship", "emergency_response", "laboratory_services", "pharmacy_services",
  "radiology_imaging", "billing_finance", "health_information_records", "supply_chain_logistics",
  "registry_governance", "regulatory_oversight", "system_administration", "marketplace_operations",
  "integration_partner_operations", "audit_oversight", "local_authority_health",
  "public_sector_workforce_governance",
];
const workplaceTypes = [
  "central_hospital", "provincial_hospital", "district_hospital", "general_hospital",
  "mission_hospital", "rural_health_centre", "clinic", "polyclinic", "private_hospital",
  "private_clinic", "laboratory", "pharmacy", "imaging_centre", "blood_bank", "warehouse",
  "public_health_office", "ministry_office", "district_health_office", "provincial_health_office",
  "national_office", "training_institution", "call_centre", "telemedicine_hub",
  "virtual_service_point", "mobile_clinic", "outreach_site", "community_post",
  "public_health_place", "inspection_site", "port_of_entry", "marketplace_organisation",
  ...MUNICIPAL_ORGANISATION_TYPES,
  ...MADI_ORGANISATION_TYPES,
  "vendor_office", "integration_partner_environment", "sandbox_environment",
];
const workspaceTypes = [
  "personal_health", "my_professional", "fundo_professional_learning",
  "facility_dashboard", "outpatient", "inpatient", "emergency", "theatre", "maternity",
  "facility_department_dashboard",
  "antenatal_care", "family_planning", "immunisation", "pharmacy", "laboratory", "radiology",
  "blood_bank", "rehabilitation", "mental_health", "dental", "nutrition", "records",
  "client_registration", "billing", "claims", "stock_inventory", "facility_admin",
  "facility_staff_management", "facility_reports", "facility_telemedicine_coordination",
  "facility_training_compliance", "telemedicine_pool", "telemedicine_triage",
  "telemedicine_specialist_review", "community_outreach", "mobile_clinic",
  "programme_dashboard", "programme_support", "above_site_dashboard", "above_site_support",
  "public_health_inspection", "emergency_response", "training_delivery", "mentorship_supervision",
  "provider_registry_admin", "client_registry_admin", "facility_registry_admin",
  "terminology_registry_admin", "product_service_registry_admin",
  "public_health_place_registry_admin", "geography_jurisdiction_admin",
  "trust_console", "policy_console", "audit_console", "integration_console", "system_admin_console",
  "marketplace_vendor_portal", "marketplace_regulator_review", "marketplace_product_catalogue",
  "marketplace_contract_management", "marketplace_sandbox_certification",
  "marketplace_api_management", "marketplace_compliance_monitoring",
  ...ORGANOGRAM_WORKSPACES.map((w) => w.code),
  ...REGULATOR_WORKSPACES.map((w) => w.code),
  ...MUNICIPAL_WORKSPACES.map((w) => w.code),
  ...MADI_WORKSPACES.map((w) => w.code),
  ...MANAGEMENT_WORKSPACES.map((w) => w.code),
  ...MULTI_ORG_MANAGEMENT_WORKSPACES.map((w) => w.code),
  ...HSC_WORKSPACES.map((w) => w.code),
  ...HSC_MANAGEMENT_WORKSPACES.map((w) => w.code),
  ...VASHANDI_WORKSPACES.map((w) => w.code),
  "varapi_provider_worker_registry_governance", "vito_client_registry_governance",
  "tuso_facility_registry_governance", "zibo_terminology_registry_governance",
  "msika_product_service_registry_governance", "indawo_public_health_places_governance",
  "ndila_geography_jurisdiction_governance", "tshepo_trust_context_governance",
  "professional_council_review", "facility_licensing_review", "medicines_product_review",
  "laboratory_accreditation_review", "training_cpd_accreditation",
  "public_health_inspection_review", "marketplace_certification_review",
];
const assignmentTypes = [
  "facility_assignment", "department_assignment", "unit_assignment", "workspace_assignment",
  "telemedicine_pool_assignment", "community_outreach_assignment", "mobile_clinic_assignment",
  "programme_assignment", "above_site_assignment", "public_health_place_assignment",
  "emergency_response_assignment", "training_mentor_assignment", "registry_governance_assignment",
  "regulatory_assignment", "marketplace_organisation_assignment", "marketplace_product_assignment",
  "system_admin_assignment", "support_assignment", "integration_partner_assignment",
];
const assignmentStatuses = [
  "draft", "pending_approval", "pending_assumption_of_duty", "active", "active_temporary",
  "active_restricted", "suspended", "ended", "expired", "revoked", "rejected", "transferred",
  "under_review",
];
const roleTemplateCategories = [
  "citizen_support", "clinical_service_delivery", "nursing", "medical", "pharmacy",
  "laboratory", "radiology", "records_information", "billing_finance", "facility_management",
  "facility_administration", "telemedicine", "community_outreach", "programme_management",
  "above_site_management", "public_health", "training_mentorship", "registry_governance",
  "regulatory_oversight", "marketplace_operations", "system_administration", "security_trust",
  "audit_compliance", "integration_support", "nompilo_operations", "emergency_response",
  "governance", "curative_services", "support_services", "digital_health", "executive_leadership",
  "municipal_health", "madi_blood_service", "madi_clinical_transfusion", "madi_haemovigilance",
  "hsc_workforce_governance",
  "vashandi_workforce_operations",
];

const workContextEntries = [
  ...workContextTypes.map((code) =>
    entry({
      id: `wct-${code}`,
      code,
      displayName: titleCase(code),
      category: "approved_work_context_type",
      ownerService: code.includes("marketplace") ? "msika" : code.includes("registry") ? "tshepo" : "workforce-governance",
      standardMapping: { fhir: "PractitionerRole" },
    }),
  ),
  ...workplaceTypes.map((code) =>
    entry({
      id: `wpt-${code}`,
      code,
      displayName: titleCase(code),
      category: "workplace_place_type",
      ownerService: code.includes("marketplace") || code.includes("vendor") || code.includes("sandbox") ? "msika" : code.includes("public_health") ? "indawo" : "tuso",
      standardMapping: { fhir: "Location", openHie: "FacilityRegistry" },
    }),
  ),
  ...workspaceTypes.map((code) => {
    let category = "workspace_type";
    if (code.startsWith("vashandi.")) category = "workspace_type";
    else if (code.endsWith("_governance") || code.endsWith("_review")) category = code.includes("registry") || code.includes("governance") ? "registry_owner_workspace" : "regulator_workspace";
    const organogramWs = ORGANOGRAM_WORKSPACES.find((w) => w.code === code);
    const regulatorWs = REGULATOR_WORKSPACES.find((w) => w.code === code);
    const municipalWs = MUNICIPAL_WORKSPACES.find((w) => w.code === code);
    const madiWs = MADI_WORKSPACES.find((w) => w.code === code);
    const mgmtWs = MANAGEMENT_WORKSPACES.find((w) => w.code === code);
    const multiMgmtWs = MULTI_ORG_MANAGEMENT_WORKSPACES.find((w) => w.code === code);
    const hscWs = HSC_WORKSPACES.find((w) => w.code === code);
    const hscMgmtWs = HSC_MANAGEMENT_WORKSPACES.find((w) => w.code === code);
    const vashandiWs = VASHANDI_WORKSPACES.find((w) => w.code === code);
    const scoped = regulatorWs ?? municipalWs ?? madiWs ?? vashandiWs ?? hscMgmtWs ?? hscWs ?? multiMgmtWs ?? mgmtWs ?? organogramWs;
    if (regulatorWs?.category) category = regulatorWs.category;
    if (mgmtWs || multiMgmtWs || hscMgmtWs) category = "management_workspace";
    if (hscWs && !hscMgmtWs) category = "workspace_type";
    return entry({
      id: `wst-${code}`,
      code,
      displayName: scoped?.displayName ?? titleCase(code),
      category,
      ownerService: code.startsWith("vashandi.")
        ? "vashandi-workforce"
        : code.includes("marketplace")
          ? "msika"
          : code.includes("registry") || code.includes("trust")
            ? "tshepo"
            : code.includes("madi") || code.includes("blood")
              ? "blood-bank-service"
              : code.startsWith("hsc_")
                ? "workforce-governance"
                : "workforce-governance",
      policyMapping: code.startsWith("vashandi.") ? "impilo.vashandi" : code.startsWith("hsc_") ? "impilo.hsc" : undefined,
      metadata: scoped
        ? {
            scopeLevel: scoped.level ?? scoped.scope,
            scopedManagementLabel: multiMgmtWs?.label ?? mgmtWs?.label,
            managementSection: multiMgmtWs?.section,
            organogramWorkspace: !!organogramWs,
            managementWorkspace: !!(mgmtWs || multiMgmtWs),
          }
        : undefined,
    });
  }),
  ...assignmentTypes.map((code) =>
    entry({ id: `at-${code}`, code, displayName: titleCase(code), category: "assignment_type", ownerService: "workforce-governance" }),
  ),
  ...assignmentStatuses.map((code) => {
    const allow = ["active", "active_temporary", "active_restricted"].includes(code);
    const limited = code === "active_restricted";
    return entry({
      id: `as-${code}`,
      code,
      displayName: titleCase(code),
      category: "assignment_status",
      ownerService: "workforce-governance",
      policyMapping: allow ? (limited ? "impilo.work.limited" : "impilo.work.allow") : "impilo.work.block",
    });
  }),
  ...roleTemplateCategories.map((code) =>
    entry({ id: `rtc-${code}`, code, displayName: titleCase(code), category: "role_template_category", ownerService: "workforce-governance" }),
  ),
];

writeSeed("work-context", bundle("work-context", "Work Context, Workplace, Workspace, Assignments", workContextEntries));

// ── Permissions ─────────────────────────────────────────────────────────────
const permissionDomains = [
  "personal", "professional", "work", "facility", "facility.staff", "facility.admin",
  "facility.clinical", "facility.records", "facility.billing", "facility.pharmacy",
  "facility.lab", "facility.radiology", "facility.reports", "clinical", "pharmacy", "lab",
  "radiology", "billing", "telemedicine", "outreach", "programme", "above_site",
  "public_health", "registry", "registry.provider", "registry.client", "registry.facility",
  "registry.terminology", "registry.product_service", "registry.public_health_place",
  "registry.geography", "marketplace", "marketplace.organisation", "marketplace.product",
  "marketplace.contract", "marketplace.api", "marketplace.sandbox", "marketplace.compliance",
  "regulator", "policy", "audit", "nompilo", "integration", "system", "security", "trust",
  "consent", "break_glass",
  ...MADI_PERMISSION_DOMAINS,
  "hsc", "hsc.workforce", "hsc.establishment", "hsc.posts", "hsc.appointments", "hsc.transfers",
  "hsc.promotions", "hsc.discipline", "hsc.posting", "hsc.employment", "hsc.registry", "hsc.audit",
  "hsc.scope", "hsc.users",
  "vashandi", "vashandi.workforce", "vashandi.assignments", "vashandi.rosters", "vashandi.shifts",
  "vashandi.attendance", "vashandi.leave", "vashandi.access_risk", "vashandi.analytics",
  "vashandi.imports", "vashandi.hsc_postings", "vashandi.organisation_workforce", "vashandi.emergency_surge",
];
const permissions = [
  "personal.profile.view", "personal.profile.update", "personal.records.view",
  "personal.appointments.manage", "personal.telemedicine.request", "personal.prescriptions.view",
  "personal.payments.manage", "personal.consent.manage", "personal.dependants.manage",
  "professional.profile.view", "professional.profile.update_request", "professional.credentials.view",
  "professional.licence.view", "professional.cpd.view", "professional.status.view",
  "professional.assignment.request", "professional.fundo.access",
  "work.context.enter", "work.context.switch", "work.tasks.view", "work.queue.view", "work.assignment.view",
  "facility.dashboard.view", "facility.client.search", "facility.client.register_assisted",
  "facility.queue.manage", "facility.department.view", "facility.department.manage",
  "facility.staff.view", "facility.staff.assign", "facility.staff.suspend_local_access",
  "facility.staff.end_assignment", "facility.reports.view", "facility.audit.view", "facility.config.manage",
  "clinical.encounter.create", "clinical.encounter.update", "clinical.notes.write",
  "clinical.orders.create", "clinical.prescriptions.create", "clinical.referrals.create",
  "clinical.admissions.manage", "clinical.discharge.manage", "clinical.results.review",
  "pharmacy.prescriptions.view", "pharmacy.dispense", "pharmacy.stock.view", "pharmacy.stock.adjust",
  "pharmacy.stock.adjust.approve", "pharmacy.orders.create", "pharmacy.orders.approve", "pharmacy.staff.manage",
  "lab.orders.view", "lab.specimen.collect", "lab.results.enter", "lab.results.validate", "lab.results.release",
  "telemedicine.pool.join", "telemedicine.consult.accept", "telemedicine.triage.perform",
  "telemedicine.encounter.document", "telemedicine.specialist.review", "telemedicine.supervise",
  "telemedicine.settings.manage",
  "registry.provider.view", "registry.provider.create_draft", "registry.provider.verify",
  "registry.provider.approve", "registry.provider.suspend", "registry.provider.revoke",
  "registry.provider.link_health_id", "registry.facility.view", "registry.facility.create",
  "registry.facility.approve", "registry.facility.update", "registry.client.resolve_duplicate",
  "registry.terminology.manage", "registry.product_service.manage", "registry.public_health_place.manage",
  "registry.geography.manage",
  "marketplace.organisation.register", "marketplace.organisation.verify", "marketplace.product.submit",
  "marketplace.product.review", "marketplace.product.approve", "marketplace.contract.manage",
  "marketplace.api.request_sandbox", "marketplace.api.approve_production", "marketplace.compliance.view",
  "marketplace.compliance.audit", "marketplace.suspend", "marketplace.revoke",
  "regulator.review", "regulator.approve", "regulator.reject", "regulator.suspend", "regulator.revoke",
  "regulator.renew", "regulator.inspect", "regulator.certify", "regulator.audit",
  "system.users.view", "system.roles.manage", "system.config.manage", "security.sessions.view",
  "security.mfa.manage", "trust.context.inspect", "trust.role_templates.manage",
  "policy.bundle.view", "policy.bundle.publish", "audit.logs.view", "integration.api_keys.manage",
  "nompilo.knowledge.manage", "nompilo.actions.approve", "break_glass.authorize", "break_glass.audit",
  ...MADI_PERMISSIONS,
  ...HSC_PERMISSIONS,
  ...VASHANDI_PERMISSIONS,
];

function permDomain(code) {
  const parts = code.split(".");
  if (parts.length <= 2) return parts[0];
  return parts.slice(0, 2).join(".");
}

const permissionEntries = [
  ...permissionDomains.map((code) =>
    entry({ id: `dom-${code.replace(/\./g, "-")}`, code, displayName: titleCase(code.replace(/\./g, " ")), category: "permission_domain", ownerService: "tshepo" }),
  ),
  ...permissions.map((code) => {
    const domain = permDomain(code);
    const pkg = code.startsWith("registry.") ? "impilo.registry"
      : code.startsWith("marketplace.") ? "impilo.marketplace"
      : code.startsWith("regulator.") ? "impilo.regulator"
      : code.startsWith("clinical.") ? "impilo.work"
      : code.startsWith("personal.") ? "impilo.personal"
      : code.startsWith("professional.") ? "impilo.professional"
      : code.startsWith("work.") ? "impilo.work"
      : code.startsWith("break_glass.") ? "impilo.break_glass"
      : code.startsWith("madi.") ? "impilo.madi"
      : code.startsWith("hsc.") ? "impilo.hsc"
      : code.startsWith("vashandi.") ? "impilo.vashandi"
      : code.startsWith("system.") || code.startsWith("security.") || code.startsWith("trust.") || code.startsWith("policy.") ? "impilo.system_admin"
      : "impilo.work";
    return entry({
      id: `perm-${code.replace(/\./g, "-")}`,
      code,
      displayName: titleCase(code.replace(/\./g, " ")),
      category: "permission",
      ownerService: "tshepo",
      policyMapping: pkg,
      metadata: { domain },
      auditSensitivity: code.includes("break_glass") || code.includes("registry.provider") ? "high" : "medium",
    });
  }),
];

writeSeed("permissions", bundle("permissions", "Permission Domains and Standard Permissions", permissionEntries));

// ── Governance / marketplace ─────────────────────────────────────────────────
const registryGovernanceRoles = [
  "provider_registry_owner", "provider_registry_steward", "provider_registry_approver",
  "provider_registry_data_quality_officer", "client_registry_owner", "client_registry_steward",
  "client_registry_resolution_officer", "facility_registry_owner", "facility_registry_steward",
  "facility_registry_approver", "terminology_registry_owner", "terminology_registry_steward",
  "product_service_registry_owner", "product_service_registry_steward",
  "public_health_place_registry_steward", "geography_jurisdiction_steward",
];
const regulatorRoles = [
  "professional_council_regulator", "facility_licensing_regulator", "medicines_products_regulator",
  "laboratory_accreditation_regulator", "training_cpd_accreditor", "public_health_regulator",
  "marketplace_certification_reviewer", "compliance_inspector", "regulatory_auditor",
];
const workplaceManagerRoles = [
  "facility_administrator", "facility_staff_manager", "medical_superintendent", "hospital_manager",
  "matron", "head_of_department_clinical", "chief_pharmacist", "records_supervisor",
  "laboratory_supervisor", "radiology_supervisor", "billing_supervisor",
  "facility_telemedicine_coordinator", "outreach_team_lead", "programme_manager",
  "district_medical_officer", "district_nursing_officer", "provincial_medical_director",
  "sister_in_charge", "sister_in_charge_community", "marketplace_organisation_admin",
];
const systemAdminRoles = [
  "national_platform_administrator", "provincial_platform_administrator", "district_platform_administrator",
  "facility_system_administrator", "programme_system_administrator", "registry_system_administrator",
  "marketplace_system_administrator", "integration_administrator", "keycloak_administrator",
  "tshepo_administrator", "opa_policy_administrator", "audit_administrator", "security_administrator",
  "helpdesk_supervisor", "support_agent",
];
const marketplaceParticipantTypes = [
  "vendor", "supplier", "manufacturer", "distributor", "private_pharmacy", "private_laboratory",
  "insurer", "payment_provider", "logistics_provider", "device_manufacturer", "iot_device_vendor",
  "training_content_provider", "telemedicine_partner", "ai_model_provider", "software_integration_partner",
  "maintenance_contractor", "accredited_service_provider", "ngo_partner", "donor_partner", "research_partner",
];
const marketplacePipelineStates = [
  "organisation_draft", "submitted", "pending_verification", "verified_organisation",
  "pending_regulatory_review", "regulatory_approved", "contract_pending", "contract_active",
  "sandbox_access_requested", "sandbox_access_granted", "certification_testing", "certification_passed",
  "production_access_pending", "production_access_granted", "active", "active_restricted",
  "suspended", "revoked", "expired", "offboarded",
];
const marketplaceRoles = [
  "marketplace_organisation_admin", "marketplace_vendor_user", "marketplace_product_manager",
  "marketplace_contract_manager", "marketplace_api_developer", "marketplace_sandbox_tester",
  "marketplace_compliance_officer", "marketplace_support_user", "marketplace_finance_user",
  "marketplace_regulatory_liaison",
];

const governanceEntries = [
  ...registryGovernanceRoles.map((code) =>
    entry({ id: `rgr-${code}`, code, displayName: titleCase(code), category: "registry_governance_role", ownerService: "tshepo", auditSensitivity: "high", policyMapping: "impilo.registry" }),
  ),
  ...regulatorRoles.map((code) => {
    const deprecated = DEPRECATED_GENERIC_REGULATOR_ROLES.includes(code);
    return entry({
      id: `reg-${code}`,
      code,
      displayName: titleCase(code),
      category: "regulator_role",
      status: deprecated ? "deprecated" : "active",
      ownerService: "tshepo",
      policyMapping: "impilo.regulator",
      metadata: deprecated
        ? { mapsTo: DEPRECATED_GENERIC_REGULATOR_ROLE_MAPS[code], deprecationReason: "Use real Zimbabwe council role templates (MDPC, NC, PC, etc.)" }
        : undefined,
    });
  }),
  ...workplaceManagerRoles.map((code) =>
    entry({ id: `wm-${code}`, code, displayName: titleCase(code), category: "workplace_manager_role", ownerService: "workforce-governance", policyMapping: "impilo.facility" }),
  ),
  ...systemAdminRoles.map((code) => {
    const scope = code.split("_")[0];
    return entry({
      id: `adm-${code}`,
      code,
      displayName: titleCase(code),
      category: "system_admin_role",
      ownerService: "tshepo",
      auditSensitivity: "critical",
      policyMapping: "impilo.system_admin",
      metadata: { scopeLevel: scope, deniedAdminDomains: ["break_glass"] },
    });
  }),
  ...marketplaceParticipantTypes.map((code) =>
    entry({ id: `mpt-${code}`, code, displayName: titleCase(code), category: "marketplace_participant_type", ownerService: "msika" }),
  ),
  ...marketplacePipelineStates.map((code) => {
    let policyMapping = "impilo.marketplace.block_production";
    if (code === "sandbox_access_granted" || code === "certification_testing") policyMapping = "impilo.marketplace.sandbox_only";
    if (code === "production_access_granted" || code === "active" || code === "contract_active") policyMapping = "impilo.marketplace.production";
    if (["suspended", "revoked", "offboarded", "organisation_draft"].includes(code)) policyMapping = "impilo.marketplace.block";
    return entry({ id: `mps-${code}`, code, displayName: titleCase(code), category: "marketplace_access_pipeline_state", ownerService: "msika", policyMapping });
  }),
  ...marketplaceRoles.map((code) =>
    entry({ id: `mrole-${code}`, code, displayName: titleCase(code), category: "marketplace_role", ownerService: "msika", policyMapping: "impilo.marketplace" }),
  ),
  ...ZIMBABWE_REGULATORY_ORGANISATIONS.flatMap((org) =>
    org.defaultRoles.map((code) =>
      entry({
        id: `crr-${code.replace(/_/g, "-")}`,
        code,
        displayName: titleCase(code),
        category: "regulator_role",
        ownerService: "tshepo",
        policyMapping: org.policyMapping,
        auditSensitivity: org.auditSensitivity,
        metadata: {
          regulatoryOrganisation: org.code,
          isRealZimbabweInstitutionRole: true,
          dataAccessLimits: org.dataAccessLimits,
        },
      }),
    ),
  ),
  ...HSC_WORKFORCE_GOVERNANCE_ROLES.map((code) =>
    entry({
      id: `hscgr-${code.replace(/_/g, "-")}`,
      code,
      displayName: titleCase(code),
      category: "hsc_workforce_governance_role",
      ownerService: "workforce-governance",
      policyMapping: "impilo.hsc",
      auditSensitivity: code.includes("administrator") ? "critical" : "high",
      metadata: {
        hscOrganisation: HSC_ORGANISATION.code,
        isRealZimbabweInstitutionRole: true,
        notClinicalByDefault: true,
        notSystemSuperuser: true,
      },
    }),
  ),
];

writeSeed("governance-marketplace", bundle("governance-marketplace", "Registry, Regulator, Workplace Manager, Marketplace", governanceEntries));

// ── Role templates (Zimbabwe-native, extensible baseline) ───────────────────
function roleOpaPackage(category) {
  const map = {
    registry_governance: "impilo.registry",
    regulatory_oversight: "impilo.regulator",
    marketplace_operations: "impilo.marketplace",
    telemedicine: "impilo.telemedicine",
    system_administration: "impilo.system_admin",
    security_trust: "impilo.system_admin",
    audit_compliance: "impilo.audit",
    emergency_response: "impilo.work",
    above_site_management: "impilo.above_site",
    public_health: "impilo.public_health",
    community_outreach: "impilo.community_outreach",
    programme_management: "impilo.programme",
    governance: "impilo.above_site",
    executive_leadership: "impilo.above_site",
    curative_services: "impilo.work",
    support_services: "impilo.work",
    digital_health: "impilo.system_admin",
    municipal_health: "impilo.public_health",
    madi_blood_service: "impilo.madi",
    madi_clinical_transfusion: "impilo.madi",
    madi_haemovigilance: "impilo.madi",
    hsc_workforce_governance: "impilo.hsc",
    vashandi_workforce_operations: "impilo.vashandi",
  };
  return map[category] ?? "impilo.work";
}

function resolvePermissions(permsOrBaseline) {
  if (typeof permsOrBaseline === "string") {
    return (
      ROLE_PERMISSION_BASELINES[permsOrBaseline]
      ?? HSC_PERMISSION_BASELINE[permsOrBaseline]
      ?? VASHANDI_PERMISSION_BASELINE[permsOrBaseline]
      ?? ["work.context.enter"]
    );
  }
  return permsOrBaseline ?? ["work.context.enter"];
}

function restrictedPermissionsForRole(code) {
  const regulatorRestricted = [
    "clinical.encounter.create",
    "clinical.notes.write",
    "clinical.prescriptions.create",
    "clinical.orders.create",
    "facility.staff.assign",
    "facility.staff.end_assignment",
    "system.roles.manage",
    "break_glass.authorize",
  ];
  const municipalRestricted = [
    ...regulatorRestricted,
    "registry.provider.approve",
    "policy.bundle.publish",
  ];
  const madiNonClinicalRestricted = [
    "clinical.encounter.create",
    "clinical.notes.write",
    "clinical.prescriptions.create",
    "personal.records.view",
  ];
  if (code.startsWith("hsc_")) {
    return HSC_DENIED_ACTIONS;
  }
  if (code.startsWith("vashandi_")) {
    return VASHANDI_DENIED_ACTIONS;
  }
  if (
    code.startsWith("hpa_") ||
    code.startsWith("mdpc_") ||
    code.startsWith("mlcsc_") ||
    code.startsWith("ahpc_") ||
    code.startsWith("pc_") ||
    code.startsWith("nc_") ||
    code.startsWith("ehpc_") ||
    code.startsWith("mrpc_") ||
    code.startsWith("ntc_") ||
    code.endsWith("_registrar") ||
    code.endsWith("_licensing_officer") ||
    code.endsWith("_disciplinary_officer")
  ) {
    return code.startsWith("ntc_") ? [...regulatorRestricted, "clinical.admissions.manage"] : regulatorRestricted;
  }
  if (MUNICIPAL_ROLES.includes(code)) {
    return municipalRestricted;
  }
  if (MADI_ROLES.includes(code) && !["blood_ordering_clinician", "ward_transfusion_nurse", "facility_transfusion_focal_person"].includes(code)) {
    return madiNonClinicalRestricted;
  }
  const executiveCodes = new Set([
    "minister_of_health", "deputy_minister_of_health", "permanent_secretary", "staffing_officer",
    "chief_director_curative_services", "chief_director_public_health",
    "chief_director_health_systems_policy_compliance_coordination", "chief_director_support_services",
    "chief_director_officer", "director_head_office",
  ]);
  if (
    executiveCodes.has(code) ||
    code.startsWith("executive_assistant") ||
    code.startsWith("chief_director") ||
    code.startsWith("director_internal_audit") ||
    code.includes("internal_auditor") ||
    code === "internal_auditor" ||
    code === "auditor" ||
    code.endsWith("_auditor") ||
    code === "audit_assistant" ||
    code === "access_review_auditor" ||
    code === "break_glass_auditor"
  ) {
    return [
      "clinical.encounter.create",
      "clinical.notes.write",
      "clinical.prescriptions.create",
      "clinical.orders.create",
      "break_glass.authorize",
    ];
  }
  if (code.includes("facility_ict") || code.includes("ict_digital_health") || code.includes("impilo_")) {
    return ["clinical.encounter.create", "clinical.notes.write", "clinical.prescriptions.create"];
  }
  if (code === "national_platform_administrator") {
    return ["break_glass.authorize", "registry.provider.approve"];
  }
  if (code === "facility_administrator" || code === "facility_staff_manager") {
    return ["registry.provider.verify", "registry.provider.approve", "registry.provider.create_draft"];
  }
  if (code.includes("marketplace")) {
    return ["clinical.encounter.create", "clinical.notes.write", "registry.client.resolve_duplicate"];
  }
  if (code.includes("registry_steward") || code.includes("registry_owner")) {
    return ["break_glass.authorize", "clinical.encounter.create"];
  }
  return [];
}

const roleTemplateEntries = [
  ...ZW_ROLE_TEMPLATE_SPECS.map(
  ([code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel]) => {
    const zw = ZW_ROLE_PROFILES[code] ?? {};
    const hscDef = HSC_ROLE_DEFINITIONS[code];
    const perms = resolvePermissions(permsOrBaseline);
    const displayName = zw.displayName ?? titleCase(code);
    const isDeprecated = DEPRECATED_ROLE_TEMPLATES.includes(code);
    return entry({
      id: `rt-${code.replace(/_/g, "-")}`,
      code,
      displayName,
      category: "role_template",
      status: isDeprecated ? "deprecated" : "active",
      ownerService: roleCategory.includes("registry")
        ? "varapi"
        : roleCategory.includes("marketplace")
          ? "msika"
          : roleCategory === "hsc_workforce_governance"
            ? "workforce-governance"
          : "workforce-governance",
      policyMapping: roleOpaPackage(roleCategory),
      auditSensitivity: code.includes("break_glass") || code.startsWith("hsc_administrator")
        ? "critical"
        : roleCategory.includes("registry") || code.startsWith("hsc_")
          ? "high"
          : "medium",
      metadata: {
        roleTemplateId: code,
        roleCategory,
        displayName,
        abbreviation: zw.abbreviation ?? (code.startsWith("hsc_") ? "HSC" : undefined),
        aliases: zw.aliases,
        primaryZimbabweRole: zw.primaryZimbabweRole ?? false,
        policyScopeLevel: scopeLevel ?? zw.policyScopeLevel,
        allowedContextTypes: contexts,
        allowedWorkspaces: workspaces,
        requiredCadres: cadres,
        defaultPermissions: perms,
        restrictedPermissions: restrictedPermissionsForRole(code),
        deniedActions: code.startsWith("hsc_") ? HSC_DENIED_ACTIONS : undefined,
        approvalRequirements: hscDef?.[3] ?? [],
        hscOrganisation: code.startsWith("hsc_") ? HSC_ORGANISATION.code : undefined,
        notClinicalByDefault: code.startsWith("hsc_") ? true : undefined,
        notSystemSuperuser: code.startsWith("hsc_") ? true : undefined,
        opaPolicyMapping: roleOpaPackage(roleCategory),
        canAssignRoles: [
          "facility_administrator",
          "facility_staff_manager",
          "chief_pharmacist",
          "nursing_manager",
          "matron",
          "district_medical_officer",
          "district_nursing_officer",
        ].includes(code),
        canManageLocalAccess: [
          "facility_administrator",
          "facility_staff_manager",
          "chief_pharmacist",
          "matron",
          "sister_in_charge",
        ].includes(code),
        canManageRegistry: code.includes("registry_owner") || code.includes("registry_steward"),
        canApproveMarketplace: code.includes("marketplace_certification"),
        canAccessSensitiveData:
          !code.includes("marketplace") &&
          !code.includes("records_clerk") &&
          !code.includes("vendor"),
        approvalStatus: "approved_baseline",
        catalogueVersion: VERSION,
        effectiveDate: "2026-06-10",
        extensible: true,
        governanceNote:
          "National MoHCC baseline catalogue entry; extensions require Tshepo Trust Console and registry governance approval",
      },
    });
  },
),
  ...VASHANDI_ROLE_TEMPLATE_SPECS.map(
    ([code, roleCategory, contexts, workspaces, cadres, permsOrBaseline, scopeLevel, approvalRequirements]) => {
      const vashandiDef = VASHANDI_ROLE_DEFINITIONS[code];
      const perms = resolvePermissions(permsOrBaseline);
      return entry({
        id: `rt-${code.replace(/_/g, "-")}`,
        code,
        displayName: titleCase(code),
        category: "role_template",
        status: "active",
        ownerService: "vashandi-workforce",
        policyMapping: "impilo.vashandi",
        auditSensitivity: code.includes("admin") ? "critical" : "high",
        metadata: {
          roleTemplateId: code,
          roleCategory,
          displayName: titleCase(code),
          policyScopeLevel: scopeLevel ?? vashandiDef?.[2],
          allowedContextTypes: contexts,
          allowedWorkspaces: workspaces,
          requiredCadres: cadres,
          defaultPermissions: perms,
          restrictedPermissions: VASHANDI_DENIED_ACTIONS,
          deniedActions: VASHANDI_DENIED_ACTIONS,
          approvalRequirements: approvalRequirements ?? vashandiDef?.[3] ?? [],
          operationalWorkforceRole: true,
          notClinicalByDefault: true,
          notSystemSuperuser: true,
          opaPolicyMapping: "impilo.vashandi",
          approvalStatus: "approved_baseline",
          catalogueVersion: VERSION,
          effectiveDate: "2026-06-19",
          extensible: true,
          governanceNote:
            "Vashandi operational workforce role; extensions require Tshepo Trust Console approval",
        },
      });
    },
  ),
];

writeSeed("role-templates", bundle("role-templates", "Standard Role Templates (Zimbabwe MoHCC Baseline)", roleTemplateEntries));

// ── Resolution, tabs, OPA, Nompilo, audit ───────────────────────────────────
const friendlyStates = [
  ["no_provider_id", "No Provider ID linked", "Link or register a Provider/Worker ID to access professional tools.", ["Register Provider ID", "Contact Support"], "TRUST.NO_PROVIDER_ID"],
  ["provider_pending_verification", "Provider verification pending", "Your Provider/Worker ID is awaiting verification.", ["View Status", "Contact Registry"], "TRUST.PROVIDER_PENDING"],
  ["provider_suspended", "Provider ID suspended", "Work access is restricted until your provider status is restored.", ["View Status", "Contact Support"], "TRUST.PROVIDER_SUSPENDED"],
  ["provider_expired", "Provider credentials expired", "Renew your licence or registration to restore work access.", ["View Credentials", "Open Fundo"], "TRUST.PROVIDER_EXPIRED"],
  ["provider_requires_update", "Provider profile requires update", "Update your professional profile to continue.", ["Update Profile"], "TRUST.PROVIDER_REQUIRES_UPDATE"],
  ["no_active_work_assignment", "No active work assignment", "You are verified as a provider/worker, but no active work assignment was found.", ["View Professional Profile", "Request Work Assignment"], "TRUST.NO_ACTIVE_ASSIGNMENT"],
  ["missing_required_training", "Required training missing", "This role requires additional training before access can be activated.", ["Open Fundo Learning"], "TRUST.MISSING_TRAINING"],
  ["licence_expired", "Licence expired", "Your professional licence has expired.", ["View Licence", "Contact Council"], "TRUST.LICENCE_EXPIRED"],
  ["context_not_allowed", "Work context not allowed", "You are not eligible for this work context.", ["Switch Context", "Contact Manager"], "TRUST.CONTEXT_NOT_ALLOWED"],
  ["role_not_allowed_for_cadre", "Role not allowed for cadre", "Your profession/cadre is not eligible for this role.", ["View Professional Profile"], "TRUST.ROLE_NOT_ALLOWED"],
  ["facility_assignment_required", "Facility assignment required", "Select or request a facility assignment to continue.", ["Request Assignment"], "TRUST.FACILITY_REQUIRED"],
  ["marketplace_pending_verification", "Marketplace verification pending", "Your organisation is awaiting verification.", ["View Status"], "TRUST.MARKETPLACE_PENDING"],
  ["marketplace_sandbox_only", "Sandbox access only", "Production access requires certification and approval.", ["Open Sandbox Portal"], "TRUST.MARKETPLACE_SANDBOX"],
  ["marketplace_contract_required", "Contract required", "An active contract is required for production operations.", ["View Contracts"], "TRUST.MARKETPLACE_CONTRACT"],
  ["registry_scope_required", "Registry scope required", "You need an assigned registry governance role.", ["Request Access"], "TRUST.REGISTRY_SCOPE"],
  ["regulator_scope_required", "Regulator scope required", "You need an assigned regulatory role for this workspace.", ["Request Access"], "TRUST.REGULATOR_SCOPE"],
  ["citizen_only_access", "Personal health account", "Your account is configured for personal health access only.", ["View My Health"], "TRUST.CITIZEN_ONLY"],
  ["access_request_pending", "Access request pending", "Your access request is awaiting approval.", ["View Request Status"], "TRUST.ACCESS_PENDING"],
  ["policy_denied", "Access denied by policy", "This action is not permitted in your current context.", ["Contact Support"], "TRUST.POLICY_DENIED"],
  ["device_context_requires_user_login", "Personal login required", "This device is linked to a facility, but you must still sign in as yourself.", ["Sign In"], "TRUST.DEVICE_LOGIN_REQUIRED"],
  ...ORGANISATION_FRIENDLY_RESOLUTION_STATES,
  ...HSC_FRIENDLY_RESOLUTION_STATES.map((s) => [s.code, s.displayName, s.description, s.allowedActions, s.policyKey]),
  ...VASHANDI_FRIENDLY_RESOLUTION_STATES.map((s) => [s.code, s.displayName, s.description, s.allowedActions, s.policyKey]),
];

const opaPackages = [
  "impilo.tabs", "impilo.personal", "impilo.professional", "impilo.work", "impilo.provider_worker",
  "impilo.work_assignment", "impilo.role_assignment", "impilo.facility", "impilo.telemedicine",
  "impilo.community_outreach", "impilo.programme", "impilo.above_site", "impilo.public_health",
  "impilo.registry", "impilo.regulator", "impilo.marketplace", "impilo.client_registration",
  "impilo.provider_registration", "impilo.nompilo", "impilo.consent", "impilo.audit",
  "impilo.system_admin", "impilo.break_glass", "impilo.madi", "impilo.organisation", "impilo.hsc",
  "impilo.vashandi",
];

const nompiloDomains = [
  "personal_health_support", "professional_profile_support", "work_navigation", "facility_operations",
  "clinical_support", "client_registration_support", "provider_staff_management_support",
  "telemedicine_support", "programme_support", "registry_support", "regulator_support",
  "marketplace_support", "system_support", "audit_support", "policy_support",
];

const auditEventTypes = [
  "identity.login.succeeded", "identity.login.failed", "identity.context.resolved",
  "identity.context.chooser.presented", "identity.context.selected", "identity.context.switched",
  "session.contract.issued", "provider.status.viewed", "provider.status.changed", "provider.verified",
  "provider.suspended", "provider.reactivated", "work.assignment.created", "work.assignment.approved",
  "work.assignment.assumed", "work.assignment.ended", "role.assignment.created", "role.assignment.changed",
  "role.assignment.revoked", "access.policy.allowed", "access.policy.denied", "registry.record.created",
  "registry.record.updated", "registry.record.approved", "registry.record.suspended", "regulator.review.performed",
  "marketplace.organisation.submitted", "marketplace.organisation.verified", "marketplace.product.submitted",
  "marketplace.product.approved", "marketplace.api.sandbox_granted", "marketplace.api.production_granted",
  "nompilo.action.suggested", "nompilo.action.executed", "break_glass.requested", "break_glass.authorized",
  "break_glass.used", "break_glass.reviewed",
];

const resolutionEntries = [
  ...friendlyStates.map(([code, title, explanation, actions, auditCode]) =>
    entry({
      id: `frs-${code}`,
      code,
      displayName: title,
      category: "friendly_resolution_state",
      ownerService: "tshepo",
      metadata: { userTitle: title, userFacingMessage: explanation, explanation, allowedActions: actions, auditCode },
    }),
  ),
  entry({ id: "tab-personal", code: "personal", displayName: "Personal", category: "session_tab", ownerService: "experience-bff", metadata: { route: "/my-health", requiredSource: "health_id", meaning: "Person as citizen/client/patient" } }),
  entry({ id: "tab-professional", code: "professional", displayName: "Professional", category: "session_tab", ownerService: "experience-bff", metadata: { route: "/my-professional", requiredSource: "verified_provider_worker_id", meaning: "Verified provider/worker" } }),
  entry({ id: "tab-work", code: "work", displayName: "Work", category: "session_tab", ownerService: "experience-bff", metadata: { route: "/work", requiredSource: "active_work_assignment", meaning: "Active service delivery or governance work" } }),
  ...opaPackages.map((code) =>
    entry({ id: `opa-${code.replace(/\./g, "-")}`, code, displayName: titleCase(code.replace("impilo.", "")), category: "opa_policy_package", ownerService: "tshepo", policyMapping: code }),
  ),
  ...nompiloDomains.map((code) =>
    entry({ id: `nompilo-${code}`, code, displayName: titleCase(code), category: "nompilo_action_domain", ownerService: "guidance", policyMapping: "impilo.nompilo" }),
  ),
  ...auditEventTypes.map((code) =>
    entry({ id: `audit-${code.replace(/\./g, "-")}`, code, displayName: titleCase(code.replace(/\./g, " ")), category: "audit_event_type", ownerService: "tshepo-audit", auditSensitivity: code.includes("break_glass") ? "critical" : code.includes("denied") ? "high" : "medium" }),
  ),
];

writeSeed("resolution-tabs-opa", bundle("resolution-tabs-opa", "Resolution States, Tabs, OPA, Nompilo, Audit", resolutionEntries));

console.log(`\nSeed catalogue generation complete (version ${VERSION}).`);
