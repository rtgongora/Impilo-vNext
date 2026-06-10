/**
 * Multi-organisation identity, trust, assignment and external actor governance seeds.
 * MoHCC is sovereign custodian but not the only actor in Impilo vNext.
 */

export const MULTI_ORG_GOVERNANCE_NOTE =
  "Organisation-contextual user management — no generic User Management outside organisational scope.";

/** Canonical organisation registry field schema (metadata on registry records) */
export const ORGANISATION_REGISTRY_FIELDS = [
  "organisationId",
  "organisationType",
  "legalName",
  "tradingName",
  "country",
  "registrationNumber",
  "taxNumber",
  "regulatorRegistrationNumber",
  "licenceStatus",
  "parentOrganisationId",
  "ownershipType",
  "publicPrivateClassification",
  "operatingJurisdictions",
  "operatingFacilities",
  "operatingServices",
  "approvedWorkspaces",
  "approvedApiScopes",
  "approvedDataScopes",
  "approvedEnvironments",
  "contractStatus",
  "mouStatus",
  "dataSharingAgreementStatus",
  "regulatoryApprovalStatus",
  "riskRating",
  "trustTier",
  "verificationStatus",
  "lifecycleStatus",
  "effectiveDate",
  "expiryDate",
  "auditSensitivity",
  "sourceRegistry",
  "provenanceMetadata",
];

export const ORGANISATION_TYPES = [
  // Public / MoHCC
  "sovereign_public_owner", "ministry_department", "national_directorate", "provincial_medical_office",
  "district_medical_office", "public_facility", "central_hospital", "provincial_hospital", "district_hospital",
  "general_hospital", "rural_health_centre", "public_clinic", "public_health_office", "national_data_centre",
  "national_helpdesk",
  // Municipal
  "city_health_department", "municipal_health_department", "local_authority_health_department", "urban_local_authority",
  "rural_district_council_health_unit", "municipal_clinic", "municipal_polyclinic", "municipal_hospital",
  "city_infectious_diseases_hospital", "municipal_environmental_health_unit", "municipal_public_health_office",
  "municipal_ambulance_service",
  // Private / mission
  "private_hospital", "private_clinic", "private_laboratory", "private_pharmacy", "private_imaging_centre",
  "private_rehabilitation_centre", "private_ambulance_service", "mission_facility", "faith_based_health_provider",
  "not_for_profit_health_provider",
  // Regulators
  "health_professions_authority", "professional_council", "medicines_regulator", "research_ethics_authority",
  "facility_licensing_authority", "public_health_regulatory_authority", "statutory_health_council",
  // Public-sector workforce governance (HSC — distinct from MoHCC and councils)
  "health_service_commission",
  // Blood / Madi
  "national_blood_transfusion_service", "central_blood_bank", "regional_blood_bank", "hospital_blood_bank",
  "facility_blood_bank", "blood_collection_site", "blood_processing_centre", "haemovigilance_unit",
  // Marketplace / payers / suppliers
  "insurer", "medical_aid_society", "payment_provider", "claims_processor", "supplier", "manufacturer",
  "distributor", "logistics_provider", "device_vendor", "iot_device_vendor", "software_vendor",
  "api_integration_partner", "cloud_hosting_partner", "maintenance_contractor", "accredited_service_provider",
  "marketplace_organisation",
  // Training / research / partners
  "training_institution", "university", "research_institution", "ngo_implementing_partner", "development_partner",
  "donor_partner", "un_agency", "external_technical_partner", "non_zimbabwean_partner", "foreign_research_partner",
];

