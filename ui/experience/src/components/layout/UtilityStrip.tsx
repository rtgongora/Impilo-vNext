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
import { PatientLocationBadge } from "@/components/layout/PatientLocationBadge";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function UtilityStrip() {
  const { user } = useAuthStore();
  const { facility } = useFacilityStore();
  const { workspace } = useWorkspaceStore();

  // Mock CDS alerts count
  const activeCDSAlerts = 3;

  return (
    <div className="h-9 min-h-[2.25rem] shrink-0 bg-white border-b border-gray-200 flex items-center justify-between px-3 z-50">
      {/* Left: Logo + Facility */}
      <div className="flex items-center gap-3">
        <span className="text-sm font-bold text-blue-600 tracking-tight">
          Impilo
        </span>
        <div className="h-4 w-px bg-gray-200" />
        {facility ? (
          <span className="flex items-center gap-1.5 text-xs text-gray-600">
            <Building2 className="h-3.5 w-3.5" />
            {facility.name}
          </span>
        ) : (
          <span className="text-xs text-gray-400">No facility</span>
        )}
      </div>

      {/* Center: Patient Location Context */}
      <PatientLocationBadge />

      {/* Right: CDS Alerts, Help, Workspace, User */}
      <div className="flex items-center gap-2">
        {/* Active CDS Alerts */}
        {activeCDSAlerts > 0 && (
          <button
            className="relative flex items-center gap-1 px-2 py-1 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md hover:bg-amber-100 transition-colors"
            title="Active CDS alerts"
          >
            <Activity className="h-3.5 w-3.5" />
            <span className="font-medium">{activeCDSAlerts} alerts</span>
          </button>
        )}

        {/* Notifications */}
        <button
          className="relative p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
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
          className="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
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

        <div className="h-4 w-px bg-gray-200" />

        {/* User */}
        <span className="flex items-center gap-1.5 text-xs text-gray-600">
          <User className="h-3.5 w-3.5" />
          {user?.displayName || user?.email || "Unknown"}
        </span>
      </div>
    </div>
  );
}
