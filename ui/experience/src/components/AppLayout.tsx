"use client";

/**
 * AppLayout — Standard application layout with 3-zone sidebar navigation.
 * Layout variant: "app" (used by most non-EHR routes)
 *
 * Structure:
 *   [Sidebar (3-zone nav)] [Main Content Area] [Header Bar]
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { ZoneNavigation } from "./ZoneNavigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility } = useFacilityStore();
  const { workspace } = useWorkspaceStore();
  const { shift } = useShiftStore();

  return (
    <div className="flex h-screen bg-gray-50">
      <ZoneNavigation />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b bg-white px-6 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3 text-sm text-gray-500">
            {facility && (
              <span className="bg-blue-50 text-blue-700 px-2 py-0.5 rounded text-xs font-medium">
                {facility.name}
              </span>
            )}
            {workspace && (
              <span className="bg-green-50 text-green-700 px-2 py-0.5 rounded text-xs font-medium">
                {workspace.name}
              </span>
            )}
            {shift && (
              <span className="bg-amber-50 text-amber-700 px-2 py-0.5 rounded text-xs font-medium">
                Shift Active
              </span>
            )}
          </div>
          <div className="flex items-center gap-4">
            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <span className="text-sm text-gray-600">{user.displayName || user.email}</span>
                <Link
                  href="/auth/logout"
                  className="text-xs text-gray-400 hover:text-gray-600"
                >
                  Sign Out
                </Link>
              </div>
            ) : (
              <Link
                href="/auth/login"
                className="text-sm text-blue-600 hover:text-blue-700"
              >
                Sign In
              </Link>
            )}
          </div>
        </header>
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  );
}
