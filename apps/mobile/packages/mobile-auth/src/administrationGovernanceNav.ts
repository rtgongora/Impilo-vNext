/**
 * Mobile Administration & Governance navigation — same Session Experience Contract doctrine as web.
 */

export interface MobileAdminNavItem {
  id: string;
  label: string;
  href: string;
  requiredWorkspaces: string[];
}

export const MOBILE_ADMIN_NAV_ITEMS: MobileAdminNavItem[] = [
  {
    id: "administration_governance",
    label: "Administration & Governance",
    href: "/work/administration-governance",
    requiredWorkspaces: ["*"],
  },
  {
    id: "onboard",
    label: "Onboard New Actor",
    href: "/work/administration-governance/onboard",
    requiredWorkspaces: ["*"],
  },
  {
    id: "organisation_users",
    label: "My Organisation Users",
    href: "/work/administration-governance/organisations",
    requiredWorkspaces: [
      "mohcc_head_office_user_management",
      "municipal_user_management",
      "private_facility_user_management",
      "marketplace_user_management",
    ],
  },
  {
    id: "facility_staff",
    label: "Facility Staff & Access",
    href: "/work/facility/staff-access",
    requiredWorkspaces: ["public_facility_staff_management", "facility_work_assignment"],
  },
  {
    id: "madi_users",
    label: "Madi Users",
    href: "/work/madi/users",
    requiredWorkspaces: ["blood_service_user_management"],
  },
  {
    id: "marketplace_users",
    label: "Marketplace Users",
    href: "/work/marketplace/organisations",
    requiredWorkspaces: ["marketplace_user_management"],
  },
  {
    id: "access_requests",
    label: "Access Requests",
    href: "/work/administration-governance/access-requests",
    requiredWorkspaces: ["*"],
  },
  {
    id: "audit_review",
    label: "Audit Review",
    href: "/work/administration-governance/access-review",
    requiredWorkspaces: ["national_audit_and_compliance", "regulatory_audit", "hsc_audit_workspace"],
  },
];

function workspaceAllowed(
  visibleManagementWorkspaces: string[] | undefined,
  visibleWorkspaces: string[] | undefined,
  required: string[],
): boolean {
  const visible = new Set([...(visibleManagementWorkspaces ?? []), ...(visibleWorkspaces ?? [])]);
  if (required.includes("*")) {
    return visible.size > 0;
  }
  return required.some((w) => visible.has(w));
}

export function resolveMobileAdministrationNav(input: {
  visibleManagementWorkspaces?: string[];
  visibleWorkspaces?: string[];
  workTabVisible?: boolean;
}): MobileAdminNavItem[] {
  if (input.workTabVisible === false) return [];
  return MOBILE_ADMIN_NAV_ITEMS.filter((item) =>
    workspaceAllowed(input.visibleManagementWorkspaces, input.visibleWorkspaces, item.requiredWorkspaces),
  );
}
