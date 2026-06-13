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
  Video,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { PRIVACY_LEVEL_LABELS } from "@/lib/pii-mask";
import { buildBreadcrumbTrail } from "@/lib/routes";
import { Eye, EyeOff } from "lucide-react";

const EHR_ACTIONS = [
  { label: "Pharmacy", href: "/pharmacy/dispense", icon: Pill },
  { label: "Payments", href: "/finance/billing", icon: CreditCard },
  { label: "Orders", href: "", icon: ClipboardList, dynamic: true },
  { label: "Consults", href: "", icon: ArrowRightLeft, dynamic: true },
  { label: "Teleconsult", href: "/telemedicine", icon: Video },
  { label: "Shift Handoff", href: "/shift/handover", icon: Timer },
] as const;

export function TopBar() {
  const params = useParams();
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { isFinance, isDispenser } = useRoleGroup();
  const { facility } = useFacilityStore();
  const { shift } = useShiftStore();

  const patientId = params?.patientId as string | undefined;

  function getActionHref(action: (typeof EHR_ACTIONS)[number]): string {
    if ("dynamic" in action && action.dynamic && patientId) {
      if (action.label === "Orders") return `/ehr/${patientId}/orders`;
      if (action.label === "Consults") return `/ehr/${patientId}/consults`;
    }
    return action.href;
  }

  const registryTrail = buildBreadcrumbTrail(pathname ?? "/");
  const trailAfterHome = registryTrail.slice(1);
  const breadcrumbs = trailAfterHome.map((c, i) => ({
    label: c.label,
    href: i < trailAfterHome.length - 1 ? c.href : undefined,
  }));

  return (
    <header className="h-12 border-b bg-card px-4 flex items-center gap-2 shrink-0 min-w-0">
      <Link
        href="/home"
        className="text-muted-foreground hover:text-primary transition-colors"
        title="Home"
      >
        <Home className="w-4 h-4" />
      </Link>

      <nav aria-label="Breadcrumb" className="flex min-w-0 max-w-[min(100%,32rem)] items-center gap-0 overflow-x-auto">
        {breadcrumbs.map((crumb, i) => (
          <span key={`${crumb.label}-${i}`} className="flex min-w-0 items-center gap-1">
            <ChevronRight className="w-3 h-3 shrink-0 text-muted-foreground" aria-hidden />
            {crumb.href ? (
              <Link
                href={crumb.href}
                className="truncate text-xs text-primary hover:text-primary-hover font-medium"
              >
                {crumb.label}
              </Link>
            ) : (
              <span className="truncate text-xs text-foreground font-medium">{crumb.label}</span>
            )}
          </span>
        ))}
      </nav>

      {pathname.startsWith("/ehr") && (
        <Link
          href="/queue"
          className="ml-2 hidden md:inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:bg-neutral-100 hover:text-foreground"
        >
          Queue
        </Link>
      )}

      <div className="flex-1" />

      <nav className="flex items-center gap-1">
        {EHR_ACTIONS.map((action) => {
          const href = getActionHref(action);
          const Icon = action.icon;
          if (!href) return null;
          // Finance links require finance-capable role
          if (action.href.startsWith("/finance") && !isFinance) return null;
          // Pharmacy links require dispenser-capable role
          if (action.href.startsWith("/pharmacy") && !isDispenser) return null;
          return (
            <Link
              key={action.label}
              href={href}
              className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-muted-foreground hover:bg-neutral-100 hover:text-foreground rounded-md transition-colors"
              title={action.label}
            >
              <Icon className="w-3.5 h-3.5" />
              <span className="hidden lg:inline">{action.label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="ml-2 pl-2 border-l flex items-center gap-3">
        <PrivacyToggle />
        {facility && (
          <span className="bg-primary-soft text-primary px-2 py-0.5 rounded text-xs font-medium">
            {facility.name}
          </span>
        )}
        {shift && (
          <span className="bg-warning-soft text-warning-foreground px-2 py-0.5 rounded text-xs font-medium">
            Shift Active
          </span>
        )}
        {user && (
          <span className="text-xs text-muted-foreground flex items-center gap-1">
            <User className="w-3 h-3" />
            {user.displayName || user.email}
          </span>
        )}
        <Link
          href="/auth/logout"
          className="text-muted-foreground hover:text-red-500 transition-colors"
          title="Sign Out"
        >
          <LogOut className="w-3.5 h-3.5" />
        </Link>
      </div>
    </header>
  );
}

const PRIVACY_BADGE_STYLES: Record<string, string> = {
  FULL: "bg-green-50 text-green-700 border-green-200",
  PARTIAL: "bg-warning-soft text-warning-foreground border-warning/35",
  MINIMAL: "bg-danger-soft text-danger border-danger/28",
};

function PrivacyToggle() {
  const { level, cycleLevel } = usePrivacyDisplayStore();
  const PrivacyIcon = level === "FULL" ? Eye : EyeOff;

  return (
    <button
      onClick={cycleLevel}
      className={`flex items-center gap-1 px-2 py-0.5 rounded border text-xs font-medium transition-colors hover:opacity-80 ${PRIVACY_BADGE_STYLES[level]}`}
      title={`Privacy: ${PRIVACY_LEVEL_LABELS[level]} — Click to cycle`}
    >
      <PrivacyIcon className="w-3 h-3" />
      <span className="hidden sm:inline">{PRIVACY_LEVEL_LABELS[level]}</span>
    </button>
  );
}

