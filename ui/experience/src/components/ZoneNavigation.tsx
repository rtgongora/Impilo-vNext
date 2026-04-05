"use client";

/**
 * Context-Aware Collapsible Sidebar Navigation
 *
 * Lovable pattern: sidebar switches nav groups by URL pathname,
 * supports collapsed (icon-only) and expanded modes.
 *
 * Contexts: home, clinical, admin, finance, pharmacy, registry,
 * inventory, reports, settings — each with context-specific nav groups.
 */

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Home, Bell, User, Settings as SettingsIcon, Users, Calendar,
  Video, Pill, Package, ShoppingCart, Receipt, BookOpen, Shield,
  BarChart3, ChevronLeft, ChevronRight, Search, UserPlus,
  Clock, ClipboardList, FileText, Building2, Activity, Lock,
  Plug, Monitor, Eye, Database, Layers, Beaker, Globe, Hash,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useWorkModeStore, type WorkMode, WORK_MODE_LABELS } from "@/hooks/useWorkModeStore";

// ── Work mode indicator colors ───────────────────────────────────
const WORK_MODE_COLORS: Record<WorkMode, string> = {
  clinical: "bg-green-400",
  pharmacy: "bg-emerald-400",
  admin: "bg-red-400",
  finance: "bg-blue-400",
  oversight: "bg-purple-400",
  independent_practice: "bg-amber-400",
  emergency_response: "bg-red-500",
  community_outreach: "bg-teal-400",
  general: "bg-gray-400",
};

// ── Role group arrays (must match SecurityConfig.java) ───────────
const ADMIN_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"];
const FINANCE_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"];
const CLINICAL_ROLES = ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const QUEUE_ROLES = ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const DISPENSER_ROLES = ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];

// ── Types ────────────────────────────────────────────────────────
interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  requiredRoles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

type PageContext = "home" | "clinical" | "admin" | "finance" | "pharmacy" | "registry" | "inventory" | "reports" | "settings";

// ── Context detection ────────────────────────────────────────────
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

const CONTEXT_LABELS: Record<PageContext, string> = {
  home: "Home", clinical: "Clinical", admin: "Administration",
  finance: "Finance", pharmacy: "Pharmacy", registry: "Registry",
  inventory: "Inventory", reports: "Reports", settings: "Settings",
};

