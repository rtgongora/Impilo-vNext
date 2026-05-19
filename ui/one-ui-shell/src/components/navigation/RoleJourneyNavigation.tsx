"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  CLIENT_NAVIGATION,
  MANAGER_NAVIGATION,
  PROVIDER_NAVIGATION,
  type RoleNavigationItem,
} from "@/lib/ui-route-journey-map";

function isActive(pathname: string, item: RoleNavigationItem): boolean {
  if (pathname === item.route || pathname.startsWith(`${item.route}/`)) {
    return true;
  }
  return (item.aliases ?? []).some((alias) => pathname === alias || pathname.startsWith(`${alias}/`));
}

export function RoleJourneyNavigation() {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const roles = user?.roles ?? [];
  const isProvider = roles.some((role) => ["CLINICIAN", "NURSE", "PHARMACIST"].includes(role));
  const isManager = roles.some((role) => ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE", "PUBLIC_HEALTH_OFFICER"].includes(role));
  const nav = isManager ? MANAGER_NAVIGATION : isProvider ? PROVIDER_NAVIGATION : CLIENT_NAVIGATION;

  return (
    <nav aria-label="Role and journey navigation" className="flex gap-2 overflow-x-auto py-1">
      {nav.map((item) => (
        <Link
          key={item.label}
          href={item.route}
          className={[
            "whitespace-nowrap rounded-full border px-3 py-1.5 text-xs font-medium transition",
            isActive(pathname, item)
              ? "border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
              : "border-[color:var(--border-soft)] bg-white text-[color:var(--text-secondary)] hover:border-[color:var(--border-strong)] hover:text-[color:var(--text-primary)]",
          ].join(" ")}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}

