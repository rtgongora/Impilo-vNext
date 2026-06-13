"use client";

/**
 * UtilityStrip -- Thin persistent strip above main content.
 * Shows: current workspace, patient context / location, active CDS alerts count,
 * help trigger, facility selector, and user info.
 *
 * Adapted for vNext -- no shadcn, no Supabase, Next.js 14 patterns.
 */

import Link from "next/link";
import {
  Building2,
  Bell,
  HelpCircle,
  User,
  Activity,
} from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { PatientLocationBadge } from "@/components/layout/PatientLocationBadge";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useCDSAlerts } from "@/hooks/queries/useCDSAlerts";

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function UtilityStrip() {
  const { user } = useAuthStore();
  const { facility } = useFacilityStore();
  const { workspace } = useWorkspaceStore();

  const { data: cdsData } = useCDSAlerts();
  const activeCDSAlerts = cdsData?.data?.count ?? 0;

  return (
    <div className="h-9 min-h-[2.25rem] shrink-0 bg-card border-b border-border flex items-center justify-between px-3 z-50">
      {/* Left: Logo + Facility */}
      <div className="flex items-center gap-3">
        <ImpiloBrandLogo variant="full" size={20} />
        <div className="h-4 w-px bg-neutral-100" />
        {facility ? (
          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Building2 className="h-3.5 w-3.5" />
            {facility.name}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">No facility</span>
        )}
      </div>

      {/* Center: Patient Location Context */}
      <PatientLocationBadge />

      {/* Right: CDS Alerts, Help, Workspace, User */}
      <div className="flex items-center gap-2">
        {/* Active CDS Alerts */}
        {activeCDSAlerts > 0 && (
          <button
            className="relative flex items-center gap-1 px-2 py-1 text-xs text-warning-foreground bg-warning-soft border border-warning/35 rounded-md hover:bg-amber-100 transition-colors"
            title="Active CDS alerts"
          >
            <Activity className="h-3.5 w-3.5" />
            <span className="font-medium">{activeCDSAlerts} alerts</span>
          </button>
        )}

        {/* Notifications */}
        <button
          className="relative p-1.5 text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded-md transition-colors"
          title="Notifications"
        >
          <Bell className="h-3.5 w-3.5" />
          <span className="absolute -top-0.5 -right-0.5 h-3.5 w-3.5 bg-red-500 text-white text-[8px] font-bold rounded-full flex items-center justify-center">
            2
          </span>
        </button>

        {/* Help */}
        <Link
          href="/help"
          className="p-1.5 text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded-md transition-colors"
          title="Help"
        >
          <HelpCircle className="h-3.5 w-3.5" />
        </Link>

        {/* Workspace indicator */}
        {workspace && (
          <span className="text-[10px] font-medium text-green-700 bg-green-50 border border-green-200 rounded px-2 py-0.5">
            {workspace.name}
          </span>
        )}

        <div className="h-4 w-px bg-neutral-100" />

        {/* User */}
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <User className="h-3.5 w-3.5" />
          {user?.displayName || user?.email || "Unknown"}
        </span>
      </div>
    </div>
  );
}
