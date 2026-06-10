/**
 * Health Service Commission (HSC) — public-sector workforce governance authority.
 * Distinct from MoHCC operations, professional councils, regulators and system admin.
 * MoHCC organogram approved by HSC (2025-03-05).
 */

export const HSC_GOVERNANCE_NOTE =
  "HSC is the public-sector health workforce governance authority — not MoHCC, council, regulator or system admin.";

export const HSC_ORGANISATION = {
  organisationId: "org-hsc-zw",
  code: "health_service_commission",
  officialName: "Health Service Commission",
  legalName: "Health Service Commission",
  tradingName: "Health Service Commission",
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
  auditSensitivity: "critical",
  approvedEnvironments: ["production_full"],
  approvedDataScopes: [
    "public_sector_employment",
    "establishment_control",
    "workforce_posting",
    "workforce_audit",
  ],
  approvedWorkspaces: [
    "hsc_workspace",
    "hsc_public_sector_workforce_dashboard",
    "hsc_user_management",
    "hsc_establishment_control",
  ],
  sourceRegistry: "workforce-governance",
  parentOrganisationId: null,
  policyMapping: "impilo.hsc",
};

export const HSC_WORKSPACES = [
  { code: "hsc_workspace", displayName: "HSC Workspace", scope: "hsc_national", label: "HSC Workspace", section: "hsc" },
  { code: "hsc_public_sector_workforce_dashboard", displayName: "HSC Public Sector Workforce Dashboard", scope: "hsc_national", label: "Public Sector Workforce Dashboard", section: "hsc" },
  { code: "hsc_establishment_control", displayName: "HSC Establishment Control", scope: "hsc_national", label: "Establishment Control", section: "hsc" },
  { code: "hsc_approved_posts_management", displayName: "HSC Approved Posts Management", scope: "hsc_national", label: "Approved Posts Management", section: "hsc" },
  { code: "hsc_public_sector_worker_review", displayName: "HSC Public Sector Worker Review", scope: "hsc_national", label: "Public Sector Worker Review", section: "hsc" },
  { code: "hsc_appointments_review", displayName: "HSC Appointments Review", scope: "hsc_national", label: "Appointments Review", section: "hsc" },
  { code: "hsc_transfers_review", displayName: "HSC Transfers Review", scope: "hsc_national", label: "Transfers Review", section: "hsc" },
  { code: "hsc_promotions_review", displayName: "HSC Promotions Review", scope: "hsc_national", label: "Promotions Review", section: "hsc" },
  { code: "hsc_discipline_employment_status_review", displayName: "HSC Discipline Employment Status Review", scope: "hsc_national", label: "Discipline Employment Status Review", section: "hsc" },
  { code: "hsc_posting_authorisation", displayName: "HSC Posting Authorisation", scope: "hsc_national", label: "Posting Authorisation", section: "hsc" },
  { code: "hsc_workforce_registry_stewardship", displayName: "HSC Workforce Registry Stewardship", scope: "hsc_national", label: "Workforce Registry Stewardship", section: "hsc" },
  { code: "hsc_audit_workspace", displayName: "HSC Audit Workspace", scope: "hsc_national", label: "HSC Audit", section: "hsc" },
];

export const HSC_MANAGEMENT_WORKSPACES = [
  { code: "hsc_user_management", displayName: "HSC User Management", scope: "hsc_national", label: "HSC Users", section: "hsc_management" },
  { code: "hsc_scope_assignment", displayName: "HSC Scope Assignment", scope: "hsc_national", label: "HSC Scope Assignment", section: "hsc_management" },
  { code: "hsc_workforce_officer_assignment", displayName: "HSC Workforce Officer Assignment", scope: "hsc_national", label: "HSC Workforce Officer Assignment", section: "hsc_management" },
  { code: "hsc_establishment_officer_assignment", displayName: "HSC Establishment Officer Assignment", scope: "hsc_national", label: "HSC Establishment Officer Assignment", section: "hsc_management" },
  { code: "hsc_public_sector_post_management", displayName: "HSC Public Sector Post Management", scope: "hsc_national", label: "Public Sector Post Management", section: "hsc_management" },
  { code: "hsc_public_sector_worker_status_management", displayName: "HSC Public Sector Worker Status Management", scope: "hsc_national", label: "Public Sector Worker Status Management", section: "hsc_management" },
];