export const ORGANISATION_TRUST_TIERS = [
  { code: "tier_0_unverified", displayName: "Tier 0 — Unverified", level: 0 },
  { code: "tier_1_registered", displayName: "Tier 1 — Registered", level: 1 },
  { code: "tier_2_verified", displayName: "Tier 2 — Verified", level: 2 },
  { code: "tier_3_contract_active", displayName: "Tier 3 — Contract Active", level: 3 },
  { code: "tier_4_regulated_service_provider", displayName: "Tier 4 — Regulated Service Provider", level: 4 },
  { code: "tier_5_integrated_service_provider", displayName: "Tier 5 — Integrated Service Provider", level: 5 },
  { code: "tier_6_sovereign_public_operator", displayName: "Tier 6 — Sovereign Public Operator", level: 6 },
  { code: "tier_7_high_trust_regulator", displayName: "Tier 7 — High Trust Regulator", level: 7 },
  { code: "tier_7_high_trust_public_workforce_authority", displayName: "Tier 7 — High Trust Public Workforce Authority (HSC)", level: 7 },
  { code: "tier_8_system_operator", displayName: "Tier 8 — System Operator", level: 8 },
];

export const ORGANISATION_LIFECYCLE_STATES = [
  "draft", "submitted", "pending_verification", "verified", "pending_regulatory_review", "regulatory_approved",
  "contract_pending", "contract_active", "mou_pending", "mou_active", "data_sharing_pending", "data_sharing_active",
  "sandbox_only", "production_access_pending", "production_active", "active", "active_restricted", "suspended",
  "expired", "revoked", "offboarding", "offboarded", "blacklisted",
];

export const ORGANISATION_ACCESS_ENVIRONMENTS = [
  "none", "demo", "training", "sandbox", "staging", "production_limited", "production_full", "read_only",
  "aggregate_only", "deidentified_only", "identified_data_approved",
];

export const ORGANISATION_LINKED_USER_TYPES = [
  "organisation_owner", "organisation_administrator", "authorised_representative", "compliance_officer",
  "data_protection_contact", "technical_contact", "billing_contact", "contract_contact", "api_developer",
  "sandbox_tester", "production_operator", "support_user", "auditor", "regulatory_reviewer", "registry_steward",
  "facility_staff_manager", "clinical_staff", "non_clinical_staff", "partner_programme_user", "research_user",
  "read_only_observer", "external_partner_admin", "external_partner_user", "external_technical_integrator",
  "external_researcher", "external_support_user", "external_auditor",
];

