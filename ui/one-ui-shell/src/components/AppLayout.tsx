"use client";

/**
 * AppLayout - Standard application layout with off-canvas zone navigation.
 * Layout variant: "app" (used by most non-EHR routes)
 *
 * Structure (union of one-ui-shell + ui/experience evolutions):
 *   [Off-canvas zone nav]
 *   [Header Bar (Menu, breadcrumbs, facility/workspace/shift chips, user)]
 *   [RoleJourneyNavigation + AccessibilityToolbar — compact shell affordances]
 *   [ClinicalSupportStrip - operational comms / help / system support, when authenticated]
 *   [OperationalContextStrip]
 *   [Main Content Area]
 *   Nompilo is reached via ShellChrome taskbar (Ask / Ctrl+K), not floating page chrome.
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Home, Menu } from "lucide-react";
import { ExperienceSidebar } from "./navigation/ExperienceSidebar";
import { RoleJourneyNavigation } from "./navigation/RoleJourneyNavigation";
import { ModuleBreadcrumb } from "./navigation/ModuleBreadcrumb";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { AccessibilityToolbar } from "./accessibility/AccessibilityToolbar";
import { ClinicalSupportStrip } from "@/components/clinical/ClinicalSupportStrip";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const router = useRouter();
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);

  return (
    <div className="flex h-screen bg-background">
      <ExperienceSidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-16 border-b border-[color:var(--border-soft)] bg-[color:var(--surface)] px-4 sm:px-6 flex items-center justify-between gap-2 shrink-0">
          <div className="flex min-w-0 flex-1 items-center gap-2 text-sm text-[color:var(--text-muted)]">
            <button
              type="button"
              onClick={() => toggleNavDrawer()}
              className="impilo-btn-secondary shrink-0 gap-2 px-3 py-1.5 text-xs"
              title="Work zones - clinical, professional, and personal navigation"
              aria-label="Open work zones menu"
            >
              <Menu className="h-4 w-4" />
              <span className="hidden sm:inline">Menu</span>
            </button>
            <button
              onClick={() => router.back()}
              className="p-1.5 rounded-full text-[color:var(--text-muted)] hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)] transition-colors"
              title="Go back"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <Link
              href="/home"
              className="p-1.5 rounded-full text-[color:var(--text-muted)] hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)] transition-colors"
              title="Home"
            >
              <Home className="w-4 h-4" />
            </Link>
            <div className="hidden h-5 w-px bg-[color:var(--border-soft)] sm:mx-1 sm:block" />
            {facility && (
              <Link
                href="/facility"
                className="hidden shrink-0 rounded-full border border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] px-2.5 py-1 text-xs font-medium text-[color:var(--primary-hover)] sm:inline-flex"
              >
                {facility.name}
              </Link>
            )}
            {workspace && (
              <Link
                href="/workspace"
                className="hidden shrink-0 rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-xs font-medium text-[color:var(--text-secondary)] lg:inline-flex"
              >
                {workspace.name}
              </Link>
            )}
            {shiftActive && (
              <span className="hidden shrink-0 rounded-full border border-[color:var(--warning)]/30 bg-[color:var(--warning-soft)] px-2.5 py-1 text-xs font-medium text-warning-foreground xl:inline-flex">
                Shift Active
              </span>
            )}
            <ModuleBreadcrumb />
          </div>
          <div className="flex items-center gap-4">
            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <span className="text-sm text-[color:var(--text-secondary)]">{user.displayName || user.email}</span>
                <Link
                  href="/auth/logout"
                  className="text-xs text-[color:var(--text-muted)] hover:text-[color:var(--text-primary)]"
                >
                  Sign Out
                </Link>
              </div>
            ) : (
              <Link
                href="/auth/login"
                className="text-sm text-[color:var(--primary)] hover:text-[color:var(--primary-hover)]"
              >
                Sign In
              </Link>
            )}
          </div>
        </header>
        <div className="border-b border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-4 py-2 sm:px-6">
          <div className="space-y-1">
            <RoleJourneyNavigation />
            <AccessibilityToolbar />
          </div>
        </div>
        {isAuthenticated ? <ClinicalSupportStrip /> : null}
        <OperationalContextStrip />
        <main className="flex-1 overflow-auto p-4 pb-[var(--shell-taskbar-height,0px)] md:p-5">{children}</main>
      </div>
    </div>
  );
}