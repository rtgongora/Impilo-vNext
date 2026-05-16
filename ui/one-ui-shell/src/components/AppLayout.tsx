"use client";

/**
 * AppLayout — Standard application layout with off-canvas zone navigation (Experience sidebar).
 * Layout variant: "app" (used by most non-EHR routes)
 *
 * Structure:
 *   [Off-canvas zone nav] [Main Content Area] [Header Bar]
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Home, Menu } from "lucide-react";
import { ExperienceSidebar } from "./navigation/ExperienceSidebar";
import { RoleJourneyNavigation } from "./navigation/RoleJourneyNavigation";
import { ModuleBreadcrumb } from "./navigation/ModuleBreadcrumb";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { NompiloGlobalCommandBar } from "./intelligent/NompiloGlobalCommandBar";
import { ProactiveAssistant } from "./intelligent/ProactiveAssistant";
import { AccessibilityToolbar } from "./accessibility/AccessibilityToolbar";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const router = useRouter();
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);

  return (
    <div className="flex h-screen bg-gray-50">
      <ExperienceSidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b bg-white px-4 sm:px-6 flex items-center justify-between gap-2 shrink-0">
          <div className="flex min-w-0 flex-1 items-center gap-2 text-sm text-gray-500">
            <button
              type="button"
              onClick={() => toggleNavDrawer()}
              className="inline-flex shrink-0 items-center gap-2 rounded-md border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
              title="Work zones — clinical, professional, and personal navigation"
              aria-label="Open work zones menu"
            >
              <Menu className="h-4 w-4" />
              <span className="hidden sm:inline">Menu</span>
            </button>
            <button
              onClick={() => router.back()}
              className="p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
              title="Go back"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <Link
              href="/home"
              className="p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
              title="Home"
            >
              <Home className="w-4 h-4" />
            </Link>
            <div className="hidden h-5 w-px bg-gray-200 sm:mx-1 sm:block" />
            {facility && (
              <Link
                href="/facility"
                className="hidden shrink-0 rounded-full bg-impilo-50 px-2.5 py-1 text-xs font-medium text-impilo-700 sm:inline-flex"
              >
                {facility.name}
              </Link>
            )}
            {workspace && (
              <Link
                href="/workspace"
                className="hidden shrink-0 rounded-full bg-green-50 px-2.5 py-1 text-xs font-medium text-green-700 lg:inline-flex"
              >
                {workspace.name}
              </Link>
            )}
            {shiftActive && (
              <span className="hidden shrink-0 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 xl:inline-flex">
                Shift Active
              </span>
            )}
            <ModuleBreadcrumb />
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
                className="text-sm text-impilo-500 hover:text-impilo-600"
              >
                Sign In
              </Link>
            )}
          </div>
        </header>
        <div className="border-b border-slate-200 bg-slate-50 px-4 py-3 sm:px-6">
          <div className="space-y-2">
            <NompiloGlobalCommandBar />
            <RoleJourneyNavigation />
            <AccessibilityToolbar />
          </div>
        </div>
        <OperationalContextStrip />
        <main className="flex-1 overflow-auto p-4 pb-[var(--shell-taskbar-height,0px)]">{children}</main>
      </div>
      <ProactiveAssistant />
    </div>
  );
}