/** Scoped management workspaces by organisation authority (sections A–K) */
export const MULTI_ORG_MANAGEMENT_WORKSPACES = [
  // A — National / sovereign
  { code: "national_identity_governance", displayName: "National Identity Governance", scope: "national", label: "National Identity Governance", section: "sovereign" },
  { code: "national_organisation_registry", displayName: "National Organisation Registry", scope: "national", label: "Organisation Registry", section: "sovereign" },
  { code: "national_trust_console", displayName: "National Trust Console", scope: "national", label: "National Trust Console", section: "sovereign" },
  { code: "national_role_template_management", displayName: "National Role Template Management", scope: "national", label: "Role Template Management", section: "sovereign" },
  { code: "national_policy_management", displayName: "National Policy Management", scope: "national", label: "National Policy Management", section: "sovereign" },
  { code: "national_registry_governance", displayName: "National Registry Governance", scope: "national", label: "Registry Governance", section: "sovereign" },
  { code: "national_platform_user_administration", displayName: "National Platform User Administration", scope: "national", label: "Platform User Administration", section: "sovereign" },
  { code: "national_system_operator_management", displayName: "National System Operator Management", scope: "national", label: "System Operator Management", section: "sovereign" },
  { code: "national_api_access_governance", displayName: "National API Access Governance", scope: "national", label: "API Access Governance", section: "sovereign" },
  { code: "national_audit_and_compliance", displayName: "National Audit and Compliance", scope: "national", label: "National Audit and Compliance", section: "sovereign" },
  // B — MoHCC workforce
  { code: "mohcc_head_office_user_management", displayName: "MoHCC Head Office User Management", scope: "national", label: "MoHCC Head Office Users", section: "mohcc" },
  { code: "provincial_office_user_management", displayName: "Provincial Office User Management", scope: "province", label: "Provincial Office Users", section: "mohcc" },
  { code: "district_office_user_management", displayName: "District Office User Management", scope: "district", label: "District Office Users", section: "mohcc" },
  { code: "public_facility_staff_management", displayName: "Public Facility Staff Management", scope: "facility", label: "Public Facility Staff", section: "mohcc" },
  // C — Municipal
  { code: "municipal_organisation_management", displayName: "Municipal Organisation Management", scope: "local_authority", label: "Municipal Organisation Management", section: "municipal" },
  { code: "municipal_user_management", displayName: "Municipal User Management", scope: "local_authority", label: "Municipal Users", section: "municipal" },
  { code: "municipal_facility_staff_management", displayName: "Municipal Facility Staff Management", scope: "local_authority", label: "Municipal Facility Staff", section: "municipal" },
  { code: "municipal_public_health_team_management", displayName: "Municipal Public Health Team Management", scope: "local_authority", label: "Municipal Public Health Teams", section: "municipal" },
  { code: "municipal_environmental_health_team_management", displayName: "Municipal Environmental Health Team Management", scope: "local_authority", label: "Municipal EHO Teams", section: "municipal" },
  { code: "municipal_impilo_support_user_management", displayName: "Municipal Impilo Support User Management", scope: "local_authority", label: "Municipal Impilo Support Users", section: "municipal" },
  // D — Private provider
  { code: "private_facility_organisation_management", displayName: "Private Facility Organisation Management", scope: "organisation", label: "Private Facility Organisation", section: "private" },
  { code: "private_facility_user_management", displayName: "Private Facility User Management", scope: "facility", label: "Private Facility Users", section: "private" },
  { code: "private_clinician_assignment", displayName: "Private Clinician Assignment", scope: "facility", label: "Private Clinician Assignment", section: "private" },
  { code: "private_non_clinical_staff_assignment", displayName: "Private Non-Clinical Staff Assignment", scope: "facility", label: "Private Non-Clinical Staff", section: "private" },
  { code: "private_facility_service_activation", displayName: "Private Facility Service Activation", scope: "facility", label: "Private Service Activation", section: "private" },
  { code: "private_facility_api_access", displayName: "Private Facility API Access", scope: "organisation", label: "Private Facility API Access", section: "private" },
  { code: "private_facility_compliance_review", displayName: "Private Facility Compliance Review", scope: "organisation", label: "Private Compliance Review", section: "private" },
  // E — Mission
  { code: "mission_facility_organisation_management", displayName: "Mission Facility Organisation Management", scope: "organisation", label: "Mission Organisation Management", section: "mission" },
  { code: "mission_facility_user_management", displayName: "Mission Facility User Management", scope: "facility", label: "Mission Facility Users", section: "mission" },
  { code: "mission_facility_staff_assignment", displayName: "Mission Facility Staff Assignment", scope: "facility", label: "Mission Staff Assignment", section: "mission" },
  { code: "mission_facility_service_activation", displayName: "Mission Facility Service Activation", scope: "facility", label: "Mission Service Activation", section: "mission" },
  { code: "mission_facility_reporting_access", displayName: "Mission Facility Reporting Access", scope: "facility", label: "Mission Reporting Access", section: "mission" },
  // F — Regulator (supplements existing council workspaces)
  { code: "professional_registration_review", displayName: "Professional Registration Review", scope: "regulator", label: "Professional Registration Review", section: "regulator" },
  { code: "licence_status_management", displayName: "Licence Status Management", scope: "regulator", label: "Licence Status Management", section: "regulator" },
  { code: "disciplinary_status_management", displayName: "Disciplinary Status Management", scope: "regulator", label: "Disciplinary Status Management", section: "regulator" },
  { code: "regulatory_audit", displayName: "Regulatory Audit", scope: "regulator", label: "Regulatory Audit", section: "regulator" },
  // G — Madi (some exist; add missing)
  // H — Marketplace
  { code: "marketplace_organisation_verification", displayName: "Marketplace Organisation Verification", scope: "marketplace", label: "Marketplace Verification", section: "marketplace" },
  { code: "marketplace_sandbox_access_management", displayName: "Marketplace Sandbox Access Management", scope: "marketplace", label: "Sandbox Access Management", section: "marketplace" },
  { code: "marketplace_production_access_management", displayName: "Marketplace Production Access Management", scope: "marketplace", label: "Production Access Management", section: "marketplace" },
  { code: "marketplace_offboarding", displayName: "Marketplace Offboarding", scope: "marketplace", label: "Marketplace Offboarding", section: "marketplace" },
  // I — Payer
  { code: "payer_organisation_management", displayName: "Payer Organisation Management", scope: "payer", label: "Payer Organisation Management", section: "payer" },
  { code: "payer_user_management", displayName: "Payer User Management", scope: "payer", label: "Payer Users", section: "payer" },
  { code: "claims_user_assignment", displayName: "Claims User Assignment", scope: "payer", label: "Claims User Assignment", section: "payer" },
  { code: "eligibility_verification_user_assignment", displayName: "Eligibility Verification User Assignment", scope: "payer", label: "Eligibility Verification Users", section: "payer" },
  { code: "payment_reconciliation_user_assignment", displayName: "Payment Reconciliation User Assignment", scope: "payer", label: "Payment Reconciliation Users", section: "payer" },
  { code: "payer_api_access_management", displayName: "Payer API Access Management", scope: "payer", label: "Payer API Access", section: "payer" },
  { code: "payer_compliance_review", displayName: "Payer Compliance Review", scope: "payer", label: "Payer Compliance Review", section: "payer" },
  // J — Training / Fundo
  { code: "training_institution_management", displayName: "Training Institution Management", scope: "training", label: "Training Institution Management", section: "training" },
  { code: "training_provider_user_management", displayName: "Training Provider User Management", scope: "training", label: "Training Provider Users", section: "training" },
  { code: "course_author_assignment", displayName: "Course Author Assignment", scope: "training", label: "Course Author Assignment", section: "training" },
  { code: "cpd_assessor_assignment", displayName: "CPD Assessor Assignment", scope: "training", label: "CPD Assessor Assignment", section: "training" },
  { code: "training_accreditation_user_assignment", displayName: "Training Accreditation User Assignment", scope: "training", label: "Training Accreditation Users", section: "training" },
  { code: "fundo_partner_user_management", displayName: "Fundo Partner User Management", scope: "training", label: "Fundo Partner Users", section: "training" },
  // K — Research / external
  { code: "research_partner_organisation_management", displayName: "Research Partner Organisation Management", scope: "research", label: "Research Partner Management", section: "external" },
  { code: "external_partner_organisation_management", displayName: "External Partner Organisation Management", scope: "external", label: "External Partner Management", section: "external" },
  { code: "external_partner_user_management", displayName: "External Partner User Management", scope: "external", label: "External Partner Users", section: "external" },
  { code: "research_project_user_assignment", displayName: "Research Project User Assignment", scope: "research", label: "Research Project Users", section: "external" },
  { code: "data_access_request_review", displayName: "Data Access Request Review", scope: "research", label: "Data Access Request Review", section: "external" },
  { code: "deidentified_dataset_access", displayName: "De-identified Dataset Access", scope: "research", label: "De-identified Dataset Access", section: "external" },
  { code: "research_audit_workspace", displayName: "Research Audit Workspace", scope: "research", label: "Research Audit", section: "external" },
  { code: "time_limited_access_management", displayName: "Time-Limited Access Management", scope: "external", label: "Time-Limited Access", section: "external" },
  { code: "cross_border_access_review", displayName: "Cross-Border Access Review", scope: "external", label: "Cross-Border Access Review", section: "external" },
];

