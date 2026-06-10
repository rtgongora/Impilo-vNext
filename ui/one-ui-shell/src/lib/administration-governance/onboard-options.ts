/**
 * Policy-filtered Onboard New Actor wizard options.
 */

export interface OnboardActorOption {
  id: string;
  title: string;
  description: string;
  href: string;
  outcome: string;
  requiredWorkspaces: string[];
  initiatorRoles?: string[];
}

export const ONBOARD_ACTOR_OPTIONS: OnboardActorOption[] = [
  {
    id: "citizen",
    title: "Citizen / Client",
    description: "Register or support a personal Health ID account.",
    href: "/work/administration-governance/onboard/citizen",
    outcome: "Creates Personal access only.",
    requiredWorkspaces: ["*"],
  },
  {
    id: "provider-worker",
    title: "Provider / Health Worker",
    description: "Create or verify Provider/Worker ID and professional profile.",
    href: "/work/administration-governance/onboard/provider-worker",
    outcome: "Creates Professional access when verified — Work requires assignment.",
    requiredWorkspaces: ["provider_worker_registry_admin", "council_user_management", "national_registry_governance"],
  },
  {
    id: "public-sector-worker",
    title: "MoHCC Public-Sector Worker",
    description: "Link HSC employment, posting and facility Work for public staff.",
    href: "/work/administration-governance/onboard/public-sector-worker",
    outcome: "Professional + HSC eligibility + Work after assignment.",
    requiredWorkspaces: ["hsc_public_sector_worker_status_management", "public_facility_staff_management", "facility_work_assignment"],
  },
  {
    id: "hsc-user",
    title: "HSC Workforce User",
    description: "Add Health Service Commission workforce governance officers.",
    href: "/work/administration-governance/onboard/hsc-user",
    outcome: "Creates HSC Workforce Governance Work — not clinical Work.",
    requiredWorkspaces: ["hsc_user_management", "hsc_scope_assignment"],
  },
  {
    id: "municipal-user",
    title: "Municipal / City Health User",
    description: "Onboard local authority health department users.",
    href: "/work/administration-governance/onboard/municipal-user",
    outcome: "Creates municipal-scoped Work within city/local authority.",
    requiredWorkspaces: ["municipal_user_management", "municipal_organisation_management"],
  },
  {
    id: "private-facility-user",
    title: "Private or Mission Facility User",
    description: "Onboard private, mission or partner facility staff.",
    href: "/work/administration-governance/onboard/private-facility-user",
    outcome: "Creates facility-scoped Work after verification and assignment.",
    requiredWorkspaces: ["private_facility_user_management", "mission_facility_user_management"],
  },
  {
    id: "regulator-user",
    title: "Council / Regulator User",
    description: "Invite council or HPA regulatory reviewers and stewards.",
    href: "/work/administration-governance/onboard/regulator-user",
    outcome: "Creates Regulatory Work — not clinical or facility assignment Work.",
    requiredWorkspaces: ["council_user_management", "regulator_organisation_management"],
  },
  {
    id: "madi-user",
    title: "Blood Service / Madi User",
    description: "Onboard NBTS/Madi blood service roles and scopes.",
    href: "/work/administration-governance/onboard/madi-user",
    outcome: "Creates Blood Service Work within Madi scope.",
    requiredWorkspaces: ["blood_service_user_management", "blood_service_organisation_management"],
  },
  {
    id: "marketplace-user",
    title: "Marketplace / Vendor User",
    description: "Onboard vendor, API or product team users with environment limits.",
    href: "/work/administration-governance/onboard/marketplace-user",
    outcome: "Creates Marketplace Work within organisation sandbox/production policy.",
    requiredWorkspaces: ["marketplace_user_management", "marketplace_sandbox_access_management"],
  },
  {
    id: "payer-user",
    title: "Payer / Insurer User",
    description: "Onboard claims, eligibility or payment partner users.",
    href: "/work/administration-governance/onboard/payer-user",
    outcome: "Creates payer-scoped Work — no clinical record browsing.",
    requiredWorkspaces: ["payer_user_management", "payer_organisation_management"],
  },
  {
    id: "training-user",
    title: "Training / Fundo Partner User",
    description: "Onboard training institution or Fundo partner users.",
    href: "/work/administration-governance/onboard/training-user",
    outcome: "Creates training/Fundo Work within programme scope.",
    requiredWorkspaces: ["training_provider_user_management", "training_institution_management", "fundo_partner_user_management"],
  },
  {
    id: "external-partner-user",
    title: "Research / External Partner User",
    description: "Onboard research or cross-border partners with data limits.",
    href: "/work/administration-governance/onboard/external-partner-user",
    outcome: "Creates partner Work with purpose, environment and expiry.",
    requiredWorkspaces: ["external_partner_user_management", "research_partner_organisation_management"],
  },
  {
    id: "system-admin",
    title: "System / Support Administrator",
    description: "Scoped platform, trust, policy or integration administrators.",
    href: "/work/administration-governance/onboard/system-admin",
    outcome: "Creates scoped admin Work — no default superuser.",
    requiredWorkspaces: ["national_platform_user_administration", "national_system_operator_management"],
  },
];

