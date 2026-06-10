/**
 * HSC workforce governance resolution — scoped workspaces and work eligibility.
 */

import type { OrganisationGovernanceInput } from "./organisation-governance";

const HSC_ONLY = [
  "hsc_workspace",
  "hsc_public_sector_workforce_dashboard",
  "hsc_establishment_control",
  "hsc_approved_posts_management",
  "hsc_user_management",
  "hsc_scope_assignment",
  "hsc_workforce_officer_assignment",
  "hsc_establishment_officer_assignment",
  "hsc_public_sector_post_management",
  "hsc_public_sector_worker_status_management",
];

const HSC_TYPE_DEFAULTS = [
  "hsc_workspace",
  "hsc_public_sector_workforce_dashboard",
  "hsc_user_management",
  "hsc_establishment_control",
];

export function isHscOrganisationType(organisationType?: string | null): boolean {
  return organisationType === "health_service_commission";
}

export function resolveHscManagementWorkspaces(input: OrganisationGovernanceInput): {
  visible: string[];
  blocked: string[];
} {
  if (!isHscOrganisationType(input.organisationType)) {
    return { visible: [], blocked: HSC_ONLY };
  }

  const merged = [...new Set([...HSC_TYPE_DEFAULTS, ...(input.approvedWorkspaces ?? [])])];
  const blocked = new Set<string>([
    "national_identity_governance",
    "national_organisation_registry",
    "national_system_operator_management",
    "mohcc_head_office_user_management",
    "council_user_management",
    "professional_registration_review",
    "private_facility_user_management",
    "public_facility_staff_management",
  ]);

  if (input.userOrganisationRole === "read_only_observer" || input.userOrganisationRole === "hsc_read_only_reviewer") {
    merged.filter((w) => w.includes("management") || w.includes("assignment")).forEach((w) => blocked.add(w));
  }

  const visible = merged.filter((w) => !blocked.has(w));
  return { visible, blocked: [...blocked] };
}

export function hscOfficerBlocksClinicalAccess(organisationType?: string | null): boolean {
  return isHscOrganisationType(organisationType);
}