/** Default management workspaces allowed by organisation type */
export const ORGANISATION_TYPE_MANAGEMENT_WORKSPACES = {
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
  professional_council: ["council_user_management", "council_scope_assignment", "professional_registration_review"],
  statutory_health_council: ["council_user_management", "licence_status_management", "disciplinary_status_management"],
  health_service_commission: ["hsc_user_management", "hsc_scope_assignment", "hsc_public_sector_post_management", "hsc_public_sector_worker_status_management"],
  national_blood_transfusion_service: ["blood_service_organisation_management", "blood_service_user_management"],
  marketplace_organisation: ["marketplace_organisation_user_management", "marketplace_sandbox_access_management"],
  software_vendor: ["marketplace_api_developer_assignment", "marketplace_sandbox_access_management"],
  api_integration_partner: ["marketplace_api_developer_assignment", "marketplace_production_access_management"],
  insurer: ["payer_organisation_management", "payer_user_management", "claims_user_assignment"],
  medical_aid_society: ["payer_user_management", "eligibility_verification_user_assignment"],
  training_institution: ["training_institution_management", "training_provider_user_management"],
  foreign_research_partner: ["external_partner_user_management", "research_project_user_assignment", "deidentified_dataset_access"],
  non_zimbabwean_partner: ["external_partner_organisation_management", "cross_border_access_review", "time_limited_access_management"],
};

