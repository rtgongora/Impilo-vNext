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
import { ExperienceSidebar } from "./navigation/ExperienceSidebar";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility, workspace, shiftActive } = useExperienceEntry();

  return (
    <div className="flex h-screen bg-gray-50">
      <ExperienceSidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b bg-white px-6 flex items-center justify-between shrink-0">
          <div className="ml-12 flex items-center gap-3 text-sm text-gray-500 md:ml-0">
            {facility && (
              <Link
                href="/facility"
                className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700"
              >
                {facility.name}
              </Link>
            )}
            {workspace && (
              <Link
                href="/workspace"
                className="rounded-full bg-green-50 px-2.5 py-1 text-xs font-medium text-green-700"
              >
                {workspace.name}
              </Link>
            )}
            {shiftActive && (
              <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700">
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
