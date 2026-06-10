/**
 * Administration & Governance tiles — driven by SessionExperienceContract management workspaces.
 */

import type { LucideIcon } from "lucide-react";
import {
  Building2,
  ClipboardCheck,
  Droplets,
  GraduationCap,
  HeartPulse,
  Landmark,
  Shield,
  ShoppingBag,
  Stethoscope,
  Users,
  Wallet,
  Globe,
  Settings,
  FileSearch,
  UserPlus,
} from "lucide-react";

export interface AdministrationTile {
  id: string;
  title: string;
  description: string;
  href: string;
  icon: LucideIcon;
  /** At least one management workspace required to show as enabled */
  requiredWorkspaces: string[];
  /** Shown as disabled hint when workspace is in blockedManagementWorkspaces */
  blockedWorkspaceHint?: string;
  quickAction?: boolean;
}

export const QUICK_ACTION_TILES: AdministrationTile[] = [
  {
    id: "onboard",
    title: "Onboard New Actor",
    description: "Guided onboarding for citizens, providers, organisations and scoped roles.",
    href: "/work/administration-governance/onboard",
    icon: UserPlus,
    requiredWorkspaces: ["*"],
    quickAction: true,
  },
  {
    id: "access_requests",
    title: "Review Pending Access Requests",
    description: "Approvals for assignments, verification, sandbox and partner access.",
    href: "/work/administration-governance/access-requests",
    icon: ClipboardCheck,
    requiredWorkspaces: ["*"],
    quickAction: true,
  },
  {
    id: "audit",
    title: "Audit Recent Access Changes",
    description: "Review who gained or lost access in your scope.",
    href: "/work/administration-governance/audit",
    icon: FileSearch,
    requiredWorkspaces: ["*"],
    quickAction: true,
  },
];

export const MANAGEMENT_TILES: AdministrationTile[] = [
  {
    id: "organisations",
    title: "Organisations",
    description: "Register, verify and govern organisation records, contracts and API access.",
    href: "/work/administration-governance/organisations",
    icon: Building2,
    requiredWorkspaces: [
      "national_organisation_registry",
      "municipal_organisation_management",
      "private_facility_organisation_management",
      "marketplace_organisation_registration",
      "research_partner_organisation_management",
    ],
    blockedWorkspaceHint: "national_organisation_registry",
  },
  {
    id: "organisation_users",
    title: "Organisation Users",
    description: "Add and scope users within your organisation — never generic global user admin.",
    href: "/work/administration-governance/organisations",
    icon: Users,
    requiredWorkspaces: [
      "mohcc_head_office_user_management",
      "municipal_user_management",
      "private_facility_user_management",
      "hsc_user_management",
      "council_user_management",
      "blood_service_user_management",
      "marketplace_user_management",
      "payer_user_management",
    ],
  },
  {
    id: "provider_registry",
    title: "Provider / Worker Registry",
    description: "Verify professional identity, council status and workforce credentials.",
    href: "/registry-admin",
    icon: Stethoscope,
    requiredWorkspaces: [
      "provider_worker_registry_admin",
      "council_user_management",
      "national_registry_governance",
    ],
  },
  {
    id: "facility_staff",
    title: "Facility Staff & Access",
    description: "Assign verified providers to departments, roles and local Work context.",
    href: "/work/facility/staff-access",
    icon: HeartPulse,
    requiredWorkspaces: [
      "public_facility_staff_management",
      "facility_work_assignment",
      "municipal_facility_staff_management",
      "private_facility_user_management",
      "private_clinician_assignment",
    ],
  },
  {
    id: "hsc",
    title: "HSC Workforce Governance",
    description: "Public-sector employment, posts, appointments, transfers and posting authority.",
    href: "/work/hsc",
    icon: Landmark,
    requiredWorkspaces: [
      "hsc_user_management",
      "hsc_public_sector_post_management",
      "hsc_public_sector_worker_status_management",
      "hsc_establishment_control",
    ],
    blockedWorkspaceHint: "hsc_user_management",
  },
  {
    id: "regulators",
    title: "Council / Regulator Workspace",
    description: "Professional registration, licence, CPD and disciplinary review within council scope.",
    href: "/work/regulators",
    icon: Shield,
    requiredWorkspaces: ["council_user_management", "regulator_organisation_management", "regulatory_audit"],
  },
  {
    id: "municipal",
    title: "Municipal Health Administration",
    description: "City and local authority health teams, facilities and environmental health.",
    href: "/work/administration-governance/municipal",
    icon: Building2,
    requiredWorkspaces: [
      "municipal_user_management",
      "municipal_facility_staff_management",
      "municipal_public_health_team_management",
    ],
  },
  {
    id: "private_facility",
    title: "Private / Partner Facility Administration",
    description: "Private, mission and partner facility users, services and compliance.",
    href: "/work/administration-governance/private-facility",
    icon: Building2,
    requiredWorkspaces: [
      "private_facility_user_management",
      "mission_facility_user_management",
      "private_facility_compliance_review",
    ],
  },
  {
    id: "madi",
    title: "Madi / Blood Service Administration",
    description: "NBTS blood service users, banks, collection and haemovigilance roles.",
    href: "/work/madi/admin",
    icon: Droplets,
    requiredWorkspaces: ["blood_service_user_management", "blood_service_organisation_management"],
  },
  {
    id: "marketplace",
    title: "Marketplace Organisation Management",
    description: "Vendor users, sandbox/production API access, products and compliance.",
    href: "/work/marketplace/organisations",
    icon: ShoppingBag,
    requiredWorkspaces: [
      "marketplace_user_management",
      "marketplace_sandbox_access_management",
      "marketplace_production_access_management",
    ],
  },
  {
    id: "payers",
    title: "Payer / Insurer Access",
    description: "Claims, eligibility and payment partners within contract data scope.",
    href: "/work/payers",
    icon: Wallet,
    requiredWorkspaces: ["payer_user_management", "payer_organisation_management", "claims_user_assignment"],
  },
  {
    id: "training",
    title: "Training & Fundo Partner Management",
    description: "Training institutions, course authors, CPD assessors and Fundo partners.",
    href: "/work/fundo/admin/partners",
    icon: GraduationCap,
    requiredWorkspaces: ["training_institution_management", "training_provider_user_management", "fundo_partner_user_management"],
  },
  {
    id: "external_partners",
    title: "Research / External Partner Access",
    description: "Time-limited partner access with purpose, environment and data-scope controls.",
    href: "/work/partners",
    icon: Globe,
    requiredWorkspaces: [
      "external_partner_user_management",
      "research_partner_organisation_management",
      "deidentified_dataset_access",
    ],
  },
  {
    id: "system_admin",
    title: "System Administration",
    description: "Scoped platform, trust, policy and integration administration — no default superuser.",
    href: "/work/system-admin",
    icon: Settings,
    requiredWorkspaces: [
      "national_platform_user_administration",
      "national_system_operator_management",
      "national_api_access_governance",
    ],
  },
  {
    id: "access_review",
    title: "Audit & Access Review",
    description: "Review assignments, permissions, API scopes and break-glass events.",
    href: "/work/administration-governance/access-review",
    icon: FileSearch,
    requiredWorkspaces: [
      "national_audit_and_compliance",
      "regulatory_audit",
      "hsc_audit_workspace",
      "research_audit_workspace",
    ],
  },
];