/** Exemplar organisation registry records */
export const EXEMPLAR_ORGANISATIONS = [
  {
    organisationId: "org-mohcc-zw",
    code: "mohcc_zimbabwe",
    legalName: "Ministry of Health and Child Care",
    tradingName: "MoHCC Zimbabwe",
    organisationType: "sovereign_public_owner",
    country: "ZW",
    trustTier: "tier_6_sovereign_public_operator",
    lifecycleStatus: "active",
    verificationStatus: "verified",
    publicPrivateClassification: "public",
    approvedEnvironments: ["production_full"],
    approvedDataScopes: ["national_health_operations", "aggregate_reporting", "registry_governance"],
    approvedWorkspaces: ["mohcc_head_office_user_management", "national_organisation_registry", "national_trust_console"],
    auditSensitivity: "critical",
    sourceRegistry: "tuso",
  },
  {
    organisationId: "org-harare-city-health",
    code: "harare_city_health",
    legalName: "City of Harare Health Department",
    organisationType: "city_health_department",
    country: "ZW",
    trustTier: "tier_4_regulated_service_provider",
    lifecycleStatus: "active",
    publicPrivateClassification: "public_local_authority",
    approvedEnvironments: ["production_limited"],
    approvedDataScopes: ["local_authority_health", "municipal_reporting"],
    approvedWorkspaces: ["municipal_user_management", "municipal_facility_staff_management"],
    auditSensitivity: "high",
    sourceRegistry: "tuso",
  },
  {
    organisationId: "org-private-hospital-demo",
    code: "avenues_private_hospital",
    legalName: "Avenues Clinic (Private Hospital)",
    organisationType: "private_hospital",
    country: "ZW",
    trustTier: "tier_4_regulated_service_provider",
    lifecycleStatus: "active",
    publicPrivateClassification: "private",
    approvedEnvironments: ["production_limited"],
    approvedDataScopes: ["facility_clinical", "facility_billing"],
    approvedWorkspaces: ["private_facility_user_management", "private_clinician_assignment"],
    auditSensitivity: "high",
    sourceRegistry: "tuso",
  },
  {
    organisationId: "org-mdpc-zw",
    code: "mdpc_zimbabwe",
    legalName: "Medical and Dental Practitioners Council of Zimbabwe",
    organisationType: "statutory_health_council",
    country: "ZW",
    trustTier: "tier_7_high_trust_regulator",
    lifecycleStatus: "active",
    approvedEnvironments: ["production_limited"],
    approvedDataScopes: ["provider_registry_review", "licence_status"],
    approvedWorkspaces: ["council_user_management", "professional_registration_review", "licence_status_management"],
    auditSensitivity: "critical",
    sourceRegistry: "varapi",
  },
  {
    organisationId: "org-hsc-zw",
    code: "health_service_commission",
    legalName: "Health Service Commission",
    tradingName: "Health Service Commission",
    officialName: "Health Service Commission",
    abbreviation: "HSC",
    aliases: ["Health Service Commission Zimbabwe", "HSC Zimbabwe"],
    organisationType: "health_service_commission",
    organisationClass: "public_sector_workforce_governance_authority",
    governanceDomain: [
      "public_sector_health_workforce",
      "establishment_control",
      "approved_posts",
      "appointments",
      "transfers",
      "promotions",
      "employment_status",
      "disciplinary_employment_status",
      "public_sector_staffing_structures",
      "public_sector_workforce_authorisation",
    ],
    country: "ZW",
    trustTier: "tier_7_high_trust_public_workforce_authority",
    lifecycleStatus: "active",
    verificationStatus: "verified",
    publicPrivateClassification: "public_workforce_governance",
    isRealZimbabweInstitution: true,
    approvedEnvironments: ["production_full"],
    approvedDataScopes: ["public_sector_employment", "establishment_control", "workforce_posting", "workforce_audit"],
    approvedWorkspaces: ["hsc_workspace", "hsc_public_sector_workforce_dashboard", "hsc_user_management", "hsc_establishment_control"],
    auditSensitivity: "critical",
    sourceRegistry: "workforce-governance",
  },
  {
    organisationId: "org-nbts-zw",
    code: "nbts_zimbabwe",
    legalName: "National Blood Transfusion Service of Zimbabwe",
    tradingName: "Madi / NBTS",
    organisationType: "national_blood_transfusion_service",
    country: "ZW",
    trustTier: "tier_6_sovereign_public_operator",
    lifecycleStatus: "active",
    approvedEnvironments: ["production_full"],
    approvedDataScopes: ["blood_donor", "blood_inventory", "transfusion_safety"],
    approvedWorkspaces: ["blood_service_user_management", "madi_blood_service_dashboard"],
    auditSensitivity: "critical",
    sourceRegistry: "tuso",
  },
  {
    organisationId: "org-vendor-sandbox",
    code: "impilo_integration_vendor",
    legalName: "Impilo Integration Partner (Sandbox)",
    organisationType: "software_vendor",
    country: "ZW",
    trustTier: "tier_3_contract_active",
    lifecycleStatus: "sandbox_only",
    approvedEnvironments: ["sandbox"],
    approvedApiScopes: ["marketplace.api.sandbox"],
    approvedDataScopes: ["sandbox_test_data"],
    approvedWorkspaces: ["marketplace_api_developer_assignment", "marketplace_sandbox_access_management"],
    auditSensitivity: "medium",
    sourceRegistry: "msika",
  },
  {
    organisationId: "org-foreign-research",
    code: "foreign_research_partner_demo",
    legalName: "Foreign Research Consortium (Demo)",
    organisationType: "foreign_research_partner",
    country: "US",
    trustTier: "tier_2_verified",
    lifecycleStatus: "data_sharing_active",
    approvedEnvironments: ["deidentified_only"],
    approvedDataScopes: ["deidentified_research_aggregate"],
    approvedWorkspaces: ["external_partner_user_management", "deidentified_dataset_access", "research_audit_workspace"],
    crossBorderAccess: true,
    auditSensitivity: "critical",
    sourceRegistry: "tuso",
  },
];