export const PUBLIC_SECTOR_EMPLOYMENT_STATUSES = [
  "not_employed_public_sector",
  "pending_appointment",
  "appointed",
  "active",
  "on_leave",
  "transferred_pending_assumption",
  "seconded",
  "suspended_from_employment",
  "dismissed",
  "retired",
  "resigned",
  "deceased",
  "contract_expired",
  "under_disciplinary_review",
];

export const HSC_FRIENDLY_RESOLUTION_STATES = [
  { code: "hsc_employment_not_found", displayName: "HSC employment not found", description: "No public-sector employment record was found for this provider.", allowedActions: ["View Professional Profile", "Contact HSC"], policyKey: "TRUST.HSC_EMPLOYMENT_NOT_FOUND" },
  { code: "hsc_appointment_pending", displayName: "HSC appointment pending", description: "Your public-sector appointment is pending HSC confirmation.", allowedActions: ["View Appointment Status"], policyKey: "TRUST.HSC_APPOINTMENT_PENDING" },
  { code: "hsc_transfer_pending_assumption", displayName: "Transfer pending assumption", description: "Your transfer is approved but assumption of duty is not yet recorded.", allowedActions: ["Record Assumption of Duty", "View Transfer Status"], policyKey: "TRUST.HSC_TRANSFER_PENDING" },
  { code: "hsc_employment_suspended", displayName: "HSC employment suspended", description: "Public-sector employment is suspended by HSC.", allowedActions: ["View Employment Status", "Contact HSC"], policyKey: "TRUST.HSC_EMPLOYMENT_SUSPENDED" },
  { code: "hsc_employment_dismissed", displayName: "HSC employment dismissed", description: "Public-sector employment has been dismissed.", allowedActions: ["View Employment Status"], policyKey: "TRUST.HSC_EMPLOYMENT_DISMISSED" },
  { code: "hsc_posting_required", displayName: "HSC posting required", description: "A valid HSC posting is required before public-sector Work can begin.", allowedActions: ["View Posting Status", "Contact HSC"], policyKey: "TRUST.HSC_POSTING_REQUIRED" },
  { code: "hsc_scope_required", displayName: "HSC scope required", description: "Your HSC officer scope must be assigned before workforce tools appear.", allowedActions: ["Request Scope Assignment"], policyKey: "TRUST.HSC_SCOPE_REQUIRED" },
  { code: "hsc_authority_required", displayName: "HSC authority required", description: "This action requires Health Service Commission workforce authority.", allowedActions: ["Contact HSC Administrator"], policyKey: "TRUST.HSC_AUTHORITY_REQUIRED" },
];

export const HSC_PERMISSIONS = [
  "hsc.workforce.view",
  "hsc.workforce.manage",
  "hsc.establishment.view",
  "hsc.establishment.manage",
  "hsc.posts.view",
  "hsc.posts.manage",
  "hsc.appointments.review",
  "hsc.appointments.approve",
  "hsc.transfers.review",
  "hsc.transfers.approve",
  "hsc.promotions.review",
  "hsc.promotions.approve",
  "hsc.discipline.review",
  "hsc.posting.authorize",
  "hsc.employment.suspend",
  "hsc.employment.reinstate",
  "hsc.registry.steward",
  "hsc.audit.view",
  "hsc.scope.assign",
  "hsc.users.manage",
];

export const HSC_PERMISSION_BASELINE = {
  hsc_read_only: ["hsc.workforce.view", "hsc.establishment.view", "hsc.posts.view", "hsc.audit.view"],
  hsc_officer: ["hsc.workforce.view", "hsc.workforce.manage", "hsc.establishment.view", "hsc.posts.view", "hsc.appointments.review", "hsc.transfers.review", "hsc.promotions.review"],
  hsc_approver: ["hsc.workforce.view", "hsc.workforce.manage", "hsc.appointments.approve", "hsc.transfers.approve", "hsc.promotions.approve", "hsc.posting.authorize"],
  hsc_administrator: ["hsc.workforce.view", "hsc.workforce.manage", "hsc.establishment.manage", "hsc.posts.manage", "hsc.users.manage", "hsc.scope.assign", "hsc.audit.view"],
  hsc_steward: ["hsc.workforce.view", "hsc.registry.steward", "hsc.audit.view"],
};

