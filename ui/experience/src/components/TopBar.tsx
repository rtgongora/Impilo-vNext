"use client";

/**
 * TopBar — EHR-specific header bar with contextual operational actions.
 * Provides quick access to Pharmacy, Payments, Orders, Referrals, Shift Handoff
 * from within the clinical encounter context.
 */

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import {
  Pill,
  CreditCard,
  ClipboardList,
  ArrowRightLeft,
  Timer,
  Home,
  ChevronRight,
  User,
  LogOut,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";

const EHR_ACTIONS = [
  { label: "Pharmacy", href: "/pharmacy/dispense", icon: Pill },
  { label: "Payments", href: "/finance/billing", icon: CreditCard },
  { label: "Orders", href: "", icon: ClipboardList, dynamic: true },
  { label: "Referrals", href: "", icon: ArrowRightLeft, dynamic: true },
  { label: "Shift Handoff", href: "/shift/handover", icon: Timer },
] as const;

export function TopBar() {
  const params = useParams();
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { facility } = useFacilityStore();
  const { shift } = useShiftStore();

  const patientId = params?.patientId as string | undefined;

  function getActionHref(action: (typeof EHR_ACTIONS)[number]): string {
    if ('dynamic' in action && action.dynamic && patientId) {
      if (action.label === "Orders") return `/ehr/${patientId}/orders`;
      if (action.label === "Referrals") return `/ehr/${patientId}/referrals`;
    }
    return action.href;
  }

  const breadcrumbs = buildBreadcrumbs(pathname, patientId);

  return (
    <header className="h-12 border-b bg-white px-4 flex items-center gap-2 shrink-0">
      <Link
        href="/home"
        className="text-gray-400 hover:text-blue-600 transition-colors"
        title="Home"
      >
        <Home className="w-4 h-4" />
      </Link>

      {breadcrumbs.map((crumb, i) => (
        <span key={i} className="flex items-center gap-1">
          <ChevronRight className="w-3 h-3 text-gray-300" />
          {crumb.href ? (
            <Link
              href={crumb.href}
              className="text-xs text-blue-600 hover:text-blue-800 font-medium"
            >
              {crumb.label}
            </Link>
          ) : (
            <span className="text-xs text-gray-700 font-medium">
              {crumb.label}
            </span>
          )}
        </span>
      ))}

      <div className="flex-1" />

      <nav className="flex items-center gap-1">
        {EHR_ACTIONS.map((action) => {
          const href = getActionHref(action);
          const Icon = action.icon;
          if (!href) return null;
          return (
            <Link
              key={action.label}
              href={href}
              className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-gray-600 hover:bg-gray-100 hover:text-gray-900 rounded-md transition-colors"
              title={action.label}
            >
              <Icon className="w-3.5 h-3.5" />
              <span className="hidden lg:inline">{action.label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="ml-2 pl-2 border-l flex items-center gap-3">
        {facility && (
          <span className="bg-blue-50 text-blue-700 px-2 py-0.5 rounded text-xs font-medium">
            {facility.name}
          </span>
        )}
        {shift && (
          <span className="bg-amber-50 text-amber-700 px-2 py-0.5 rounded text-xs font-medium">
            Shift Active
          </span>
        )}
        {user && (
          <span className="text-xs text-gray-500 flex items-center gap-1">
            <User className="w-3 h-3" />
            {user.displayName || user.email}
          </span>
        )}
        <Link
          href="/auth/logout"
          className="text-gray-400 hover:text-red-500 transition-colors"
          title="Sign Out"
        >
          <LogOut className="w-3.5 h-3.5" />
        </Link>
      </div>
    </header>
  );
}

function buildBreadcrumbs(
  pathname: string,
  patientId?: string
): Array<{ label: string; href?: string }> {
  const crumbs: Array<{ label: string; href?: string }> = [];

  if (pathname.startsWith("/ehr")) {
    crumbs.push({ label: "Queue", href: "/queue" });

    if (patientId) {
      crumbs.push({ label: "Patient Chart", href: `/ehr/${patientId}` });

      const segments = pathname.split("/").filter(Boolean);
      if (segments.length > 2) {
        const section = segments[2];
        if (section === "encounter") {
          crumbs.push({ label: "Encounter" });
        } else {
          const label = section.charAt(0).toUpperCase() + section.slice(1);
          crumbs.push({ label });
        }
      }
    }
  }

  return crumbs;
}