export const ORGANISATION_FRIENDLY_RESOLUTION_STATES = [
  ["organisation_not_verified", "Organisation not verified", "Your organisation must be verified before work access can be granted.", ["View Organisation Status", "Contact Support"], "TRUST.ORG_NOT_VERIFIED"],
  ["organisation_contract_required", "Organisation contract required", "An active contract is required before this organisation can operate in Impilo.", ["View Contracts", "Contact Support"], "TRUST.ORG_CONTRACT_REQUIRED"],
  ["organisation_data_sharing_required", "Data sharing agreement required", "A data sharing agreement must be active for this access.", ["View Agreements"], "TRUST.ORG_DATA_SHARING_REQUIRED"],
  ["organisation_suspended", "Organisation suspended", "Organisation-linked work access is disabled until suspension is lifted.", ["Contact Support"], "TRUST.ORG_SUSPENDED"],
  ["organisation_offboarded", "Organisation offboarded", "This organisation has been offboarded from Impilo.", ["Contact Support"], "TRUST.ORG_OFFBOARDED"],
  ["organisation_scope_required", "Organisation scope required", "You need an assigned organisation scope to continue.", ["Request Assignment"], "TRUST.ORG_SCOPE_REQUIRED"],
  ["production_access_not_approved", "Production access not approved", "Production API or data access requires explicit approval.", ["Request Production Access"], "TRUST.PRODUCTION_NOT_APPROVED"],
  ["sandbox_only_access", "Sandbox access only", "Your organisation is limited to sandbox environments.", ["Open Sandbox Portal"], "TRUST.SANDBOX_ONLY"],
  ["data_scope_not_approved", "Data scope not approved", "This data scope is not approved for your organisation.", ["View Approved Scopes"], "TRUST.DATA_SCOPE_DENIED"],
  ["cross_border_access_not_approved", "Cross-border access not approved", "Cross-border data access requires explicit approval.", ["Request Cross-Border Review"], "TRUST.CROSS_BORDER_DENIED"],
  ["research_project_approval_required", "Research project approval required", "An approved research project assignment is required.", ["View Research Projects"], "TRUST.RESEARCH_APPROVAL_REQUIRED"],
  ["external_partner_access_expired", "External partner access expired", "Time-limited external partner access has expired.", ["Renew Access"], "TRUST.EXTERNAL_ACCESS_EXPIRED"],
  ["organisation_user_not_assigned", "Organisation assignment required", "You must be assigned to an organisation before work access is available.", ["Request Organisation Assignment"], "TRUST.ORG_USER_NOT_ASSIGNED"],
  ["organisation_admin_scope_exceeded", "Admin scope exceeded", "You cannot perform this management action outside your organisation scope.", ["Contact Support"], "TRUST.ORG_ADMIN_SCOPE_EXCEEDED"],
];

