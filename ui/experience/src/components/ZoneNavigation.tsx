"use client";

/**
 * Context-Aware Sidebar Navigation
 *
 * Lovable pattern: Sidebar switches nav groups based on URL pathname.
 * When at /home or root: shows the full 3-zone nav (Life, Work, Professional).
 * When in a specific context (clinical, admin, finance, etc.): shows
 * context-specific nav groups with a "Back to Home" link.
 *
 * Role filtering: items with requiredRoles are hidden for users who
 * lack the specified Keycloak roles. Matches backend SecurityConfig.
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

// ── Role group arrays (must match SecurityConfig.java) ───────────
const ADMIN_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"];
const FINANCE_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"];
const CLINICAL_ROLES = ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const QUEUE_ROLES = ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const DISPENSER_ROLES = ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];

// ── Nav item type ────────────────────────────────────────────────
interface NavItem {
  label: string;
  href: string;
  requiredRoles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

type PageContext = "home" | "clinical" | "admin" | "finance" | "pharmacy" | "registry" | "inventory" | "reports" | "settings";

// ── Context detection from pathname ──────────────────────────────
function getContextFromPath(pathname: string): PageContext {
  if (pathname.startsWith("/ehr") || pathname.startsWith("/queue") || pathname.startsWith("/scheduling") || pathname.startsWith("/telemedicine")) return "clinical";
  if (pathname.startsWith("/admin")) return "admin";
  if (pathname.startsWith("/finance")) return "finance";
  if (pathname.startsWith("/pharmacy")) return "pharmacy";
  if (pathname.startsWith("/registry")) return "registry";
  if (pathname.startsWith("/inventory")) return "inventory";
  if (pathname.startsWith("/reports")) return "reports";
  if (pathname.startsWith("/settings")) return "settings";
  return "home";
}

// ── Context labels ───────────────────────────────────────────────
const CONTEXT_LABELS: Record<PageContext, string> = {
  home: "Home",
  clinical: "Clinical",
  admin: "Administration",
  finance: "Finance",
  pharmacy: "Pharmacy",
  registry: "Registry",
  inventory: "Inventory",
  reports: "Reports",
  settings: "Settings",
};

// ── Nav sections per context ─────────────────────────────────────
function getNavSections(context: PageContext): NavSection[] {
  switch (context) {
    case "clinical":
      return [
        {
          title: "Quick Access",
          items: [
            { label: "Queue", href: "/queue", requiredRoles: QUEUE_ROLES },
            { label: "Scheduling", href: "/scheduling", requiredRoles: CLINICAL_ROLES },
            { label: "Telemedicine", href: "/telemedicine", requiredRoles: CLINICAL_ROLES },
          ],
        },
        {
          title: "Clinical",
          items: [
            { label: "Queue — Triage", href: "/queue/triage", requiredRoles: CLINICAL_ROLES },
            { label: "Queue — Waiting", href: "/queue/waiting", requiredRoles: QUEUE_ROLES },
            { label: "Queue — Scheduled", href: "/queue/scheduled", requiredRoles: QUEUE_ROLES },
            { label: "Patient Search", href: "/queue/search", requiredRoles: QUEUE_ROLES },
            { label: "Walk-in", href: "/queue/walk-in", requiredRoles: QUEUE_ROLES },
          ],
        },
      ];

    case "admin":
      return [
        {
          title: "Administration",
          items: [
            { label: "Dashboard", href: "/admin", requiredRoles: ADMIN_ROLES },
            { label: "Users", href: "/admin/users", requiredRoles: ADMIN_ROLES },
            { label: "Roles", href: "/admin/roles", requiredRoles: ADMIN_ROLES },
            { label: "Policies", href: "/admin/policies", requiredRoles: ADMIN_ROLES },
            { label: "Audit Trail", href: "/admin/audit", requiredRoles: ADMIN_ROLES },
            { label: "Consent", href: "/admin/consent", requiredRoles: ADMIN_ROLES },
          ],
        },
        {
          title: "Security",
          items: [
            { label: "Break Glass", href: "/admin/break-glass", requiredRoles: ADMIN_ROLES },
            { label: "Keys", href: "/admin/keys", requiredRoles: ADMIN_ROLES },
            { label: "Devices", href: "/admin/devices", requiredRoles: ADMIN_ROLES },
            { label: "Federation", href: "/admin/federation", requiredRoles: ADMIN_ROLES },
            { label: "Tenants", href: "/admin/tenants", requiredRoles: ADMIN_ROLES },
          ],
        },
      ];

    case "finance":
      return [
        {
          title: "Finance",
          items: [
            { label: "Dashboard", href: "/finance", requiredRoles: FINANCE_ROLES },
            { label: "Billing", href: "/finance/billing", requiredRoles: FINANCE_ROLES },
            { label: "Payments", href: "/finance/payments", requiredRoles: FINANCE_ROLES },
            { label: "Claims", href: "/finance/claims", requiredRoles: FINANCE_ROLES },
            { label: "Tariffs", href: "/finance/tariffs", requiredRoles: FINANCE_ROLES },
          ],
        },
      ];

    case "pharmacy":
      return [
        {
          title: "Pharmacy",
          items: [
            { label: "Dashboard", href: "/pharmacy", requiredRoles: DISPENSER_ROLES },
            { label: "Prescriptions", href: "/pharmacy/prescriptions", requiredRoles: DISPENSER_ROLES },
            { label: "Dispense", href: "/pharmacy/dispense", requiredRoles: DISPENSER_ROLES },
            { label: "Stock", href: "/pharmacy/stock", requiredRoles: DISPENSER_ROLES },
          ],
        },
      ];

    case "registry":
      return [
        {
          title: "Registry",
          items: [
            { label: "Hub", href: "/registry" },
            { label: "Providers", href: "/registry/providers" },
            { label: "Facilities", href: "/registry/facilities" },
            { label: "Products", href: "/registry/products" },
            { label: "Terminology", href: "/registry/terminology" },
          ],
        },
      ];

    case "inventory":
      return [
        {
          title: "Inventory",
          items: [
            { label: "Dashboard", href: "/inventory" },
            { label: "Movements", href: "/inventory/movements" },
            { label: "Counts", href: "/inventory/counts" },
            { label: "Requisitions", href: "/inventory/requisitions" },
          ],
        },
      ];

    case "reports":
      return [
        {
          title: "Reports",
          items: [
            { label: "Hub", href: "/reports" },
            { label: "Facility", href: "/reports/facility" },
            { label: "Clinical", href: "/reports/clinical" },
            { label: "Operational", href: "/reports/operational" },
            { label: "Custom", href: "/reports/custom" },
          ],
        },
      ];

    case "settings":
      return [
        {
          title: "Settings",
          items: [
            { label: "Overview", href: "/settings" },
            { label: "Account", href: "/settings/account" },
            { label: "Security", href: "/settings/security" },
            { label: "Notifications", href: "/settings/notifications" },
            { label: "Display", href: "/settings/display" },
            { label: "Integrations", href: "/settings/integrations" },
          ],
        },
      ];

    case "home":
    default:
      return [
        {
          title: "Life",
          items: [
            { label: "Home", href: "/home" },
            { label: "Notifications", href: "/home/notifications" },
            { label: "Profile", href: "/home/profile" },
            { label: "Preferences", href: "/home/preferences" },
          ],
        },
        {
          title: "Work",
          items: [
            { label: "Queue", href: "/queue", requiredRoles: QUEUE_ROLES },
            { label: "Scheduling", href: "/scheduling", requiredRoles: CLINICAL_ROLES },
            { label: "Telemedicine", href: "/telemedicine", requiredRoles: CLINICAL_ROLES },
            { label: "Pharmacy", href: "/pharmacy", requiredRoles: DISPENSER_ROLES },
            { label: "Inventory", href: "/inventory" },
            { label: "Marketplace", href: "/marketplace" },
            { label: "Finance", href: "/finance", requiredRoles: FINANCE_ROLES },
          ],
        },
        {
          title: "Professional",
          items: [
            { label: "Registry", href: "/registry" },
            { label: "Admin", href: "/admin", requiredRoles: ADMIN_ROLES },
            { label: "Reports", href: "/reports" },
            { label: "Settings", href: "/settings" },
          ],
        },
      ];
  }
}

// ── Component ────────────────────────────────────────────────────
export function ZoneNavigation() {
  const pathname = usePathname();
  const { hasRole } = useAuthStore();
  const { facility } = useFacilityStore();
  const { workspace } = useWorkspaceStore();
  const { shift } = useShiftStore();

  const context = getContextFromPath(pathname);
  const sections = getNavSections(context);
  const isInContext = context !== "home";

  return (
    <nav className="w-56 bg-gray-900 text-gray-300 flex flex-col shrink-0">
      {/* Header */}
      <div className="h-14 flex items-center px-4 border-b border-gray-700">
        <Link href="/home" className="text-white font-semibold text-sm hover:text-blue-300">
          Impilo Experience
        </Link>
      </div>

      {/* Context indicator + back link */}
      {isInContext && (
        <div className="px-4 py-2 border-b border-gray-700 bg-gray-800">
          <Link
            href="/home"
            className="text-xs text-gray-400 hover:text-white transition-colors"
          >
            ← Home
          </Link>
          <div className="text-xs font-semibold text-white mt-0.5">
            {CONTEXT_LABELS[context]}
          </div>
        </div>
      )}

      {/* Nav sections */}
      <div className="flex-1 overflow-y-auto py-2">
        {sections.map((section) => {
          const visibleItems = section.items.filter((item) => {
            if (item.requiredRoles && !item.requiredRoles.some((r) => hasRole(r))) return false;
            return true;
          });
          if (visibleItems.length === 0) return null;
          return (
            <div key={section.title} className="mb-4">
              <div className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-gray-500">
                {section.title}
              </div>
              {visibleItems.map((item) => {
                const isActive =
                  pathname === item.href || pathname.startsWith(item.href + "/");
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`block px-4 py-2 text-sm transition-colors ${
                      isActive
                        ? "bg-gray-800 text-white font-medium border-l-2 border-blue-400"
                        : "hover:bg-gray-800 hover:text-white"
                    }`}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </div>

      {/* Footer: facility/workspace/shift */}
      <div className="border-t border-gray-700 p-3 space-y-1">
        {facility ? (
          <Link href="/facility" className="block text-xs text-gray-400 hover:text-white truncate">
            <span className="text-gray-500">Facility:</span> {facility.name}
          </Link>
        ) : (
          <Link href="/facility" className="block text-xs text-yellow-500 hover:text-yellow-400">
            Select Facility
          </Link>
        )}
        {workspace ? (
          <Link href="/workspace" className="block text-xs text-gray-400 hover:text-white truncate">
            <span className="text-gray-500">Workspace:</span> {workspace.name}
          </Link>
        ) : facility ? (
          <Link href="/workspace" className="block text-xs text-yellow-500 hover:text-yellow-400">
            Select Workspace
          </Link>
        ) : null}
        {shift ? (
          <div className="text-xs text-green-400">Shift Active</div>
        ) : workspace ? (
          <Link href="/shift" className="block text-xs text-yellow-500 hover:text-yellow-400">
            Start Shift
          </Link>
        ) : null}
      </div>
    </nav>
  );
}
