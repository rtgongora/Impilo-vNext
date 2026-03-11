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
import { ROUTES } from "@/lib/routes";

interface NavItem {
  label: string;
  href: string;
  zone: "work" | "professional" | "life";
}

const NAV_ITEMS: NavItem[] = [
  // Life Zone
  { label: "Home", href: "/home", zone: "life" },
  { label: "Notifications", href: "/home/notifications", zone: "life" },
  { label: "Profile", href: "/home/profile", zone: "life" },

  // Work Zone
  { label: "Queue", href: "/queue", zone: "work" },
  { label: "Pharmacy", href: "/pharmacy", zone: "work" },
  { label: "Inventory", href: "/inventory", zone: "work" },
  { label: "Marketplace", href: "/marketplace", zone: "work" },
  { label: "Finance", href: "/finance", zone: "work" },

  // Professional Zone
  { label: "Registry", href: "/registry", zone: "professional" },
  { label: "Admin", href: "/admin", zone: "professional" },
  { label: "Reports", href: "/reports", zone: "professional" },
  { label: "Settings", href: "/settings", zone: "professional" },
];

const ZONE_LABELS: Record<string, string> = {
  work: "Work",
  professional: "Professional",
  life: "Life",
};

const ZONE_ORDER: ("life" | "work" | "professional")[] = ["life", "work", "professional"];

export function ZoneNavigation() {
  const pathname = usePathname();

  return (
    <nav className="w-56 bg-gray-900 text-gray-300 flex flex-col shrink-0">
      <div className="h-14 flex items-center px-4 border-b border-gray-700">
        <span className="text-white font-semibold text-sm">Impilo Experience</span>
      </div>

      <div className="flex-1 overflow-y-auto py-2">
        {ZONE_ORDER.map((zone) => (
          <div key={zone} className="mb-4">
            <div className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-gray-500">
              {ZONE_LABELS[zone]}
            </div>
            {NAV_ITEMS.filter((item) => item.zone === zone).map((item) => {
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

      <div className="border-t border-gray-700 p-4">
        <div className="text-xs text-gray-500">Facility / Workspace / Shift context</div>
      </div>
    </nav>
  );
}