/** Resolve visible management workspaces for an organisation context */
export function resolveManagementWorkspacesForOrganisation(input) {
  const {
    organisationType,
    userOrganisationRole,
    trustTier,
    lifecycleStatus,
    approvedWorkspaces = [],
    accessEnvironment,
  } = input ?? {};
  if (!organisationType) return { visible: [], blocked: [] };

  const defaults = ORGANISATION_TYPE_MANAGEMENT_WORKSPACES[organisationType] ?? [];
  const merged = [...new Set([...defaults, ...approvedWorkspaces])];
  const blocked = [];

  if (lifecycleStatus === "suspended" || lifecycleStatus === "offboarded" || lifecycleStatus === "blacklisted") {
    return { visible: [], blocked: merged };
  }
  if (lifecycleStatus === "sandbox_only" || accessEnvironment === "sandbox") {
    blocked.push("marketplace_production_access_management", "national_platform_user_administration", "production_access_not_approved");
  }
  if (organisationType !== "sovereign_public_owner" && organisationType !== "ministry_department") {
    blocked.push(
      "national_identity_governance",
      "national_organisation_registry",
      "national_system_operator_management",
      "mohcc_head_office_user_management",
    );
  }
  if (!["city_health_department", "municipal_health_department", "local_authority_health_department"].includes(organisationType)) {
    blocked.push("municipal_user_management", "municipal_facility_staff_management");
  }
  if (organisationType !== "private_hospital" && organisationType !== "private_clinic") {
    blocked.push("private_facility_user_management");
  }
  if (!organisationType.includes("council") && organisationType !== "health_professions_authority" && organisationType !== "statutory_health_council") {
    blocked.push("council_user_management", "professional_registration_review");
  }
  if (userOrganisationRole === "read_only_observer") {
    blocked.push(...merged.filter((w) => w.includes("assignment") || w.includes("management")));
  }

  const visible = merged.filter((w) => !blocked.includes(w));
  return { visible, blocked: [...new Set(blocked)] };
}

/** Organisation-aware work eligibility helper */
export function organisationBlocksWork(input) {
  const lifecycle = input?.organisationLifecycleStatus;
  if (["suspended", "offboarded", "blacklisted", "expired", "revoked"].includes(lifecycle)) {
    return lifecycle === "suspended" ? "organisation_suspended" : "organisation_offboarded";
  }
  if (input?.identityType !== "citizen" && !input?.activeOrganisationAssignment) {
    return "organisation_user_not_assigned";
  }
  if (input?.verificationStatus && input.verificationStatus !== "verified" && lifecycle !== "active") {
    return "organisation_not_verified";
  }
  if (input?.accessEnvironment === "sandbox" && input?.requiresProduction) {
    return "sandbox_only_access";
  }
  if (input?.crossBorderAccessFlag && !input?.crossBorderApproved) {
    return "cross_border_access_not_approved";
  }
  if (input?.organisationType === "foreign_research_partner" && !input?.researchProjectAssignment) {
    return "research_project_approval_required";
  }
  return undefined;
}
