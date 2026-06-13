"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { ENTERPRISE_NAV_ITEMS, filterEnterpriseNavItems } from "@/lib/enterprise-resource-nav";

export function EnterpriseSubnav() {
  const pathname = usePathname();
  const { hasRole } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const items = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, Boolean(facility));

  return (
    <nav
      aria-label="Enterprise resources"
      className="mb-6 rounded-2xl border border-border bg-card p-4 shadow-sm"
    >
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Enterprise resource plane</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {items.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              title={item.description}
              className={[
                "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                active
                  ? "border-impilo-500 bg-primary-soft text-impilo-900"
                  : "border-border text-foreground hover:border-border hover:bg-background",
              ].join(" ")}
            >
              {item.label}
            </Link>
          );
        })}
      </div>
      {!facility ? (
        <p className="mt-3 text-xs text-warning-foreground">
          Select a facility to unlock facility-scoped inventory, pharmacy stock, and charge-sheet shortcuts.
        </p>
      ) : null}
    </nav>
  );
}
