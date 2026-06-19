/**
 * Scoped administration surface definitions for domain pages.
 */

export interface AdministrationSurface {
  id: string;
  title: string;
  subtitle: string;
  requiredWorkspaces: string[];
  sections: Array<{ title: string; description: string; href?: string }>;
}

export const ADMINISTRATION_SURFACES: Record<string, AdministrationSurface> = {
  hsc: {
    id: "hsc",
    title: "HSC Workforce Governance",
    subtitle: "Health Service Commission public-sector employment and posting authority — not clinical Work.",
    requiredWorkspaces: ["hsc_user_management", "hsc_establishment_control", "hsc_workspace"],
    sections: [
      { title: "Users", description: "HSC officers and scope assignments.", href: "/work/hsc/users" },
      { title: "Establishment control", description: "Approved establishment units and post families.", href: "/work/hsc/establishment" },
      { title: "Approved posts", description: "Manage approved public-sector posts.", href: "/work/hsc/posts" },
      { title: "Appointments", description: "Review and approve appointments.", href: "/work/hsc/appointments" },
      { title: "Transfers", description: "Review transfer requests.", href: "/work/hsc/transfers" },
      { title: "Posting authorisation", description: "Authorise postings and assumption of duty.", href: "/work/hsc/postings" },
      { title: "Discipline", description: "Employment disciplinary status.", href: "/work/hsc/discipline" },
      { title: "Audit", description: "HSC workforce governance audit trail.", href: "/work/hsc/audit" },
    ],
  },
  hsc_users: {
    id: "hsc_users",
    title: "HSC Users",
    subtitle: "Manage Health Service Commission users within HSC scope.",
    requiredWorkspaces: ["hsc_user_management"],
    sections: [{ title: "User list", description: "Invite, assign roles and scope for HSC officers." }],
  },
  regulators: {
    id: "regulators",
    title: "Council / Regulator Workspace",
    subtitle: "Real Zimbabwe health professions councils and HPA — professional truth only.",
    requiredWorkspaces: ["council_user_management", "regulator_organisation_management"],
    sections: [
      { title: "Health Professions Authority", description: "HPA oversight and appeals.", href: "/work/regulators/health_professions_authority" },
      { title: "Medical & Dental Practitioners Council", description: "MDPC registration review.", href: "/work/regulators/medical_dental_practitioners_council" },
      { title: "Nurses Council", description: "Nursing registration and training.", href: "/work/regulators/nurses_council" },
      { title: "Pharmacists Council", description: "Pharmacy practice registration.", href: "/work/regulators/pharmacists_council" },
      { title: "Allied Health Practitioners Council", description: "Allied health professions.", href: "/work/regulators/allied_health_practitioners_council" },
      { title: "Medical Laboratories Council", description: "Laboratory practitioners.", href: "/work/regulators/medical_laboratories_clinical_scientists_council" },
      { title: "Environmental Health Council", description: "Environmental health practitioners.", href: "/work/regulators/environmental_health_practitioners_council" },
      { title: "Rehabilitation Practitioners Council", description: "Rehabilitation professions.", href: "/work/regulators/medical_rehabilitation_practitioners_council" },
      { title: "Natural Therapists Council", description: "Natural therapists registration.", href: "/work/regulators/natural_therapists_council" },
    ],
  },
  madi_admin: {
    id: "madi_admin",
    title: "Madi / Blood Service Administration",
    subtitle: "NBTS blood service users and assignments — separate from general clinical records.",
    requiredWorkspaces: ["blood_service_user_management", "blood_service_organisation_management"],
    sections: [
      { title: "Users", description: "Blood service role assignments.", href: "/work/madi/users" },
      { title: "Blood banks", description: "National, regional and hospital blood banks.", href: "/work/madi/blood-banks" },
      { title: "Assignments", description: "Collection, processing and distribution teams.", href: "/work/madi/assignments" },
      { title: "Audit", description: "Madi administration audit.", href: "/work/madi/audit" },
    ],
  },
  marketplace_orgs: {
    id: "marketplace_orgs",
    title: "Marketplace Organisation Management",
    subtitle: "Vendor users, sandbox/production API access and compliance.",
    requiredWorkspaces: ["marketplace_user_management", "marketplace_sandbox_access_management"],
    sections: [{ title: "Organisations", description: "Select an organisation to manage users and API access." }],
  },
  payers: {
    id: "payers",
    title: "Payer / Insurer Access",
    subtitle: "Claims, eligibility and payment partners — no clinical record browsing.",
    requiredWorkspaces: ["payer_user_management", "payer_organisation_management"],
    sections: [{ title: "Payer organisations", description: "Manage payer-scoped users and data access." }],
  },
  partners: {
    id: "partners",
    title: "Research / External Partner Access",
    subtitle: "Purpose-bound, time-limited partner access with data-scope controls.",
    requiredWorkspaces: ["external_partner_user_management", "research_partner_organisation_management"],
    sections: [
      { title: "Partner organisations", description: "External and research partners." },
      { title: "Data access", description: "Aggregate, de-identified or approved identified access." },
    ],
  },
  system_admin: {
    id: "system_admin",
    title: "System Administration",
    subtitle: "Scoped platform administration — Keycloak roles alone do not unlock Work.",
    requiredWorkspaces: ["national_platform_user_administration", "national_system_operator_management"],
    sections: [
      { title: "Users", description: "Scoped platform users.", href: "/work/system-admin/users" },
      { title: "Scopes", description: "Administrative scope assignments.", href: "/work/system-admin/scopes" },
      { title: "Trust (Tshepo)", description: "Policy bundles and trust context.", href: "/work/system-admin/tshepo" },
      { title: "OPA", description: "Policy packages and bundles.", href: "/work/system-admin/opa" },
      { title: "Break-glass", description: "Separate, audited emergency access.", href: "/work/system-admin/break-glass" },
      { title: "Audit", description: "Platform administration audit.", href: "/work/system-admin/audit" },
    ],
  },
  access_requests: {
    id: "access_requests",
    title: "Access Requests & Approvals",
    subtitle: "Pending verification, assignment, sandbox and partner access requests in your scope.",
    requiredWorkspaces: ["*"],
    sections: [{ title: "Request queue", description: "Approve, reject or escalate within OPA-allowed actions." }],
  },
  audit: {
    id: "audit",
    title: "Administration Audit",
    subtitle: "Recent access and governance changes in your authority scope.",
    requiredWorkspaces: ["*"],
    sections: [{ title: "Audit log", description: "Management actions emit audit events." }],
  },
  access_review: {
    id: "access_review",
    title: "Access Review",
    subtitle: "Review who has access to what by person, organisation, role or assignment.",
    requiredWorkspaces: ["national_audit_and_compliance", "regulatory_audit", "hsc_audit_workspace"],
    sections: [{ title: "Review subjects", description: "By person, organisation, facility, role or permission." }],
  },
  municipal: {
    id: "municipal",
    title: "Municipal Health Administration",
    subtitle: "Local authority health users — no national MoHCC powers.",
    requiredWorkspaces: ["municipal_user_management", "municipal_organisation_management"],
    sections: [
      { title: "Organisation management", description: "City or municipal health department." },
      { title: "User management", description: "Municipal health team users." },
      { title: "Facility staff", description: "Municipal facility staff assignments." },
    ],
  },
  private_facility: {
    id: "private_facility",
    title: "Private / Partner Facility Administration",
    subtitle: "Private and mission facility users within organisation scope only.",
    requiredWorkspaces: ["private_facility_user_management", "mission_facility_user_management"],
    sections: [
      { title: "Organisation", description: "Private or mission facility organisation profile." },
      { title: "Users", description: "Facility administrators and staff." },
      { title: "Clinician assignment", description: "Assign verified clinicians to facility Work." },
    ],
  },
  facility_staff_access: {
    id: "facility_staff_access",
    title: "Facility Staff & Access",
    subtitle: "Assign verified providers with council/HSC/training prechecks.",
    requiredWorkspaces: ["public_facility_staff_management", "facility_work_assignment", "private_facility_user_management"],
    sections: [
      { title: "Assign provider/worker", description: "Search verified provider, select role and department." },
      { title: "Assumption of duty", description: "Record assumption where required." },
      { title: "Local suspension", description: "Suspend local access without overriding HSC employment." },
    ],
  },
  organisations: {
    id: "organisations",
    title: "Organisation Registry",
    subtitle: "Register, verify and govern organisations — contextual not generic.",
    requiredWorkspaces: ["national_organisation_registry", "municipal_organisation_management", "private_facility_organisation_management"],
    sections: [
      { title: "Organisation list", description: "Search and filter by type, trust tier and lifecycle." },
      { title: "Register organisation", description: "Start organisation onboarding.", href: "/work/administration-governance/organisations/new" },
    ],
  },
  vashandi: {
    id: "vashandi",
    title: "Vashandi Operational Workforce",
    subtitle: "Roster, shift, attendance, leave and access risk — operational SoR, not HSC employment authority.",
    requiredWorkspaces: ["vashandi.dashboard", "vashandi.workforce_registry", "vashandi.facility_staff"],
    sections: [
      { title: "Workforce registry", description: "Operational workforce profiles and memberships.", href: "/work/vashandi/workforce" },
      { title: "Assignments", description: "Facility and programme assignment lifecycle.", href: "/work/vashandi/assignments" },
      { title: "Rosters", description: "Roster planning, shifts and coverage.", href: "/work/vashandi/rosters" },
      { title: "Attendance", description: "Check-in, check-out and supervisor confirmation.", href: "/work/vashandi/attendance" },
      { title: "Leave & availability", description: "Leave records and availability windows.", href: "/work/vashandi/leave-availability" },
      { title: "Access review", description: "Workforce access risk scan and remediation.", href: "/work/vashandi/access-review" },
      { title: "Analytics", description: "Staffing, roster coverage and attendance metrics.", href: "/work/vashandi/analytics" },
      { title: "Imports", description: "Bulk workforce import bridge.", href: "/work/vashandi/imports" },
    ],
  },
  training: {
    id: "training",
    title: "Training & Fundo Partner Management",
    subtitle: "Training institutions, course authors and Fundo partners within assigned scope.",
    requiredWorkspaces: ["training_institution_management", "training_provider_user_management", "fundo_partner_user_management"],
    sections: [
      { title: "Partners", description: "Training and Fundo partner organisations.", href: "/work/fundo/admin/partners" },
      { title: "Courses", description: "Course catalogue administration.", href: "/work/fundo/admin/courses" },
      { title: "Accreditation", description: "Training accreditation workflows.", href: "/work/fundo/admin/accreditation" },
    ],
  },
};