// ── Nav sections per context ─────────────────────────────────────
function getNavSections(context: PageContext): NavSection[] {
  switch (context) {
    case "clinical":
      return [
        {
          title: "Quick Access",
          items: [
            { label: "Queue", href: "/queue", icon: Users, requiredRoles: QUEUE_ROLES },
            { label: "Scheduling", href: "/scheduling", icon: Calendar, requiredRoles: CLINICAL_ROLES },
            { label: "Telemedicine", href: "/telemedicine", icon: Video, requiredRoles: CLINICAL_ROLES },
          ],
        },
        {
          title: "Clinical",
          items: [
            { label: "Triage", href: "/queue/triage", icon: Activity, requiredRoles: CLINICAL_ROLES },
            { label: "Waiting", href: "/queue/waiting", icon: Clock, requiredRoles: QUEUE_ROLES },
            { label: "Scheduled", href: "/queue/scheduled", icon: Calendar, requiredRoles: QUEUE_ROLES },
            { label: "Patient Search", href: "/queue/search", icon: Search, requiredRoles: QUEUE_ROLES },
            { label: "Walk-in", href: "/queue/walk-in", icon: UserPlus, requiredRoles: QUEUE_ROLES },
          ],
        },
      ];
    case "admin":
      return [
        {
          title: "Administration",
          items: [
            { label: "Dashboard", href: "/admin", icon: Shield, requiredRoles: ADMIN_ROLES },
            { label: "Users", href: "/admin/users", icon: Users, requiredRoles: ADMIN_ROLES },
            { label: "Roles", href: "/admin/roles", icon: Lock, requiredRoles: ADMIN_ROLES },
            { label: "Policies", href: "/admin/policies", icon: FileText, requiredRoles: ADMIN_ROLES },
            { label: "Audit Trail", href: "/admin/audit", icon: Eye, requiredRoles: ADMIN_ROLES },
            { label: "Consent", href: "/admin/consent", icon: ClipboardList, requiredRoles: ADMIN_ROLES },
          ],
        },
        {
          title: "Security",
          items: [
            { label: "Break Glass", href: "/admin/break-glass", icon: Shield, requiredRoles: ADMIN_ROLES },
            { label: "Keys", href: "/admin/keys", icon: Lock, requiredRoles: ADMIN_ROLES },
            { label: "Devices", href: "/admin/devices", icon: Monitor, requiredRoles: ADMIN_ROLES },
            { label: "Federation", href: "/admin/federation", icon: Globe, requiredRoles: ADMIN_ROLES },
            { label: "Tenants", href: "/admin/tenants", icon: Layers, requiredRoles: ADMIN_ROLES },
          ],
        },
      ];
    case "finance":
      return [{
        title: "Finance",
        items: [
          { label: "Dashboard", href: "/finance", icon: Receipt, requiredRoles: FINANCE_ROLES },
          { label: "Billing", href: "/finance/billing", icon: FileText, requiredRoles: FINANCE_ROLES },
          { label: "Payments", href: "/finance/payments", icon: Receipt, requiredRoles: FINANCE_ROLES },
          { label: "Claims", href: "/finance/claims", icon: ClipboardList, requiredRoles: FINANCE_ROLES },
          { label: "Tariffs", href: "/finance/tariffs", icon: Hash, requiredRoles: FINANCE_ROLES },
        ],
      }];
    case "pharmacy":
      return [{
        title: "Pharmacy",
        items: [
          { label: "Dashboard", href: "/pharmacy", icon: Pill, requiredRoles: DISPENSER_ROLES },
          { label: "Prescriptions", href: "/pharmacy/prescriptions", icon: FileText, requiredRoles: DISPENSER_ROLES },
          { label: "Dispense", href: "/pharmacy/dispense", icon: Pill, requiredRoles: DISPENSER_ROLES },
          { label: "Stock", href: "/pharmacy/stock", icon: Package, requiredRoles: DISPENSER_ROLES },
        ],
      }];
    case "registry":
      return [{
        title: "Registry",
        items: [
          { label: "Hub", href: "/registry", icon: Database },
          { label: "Providers", href: "/registry/providers", icon: User },
          { label: "Facilities", href: "/registry/facilities", icon: Building2 },
          { label: "Products", href: "/registry/products", icon: Package },
          { label: "Terminology", href: "/registry/terminology", icon: BookOpen },
        ],
      }];
    case "inventory":
      return [{
        title: "Inventory",
        items: [
          { label: "Dashboard", href: "/inventory", icon: Package },
          { label: "Movements", href: "/inventory/movements", icon: Activity },
          { label: "Counts", href: "/inventory/counts", icon: ClipboardList },
          { label: "Requisitions", href: "/inventory/requisitions", icon: FileText },
        ],
      }];
    case "reports":
      return [{
        title: "Reports",
        items: [
          { label: "Hub", href: "/reports", icon: BarChart3 },
          { label: "Facility", href: "/reports/facility", icon: Building2 },
          { label: "Clinical", href: "/reports/clinical", icon: Activity },
          { label: "Operational", href: "/reports/operational", icon: Beaker },
          { label: "Custom", href: "/reports/custom", icon: FileText },
        ],
      }];
    case "settings":
      return [{
        title: "Settings",
        items: [
          { label: "Overview", href: "/settings", icon: SettingsIcon },
          { label: "Account", href: "/settings/account", icon: User },
          { label: "Security", href: "/settings/security", icon: Lock },
          { label: "Notifications", href: "/settings/notifications", icon: Bell },
          { label: "Display", href: "/settings/display", icon: Monitor },
          { label: "Integrations", href: "/settings/integrations", icon: Plug },
        ],
      }];
    case "home":
    default:
      return [
        {
          title: "Life",
          items: [
            { label: "Home", href: "/home", icon: Home },
            { label: "Notifications", href: "/home/notifications", icon: Bell },
            { label: "Profile", href: "/home/profile", icon: User },
            { label: "Preferences", href: "/home/preferences", icon: SettingsIcon },
          ],
        },
        {
          title: "Work",
          items: [
            { label: "Queue", href: "/queue", icon: Users, requiredRoles: QUEUE_ROLES },
            { label: "Scheduling", href: "/scheduling", icon: Calendar, requiredRoles: CLINICAL_ROLES },
            { label: "Telemedicine", href: "/telemedicine", icon: Video, requiredRoles: CLINICAL_ROLES },
            { label: "Pharmacy", href: "/pharmacy", icon: Pill, requiredRoles: DISPENSER_ROLES },
            { label: "Inventory", href: "/inventory", icon: Package },
            { label: "Marketplace", href: "/marketplace", icon: ShoppingCart },
            { label: "Finance", href: "/finance", icon: Receipt, requiredRoles: FINANCE_ROLES },
          ],
        },
        {
          title: "Professional",
          items: [
            { label: "Registry", href: "/registry", icon: BookOpen },
            { label: "Admin", href: "/admin", icon: Shield, requiredRoles: ADMIN_ROLES },
            { label: "Reports", href: "/reports", icon: BarChart3 },
            { label: "Settings", href: "/settings", icon: SettingsIcon },
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
  const workMode = useWorkModeStore((s) => s.mode);
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === "undefined") return false;
    return sessionStorage.getItem("exp:sidebar-collapsed") === "true";
  });

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    sessionStorage.setItem("exp:sidebar-collapsed", String(next));
  }

  const context = getContextFromPath(pathname);
  const sections = getNavSections(context);
  const isInContext = context !== "home";

  // Auto-sync work mode from URL context
  const setWorkMode = useWorkModeStore((s) => s.setMode);
  const contextToMode: Partial<Record<PageContext, WorkMode>> = {
    clinical: "clinical", admin: "admin", finance: "finance", pharmacy: "pharmacy",
  };
  const inferredMode = contextToMode[context];
  if (inferredMode && inferredMode !== workMode) {
    setWorkMode(inferredMode);
  }

  return (
    <nav className={`bg-gray-900 text-gray-300 flex flex-col shrink-0 transition-all duration-200 ${collapsed ? "w-14" : "w-56"}`}>
      {/* Header */}
      <div className="h-14 flex items-center justify-between px-3 border-b border-gray-700">
        {!collapsed && (
          <Link href="/home" className="text-white font-semibold text-sm hover:text-blue-300 truncate">
            Impilo
          </Link>
        )}
        <button
          onClick={toggleCollapsed}
          className="p-1.5 text-gray-400 hover:text-white hover:bg-gray-800 rounded transition-colors"
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>

      {/* Context indicator */}
      {isInContext && !collapsed && (
        <div className="px-4 py-2 border-b border-gray-700 bg-gray-800">
          <Link href="/home" className="text-xs text-gray-400 hover:text-white transition-colors">
            ← Home
          </Link>
          <div className="text-xs font-semibold text-white mt-0.5">
            {CONTEXT_LABELS[context]}
          </div>
        </div>
      )}
      {isInContext && collapsed && (
        <div className="py-2 border-b border-gray-700 bg-gray-800 flex justify-center">
          <Link href="/home" className="p-1 text-gray-400 hover:text-white" title="Back to Home">
            <Home className="w-4 h-4" />
          </Link>
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
              {!collapsed && (
                <div className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-gray-500">
                  {section.title}
                </div>
              )}
              {visibleItems.map((item) => {
                const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    title={collapsed ? item.label : undefined}
                    className={`flex items-center gap-3 transition-colors ${
                      collapsed ? "justify-center px-2 py-2.5" : "px-4 py-2"
                    } text-sm ${
                      isActive
                        ? "bg-gray-800 text-white font-medium border-l-2 border-blue-400"
                        : "hover:bg-gray-800 hover:text-white"
                    }`}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    {!collapsed && <span className="truncate">{item.label}</span>}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </div>

      {/* Footer: facility/workspace/shift */}
      <div className="border-t border-gray-700 p-3 space-y-1">
        {collapsed ? (
          <>
            <Link href="/home" title={facility ? facility.name : "Select Facility"} className="block text-center">
              <Building2 className={`w-4 h-4 mx-auto ${facility ? "text-gray-400" : "text-yellow-500"}`} />
            </Link>
            {shift && <Activity className="w-4 h-4 mx-auto text-green-400" />}
          </>
        ) : (
          <>
            <div className="flex items-center gap-1.5 mb-1">
              <span className={`w-1.5 h-1.5 rounded-full ${WORK_MODE_COLORS[workMode]}`} />
              <span className="text-[10px] uppercase tracking-wider text-gray-500 font-semibold truncate">
                {WORK_MODE_LABELS[workMode]}
              </span>
            </div>
            {facility ? (
              <Link href="/facility" className="block text-xs text-gray-400 hover:text-white truncate">
                <span className="text-gray-500">Facility:</span> {facility.name}
              </Link>
            ) : (
              <Link href="/home" className="block text-xs text-yellow-500 hover:text-yellow-400">
                Select Facility
              </Link>
            )}
            {workspace ? (
              <Link href="/workspace" className="block text-xs text-gray-400 hover:text-white truncate">
                <span className="text-gray-500">Workspace:</span> {workspace.name}
                <span className="ml-1 text-[10px] text-gray-500">({workspace.workspaceType})</span>
              </Link>
            ) : facility ? (
              <Link href="/workspace" className="block text-xs text-yellow-500 hover:text-yellow-400">
                Select Workspace
              </Link>
            ) : null}
            {shift ? (
              <div className="text-xs text-green-400 flex items-center gap-1">
                <Activity className="w-3 h-3" /> Shift Active
              </div>
            ) : workspace ? (
              <Link href="/shift" className="block text-xs text-yellow-500 hover:text-yellow-400">
                Start Shift
              </Link>
            ) : null}
          </>
        )}
      </div>
    </nav>
  );
}
