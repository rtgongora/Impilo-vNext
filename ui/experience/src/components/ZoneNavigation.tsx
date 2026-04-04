"use client";

/**
 * 3-Zone Sidebar Navigation
 *
 * Zones (from 00_executive_summary.md):
 *   Work Zone — Clinical/operational (Queue, EHR, Pharmacy, Inventory, Marketplace, Finance)
 *   Professional Zone — Admin, Registry, Reports, Settings
 *   Life Zone — Home, Profile, Notifications
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

const ADMIN_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"];
const FINANCE_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"];
const CLINICAL_ROLES = ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const QUEUE_ROLES = ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];
const DISPENSER_ROLES = ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"];

interface NavItem {
  label: string;
  href: string;
  zone: "work" | "professional" | "life";
  requiredRoles?: string[];
}

const NAV_ITEMS: NavItem[] = [
  // Life Zone
  { label: "Home", href: "/home", zone: "life" },
  { label: "Notifications", href: "/home/notifications", zone: "life" },
  { label: "Profile", href: "/home/profile", zone: "life" },
  { label: "Preferences", href: "/home/preferences", zone: "life" },

  // Work Zone
  { label: "Queue", href: "/queue", zone: "work", requiredRoles: QUEUE_ROLES },
  { label: "Scheduling", href: "/scheduling", zone: "work", requiredRoles: CLINICAL_ROLES },
  { label: "Telemedicine", href: "/telemedicine", zone: "work", requiredRoles: CLINICAL_ROLES },
  { label: "Pharmacy", href: "/pharmacy", zone: "work", requiredRoles: DISPENSER_ROLES },
  { label: "Inventory", href: "/inventory", zone: "work" },
  { label: "Marketplace", href: "/marketplace", zone: "work" },
  { label: "Finance", href: "/finance", zone: "work", requiredRoles: FINANCE_ROLES },

  // Professional Zone
  { label: "Registry", href: "/registry", zone: "professional" },
  { label: "Admin", href: "/admin", zone: "professional", requiredRoles: ADMIN_ROLES },
  { label: "Reports", href: "/reports", zone: "professional" },
  { label: "Settings", href: "/settings", zone: "professional" },
];

const ZONE_LABELS: Record<string, string> = {
  life: "Life",
  work: "Work",
  professional: "Professional",
};

const ZONE_ORDER: ("life" | "work" | "professional")[] = ["life", "work", "professional"];

export function ZoneNavigation() {
  const pathname = usePathname();
  const { hasRole } = useAuthStore();
  const { facility } = useFacilityStore();
  const { workspace } = useWorkspaceStore();
  const { shift } = useShiftStore();

  return (
    <nav className="w-56 bg-gray-900 text-gray-300 flex flex-col shrink-0">
      <div className="h-14 flex items-center px-4 border-b border-gray-700">
        <Link href="/home" className="text-white font-semibold text-sm hover:text-blue-300">
          Impilo Experience
        </Link>
      </div>

      <div className="flex-1 overflow-y-auto py-2">
        {ZONE_ORDER.map((zone) => (
          <div key={zone} className="mb-4">
            <div className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-gray-500">
              {ZONE_LABELS[zone]}
            </div>
            {NAV_ITEMS.filter((item) => {
              if (item.zone !== zone) return false;
              if (item.requiredRoles && !item.requiredRoles.some((r) => hasRole(r))) return false;
              return true;
            }).map((item) => {
              const isActive =
                pathname === item.href || pathname.startsWith(item.href + "/");
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`block px-4 py-2 text-sm transition-colors ${
                    isActive
                      ? "bg-gray-800 text-white font-medium"
                      : "hover:bg-gray-800 hover:text-white"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
      </div>

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
