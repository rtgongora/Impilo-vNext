/**
 * Organisation-context management workspace and work-access resolution.
 */

export interface OrganisationGovernanceInput {
  organisationId?: string | null;
  organisationType?: string | null;
  organisationName?: string | null;
  organisationTrustTier?: string | null;
  organisationLifecycleStatus?: string | null;
  organisationAccessEnvironment?: string | null;
  organisationDataScopes?: string[];
  organisationApiScopes?: string[];
  organisationCountry?: string | null;
  crossBorderAccessFlag?: boolean;
  crossBorderApproved?: boolean;
  userOrganisationRole?: string | null;
  activeOrganisationAssignment?: boolean;
  approvedWorkspaces?: string[];
  verificationStatus?: string | null;
  identityType?: string;
  researchProjectAssignment?: boolean;
  requiresProduction?: boolean;
}

const SOVEREIGN_ONLY = [
  "national_identity_governance",
  "national_organisation_registry",
  "national_system_operator_management",
  "mohcc_head_office_user_management",
  "national_platform_user_administration",
];

const MUNICIPAL_ONLY = [
  "municipal_user_management",
  "municipal_facility_staff_management",
  "municipal_public_health_team_management",
];

const PRIVATE_ONLY = ["private_facility_user_management", "private_clinician_assignment"];

const COUNCIL_ONLY = ["council_user_management", "professional_registration_review", "licence_status_management"];

const TYPE_DEFAULTS: Record<string, string[]> = {
  sovereign_public_owner: ["national_organisation_registry", "national_trust_console", "national_platform_user_administration"],
  ministry_department: ["mohcc_head_office_user_management", "national_policy_management"],
  provincial_medical_office: ["provincial_office_user_management", "public_facility_staff_management"],
  district_medical_office: ["district_office_user_management", "public_facility_staff_management"],
  public_facility: ["public_facility_staff_management", "facility_work_assignment", "facility_role_assignment"],
  city_health_department: ["municipal_organisation_management", "municipal_user_management", "municipal_facility_staff_management"],
  municipal_health_department: ["municipal_user_management", "municipal_public_health_team_management"],
  private_hospital: ["private_facility_user_management", "private_clinician_assignment", "private_non_clinical_staff_assignment"],
  private_clinic: ["private_facility_user_management", "private_clinician_assignment"],
  mission_facility: ["mission_facility_user_management", "mission_facility_staff_assignment"],
  health_professions_authority: ["regulator_organisation_management", "council_user_management", "regulatory_audit"],
  statutory_health_council: ["council_user_management", "licence_status_management", "disciplinary_status_management"],
  national_blood_transfusion_service: ["blood_service_organisation_management", "blood_service_user_management"],
  marketplace_organisation: ["marketplace_organisation_user_management", "marketplace_sandbox_access_management"],
  software_vendor: ["marketplace_api_developer_assignment", "marketplace_sandbox_access_management"],
  api_integration_partner: ["marketplace_api_developer_assignment", "marketplace_production_access_management"],
  insurer: ["payer_organisation_management", "payer_user_management", "claims_user_assignment"],
  medical_aid_society: ["payer_user_management", "eligibility_verification_user_assignment"],
  training_institution: ["training_institution_management", "training_provider_user_management"],
  foreign_research_partner: ["external_partner_user_management", "research_project_user_assignment", "deidentified_dataset_access"],
  non_zimbabwean_partner: ["external_partner_organisation_management", "cross_border_access_review", "time_limited_access_management"],
  health_service_commission: ["hsc_user_management", "hsc_scope_assignment", "hsc_public_sector_post_management", "hsc_public_sector_worker_status_management"],
};

export function resolveManagementWorkspacesForOrganisation(input: OrganisationGovernanceInput): {
  visible: string[];
  blocked: string[];
} {
  const organisationType = input.organisationType ?? undefined;
  if (!organisationType) return { visible: [], blocked: [] };

  const defaults = TYPE_DEFAULTS[organisationType] ?? [];
  const merged = [...new Set([...defaults, ...(input.approvedWorkspaces ?? [])])];
  const blocked = new Set<string>();

  const lifecycle = input.organisationLifecycleStatus ?? undefined;
  if (lifecycle === "suspended" || lifecycle === "offboarded" || lifecycle === "blacklisted") {
    return { visible: [], blocked: merged };
  }

  if (lifecycle === "sandbox_only" || input.organisationAccessEnvironment === "sandbox") {
    blocked.add("marketplace_production_access_management");
    blocked.add("national_platform_user_administration");
  }

  if (organisationType !== "sovereign_public_owner" && organisationType !== "ministry_department") {
    SOVEREIGN_ONLY.forEach((w) => blocked.add(w));
  }
  if (!["city_health_department", "municipal_health_department", "local_authority_health_department"].includes(organisationType)) {
    MUNICIPAL_ONLY.forEach((w) => blocked.add(w));
  }
  if (organisationType !== "private_hospital" && organisationType !== "private_clinic") {
    PRIVATE_ONLY.forEach((w) => blocked.add(w));
  }
  if (
    organisationType !== "health_professions_authority" &&
    organisationType !== "statutory_health_council" &&
    organisationType !== "professional_council"
  ) {
    COUNCIL_ONLY.forEach((w) => blocked.add(w));
  }
  if (input.userOrganisationRole === "read_only_observer") {
    merged.filter((w) => w.includes("assignment") || w.includes("management")).forEach((w) => blocked.add(w));
  }

  const visible = merged.filter((w) => !blocked.has(w));
  return { visible, blocked: [...blocked] };
}

const ORG_REQUIRED_IDENTITY_TYPES = new Set([
  "marketplace_actor",
  "regulator",
  "registry_owner",
  "system_admin",
  "support_user",
]);

function requiresOrganisationAssignment(input: OrganisationGovernanceInput): boolean {
  if (input.identityType === "citizen") return false;
  if (input.organisationId || input.organisationType) return true;
  return ORG_REQUIRED_IDENTITY_TYPES.has(input.identityType ?? "");
}

export function organisationBlocksWork(input: OrganisationGovernanceInput): string | undefined {
  const lifecycle = input.organisationLifecycleStatus ?? undefined;
  if (lifecycle === "suspended") return "organisation_suspended";
  if (lifecycle === "offboarded" || lifecycle === "blacklisted" || lifecycle === "revoked") return "organisation_offboarded";
  if (lifecycle === "expired") return "external_partner_access_expired";

  if (requiresOrganisationAssignment(input) && input.activeOrganisationAssignment === false) {
    return "organisation_user_not_assigned";
  }
  if (input.verificationStatus && input.verificationStatus !== "verified" && lifecycle !== "active") {
    return "organisation_not_verified";
  }
  if (input.organisationAccessEnvironment === "sandbox" && input.requiresProduction) {
    return "sandbox_only_access";
  }
  if (input.crossBorderAccessFlag && !input.crossBorderApproved) {
    return "cross_border_access_not_approved";
  }
  if (input.organisationType === "foreign_research_partner" && !input.researchProjectAssignment) {
    return "research_project_approval_required";
  }
  if (lifecycle === "contract_pending") {
    return "organisation_contract_required";
  }
  if (lifecycle === "data_sharing_pending") {
    return "organisation_data_sharing_required";
  }
  if (lifecycle === "production_access_pending") {
    return "production_access_not_approved";
  }
  return undefined;
}