export const HSC_DENIED_ACTIONS = [
  "clinical.encounter.create",
  "clinical.notes.write",
  "clinical.prescriptions.create",
  "clinical.orders.create",
  "clinical.admissions.manage",
  "registry.provider.verify",
  "registry.provider.approve",
  "registry.provider.create_draft",
  "facility.staff.assign",
  "system.roles.manage",
  "break_glass.authorize",
  "policy.bundle.publish",
];

/** Role code → [baselineKey, workspaces, scopeLevel, approvalRequirements] */
export const HSC_ROLE_DEFINITIONS = {
  hsc_administrator: ["hsc_administrator", ["hsc_workspace", "hsc_public_sector_workforce_dashboard", "hsc_user_management", "hsc_scope_assignment"], "hsc_national", ["dual_control_for_employment_truth"]],
  hsc_workforce_governance_officer: ["hsc_officer", ["hsc_workspace", "hsc_public_sector_workforce_dashboard", "hsc_public_sector_worker_review"], "hsc_national", []],
  hsc_establishment_control_officer: ["hsc_officer", ["hsc_establishment_control", "hsc_approved_posts_management"], "hsc_national", []],
  hsc_appointments_officer: ["hsc_officer", ["hsc_appointments_review", "hsc_public_sector_worker_review"], "hsc_national", ["supervisor_approval_for_appointment"]],
  hsc_transfers_officer: ["hsc_officer", ["hsc_transfers_review", "hsc_posting_authorisation"], "hsc_national", ["supervisor_approval_for_transfer"]],
  hsc_promotions_officer: ["hsc_officer", ["hsc_promotions_review"], "hsc_national", ["supervisor_approval_for_promotion"]],
  hsc_discipline_officer: ["hsc_officer", ["hsc_discipline_employment_status_review"], "hsc_national", ["legal_review_for_dismissal"]],
  hsc_public_sector_staffing_reviewer: ["hsc_officer", ["hsc_public_sector_worker_review", "hsc_approved_posts_management"], "hsc_national", []],
  hsc_posting_authority_officer: ["hsc_approver", ["hsc_posting_authorisation", "hsc_transfers_review"], "hsc_national", ["posting_authority_delegation"]],
  hsc_workforce_registry_steward: ["hsc_steward", ["hsc_workforce_registry_stewardship"], "hsc_national", []],
  hsc_workforce_data_quality_officer: ["hsc_steward", ["hsc_workforce_registry_stewardship", "hsc_public_sector_worker_review"], "hsc_national", []],
  hsc_auditor: ["hsc_read_only", ["hsc_audit_workspace"], "hsc_national", []],
  hsc_read_only_reviewer: ["hsc_read_only", ["hsc_workspace", "hsc_public_sector_workforce_dashboard"], "hsc_national", []],
};

export const HSC_ROLE_TEMPLATE_SPECS = Object.entries(HSC_ROLE_DEFINITIONS).map(([code, [baseline, workspaces, scopeLevel, approvalRequirements]]) => [
  code,
  "hsc_workforce_governance",
  ["public_sector_workforce_governance"],
  workspaces,
  [],
  baseline,
  scopeLevel,
  approvalRequirements,
]);

export const HSC_WORKFORCE_GOVERNANCE_ROLES = Object.keys(HSC_ROLE_DEFINITIONS);

export const PUBLIC_SECTOR_EMPLOYER_TYPES = new Set([
  "sovereign_public_owner",
  "ministry_department",
  "national_directorate",
  "provincial_medical_office",
  "district_medical_office",
  "public_facility",
  "central_hospital",
  "provincial_hospital",
  "district_hospital",
  "general_hospital",
  "rural_health_centre",
  "public_clinic",
  "public_health_office",
  "health_service_commission",
]);

export const HSC_EMPLOYMENT_WORK_BLOCKED_STATUSES = new Set([
  "not_employed_public_sector",
  "pending_appointment",
  "suspended_from_employment",
  "dismissed",
  "retired",
  "resigned",
  "deceased",
  "contract_expired",
  "under_disciplinary_review",
]);
