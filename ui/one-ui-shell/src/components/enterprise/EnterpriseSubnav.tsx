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
      className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
    >
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Enterprise resource plane</p>
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
                  ? "border-impilo-500 bg-impilo-50 text-impilo-900"
                  : "border-slate-200 text-slate-700 hover:border-slate-300 hover:bg-slate-50",
              ].join(" ")}
            >
              {item.label}
            </Link>
          );
        })}
      </div>
      {!facility ? (
        <p className="mt-3 text-xs text-amber-700">
          Select a facility to unlock facility-scoped inventory, pharmacy stock, and charge-sheet shortcuts.
        </p>
      ) : null}
    </nav>
  );
}