export const ONBOARD_FLOW_STEPS: Record<string, { title: string; steps: string[] }> = {
  citizen: {
    title: "Citizen / Client onboarding",
    steps: ["Registration path", "Find or capture person", "Account state", "Consent & relationships", "Review & submit"],
  },
  "provider-worker": {
    title: "Provider / Health Worker onboarding",
    steps: ["Health ID", "Provider/Worker ID", "Professional profile", "Link IDs", "Verification", "Professional status"],
  },
  "public-sector-worker": {
    title: "MoHCC public-sector worker onboarding",
    steps: ["Provider/Worker ID", "Council status", "HSC employment", "MoHCC scope", "Role template", "Assumption of duty", "OPA review", "Activate Work"],
  },
  "hsc-user": {
    title: "HSC workforce user onboarding",
    steps: ["HSC organisation", "Invite user", "HSC role", "Scope assignment", "Activate governance Work"],
  },
  "municipal-user": {
    title: "Municipal health user onboarding",
    steps: ["Organisation", "Verification", "Add user", "Link IDs", "Municipal role", "Scope", "Workspace", "Activate Work"],
  },
  "private-facility-user": {
    title: "Private / mission facility user onboarding",
    steps: ["Organisation", "Verify organisation", "Invite user", "Clinical linkage", "Organisation role", "Facility scope", "Activate Work"],
  },
  "regulator-user": {
    title: "Council / regulator user onboarding",
    steps: ["Regulatory organisation", "Invite user", "Council role", "Regulatory scope", "Activate regulatory Work"],
  },
  "madi-user": {
    title: "Madi / blood service user onboarding",
    steps: ["Blood service organisation", "Invite user", "Madi role", "Blood service scope", "Activate Madi Work"],
  },
  "marketplace-user": {
    title: "Marketplace / vendor user onboarding",
    steps: ["Organisation", "Verification", "Pipeline state", "Invite users", "Roles", "Access environment", "Activate marketplace Work"],
  },
  "payer-user": {
    title: "Payer / insurer user onboarding",
    steps: ["Payer organisation", "Contract status", "Invite users", "Payer role", "Data scope", "Activate payer Work"],
  },
  "training-user": {
    title: "Training / Fundo partner onboarding",
    steps: ["Institution", "Accreditation", "Invite user", "Training role", "Programme scope", "Activate training Work"],
  },
  "external-partner-user": {
    title: "Research / external partner onboarding",
    steps: ["Partner organisation", "Contracts & approvals", "Invite user", "Project role", "Data scope", "Environment & expiry", "Activate partner Work"],
  },
  "system-admin": {
    title: "System administrator onboarding",
    steps: ["Admin type", "Scope", "Permissions", "MFA assurance", "Approval", "Activate scoped admin Work"],
  },
};
