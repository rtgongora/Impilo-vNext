/**
 * Enterprise Resource Plane — role-aware navigation metadata (One UI Shell).
 *
 * `requiredRoles` is satisfied when **any** listed Keycloak realm role matches
 * (same convention as `ExperienceSidebar` zone items).
 */

const DISPENSER_ROLES = ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const FINANCE_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"];
const COMMERCE_ROLES = ["FINANCE", "CLINICIAN", "NURSE", "PHARMACIST", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const CLINICAL_ROLES = ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const PAYER_OPS = ["SYSTEM_ADMIN", "FINANCE", "DEVELOPER"];
const ADMIN_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"];

export type EnterpriseNavItem = {
  href: string;
  label: string;
  description?: string;
  /** Any of these roles may see the item. Omit for all authenticated users. */
  requiredRoles?: string[];
  /** When true, hide if no facility is selected in session. */
  requiresFacility?: boolean;
};

export const ENTERPRISE_NAV_ITEMS: EnterpriseNavItem[] = [
  { href: "/enterprise", label: "Enterprise dashboard", description: "Resource health for the active facility" },
  { href: "/inventory", label: "Commodities & inventory", requiresFacility: true },
  { href: "/pharmacy/stock", label: "Pharmacy stock", requiredRoles: DISPENSER_ROLES, requiresFacility: true },
  { href: "/inventory/requisitions", label: "Requisitions", requiresFacility: true },
  { href: "/erp/procurement", label: "Procurement", requiredRoles: [...new Set([...FINANCE_ROLES, ...COMMERCE_ROLES])] },
  { href: "/marketplace/vendor", label: "Suppliers & vendor fulfilment", requiredRoles: COMMERCE_ROLES },
  { href: "/enterprise/warehousing", label: "Warehousing & distribution", requiredRoles: ADMIN_ROLES },
  { href: "/operations/assets", label: "Assets (platform)", requiredRoles: ADMIN_ROLES },
  { href: "/enterprise/fleet", label: "Fleet & logistics", requiredRoles: ADMIN_ROLES },
  { href: "/finance", label: "Finance & billing", requiredRoles: FINANCE_ROLES },
  { href: "/finance/claims", label: "Claims", requiredRoles: FINANCE_ROLES },
  { href: "/finance/payments", label: "Payments", requiredRoles: FINANCE_ROLES },
  { href: "/finance/tariffs", label: "Tariffs & costing", requiredRoles: FINANCE_ROLES },
  { href: "/finance/reports", label: "Revenue & receipting", requiredRoles: FINANCE_ROLES },
  { href: "/finance/reconciliation", label: "Audit & reconciliation", requiredRoles: PAYER_OPS },
  { href: "/reports", label: "Reports" },
  { href: "/enterprise/charge-sheet", label: "Charge sheet (clinical)", requiredRoles: CLINICAL_ROLES, requiresFacility: true },
];

export function filterEnterpriseNavItems(
  items: EnterpriseNavItem[],
  hasRole: (role: string) => boolean,
  hasFacility: boolean,
): EnterpriseNavItem[] {
  return items.filter((item) => {
    if (item.requiresFacility && !hasFacility) return false;
    if (!item.requiredRoles?.length) return true;
    return item.requiredRoles.some((r) => hasRole(r));
  });
}
