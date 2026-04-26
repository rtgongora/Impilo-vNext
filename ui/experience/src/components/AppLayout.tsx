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
import { useRouter } from "next/navigation";
import { ArrowLeft, Home } from "lucide-react";
import { ExperienceSidebar } from "./navigation/ExperienceSidebar";
import { OperationalContextStrip } from "./experience/OperationalContextStrip";
import { ProactiveAssistant } from "./intelligent/ProactiveAssistant";
import { ClinicalSupportStrip } from "@/components/clinical/ClinicalSupportStrip";
import { FloatingClinicalAssist } from "@/components/clinical/FloatingClinicalAssist";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const router = useRouter();

  return (
    <div className="flex h-screen bg-gray-50">
      <ExperienceSidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b bg-white px-6 flex items-center justify-between shrink-0">
          <div className="ml-12 flex items-center gap-2 text-sm text-gray-500 md:ml-0">
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
            <div className="w-px h-5 bg-gray-200 mx-1" />
            {facility && (
              <Link
                href="/facility"
                className="rounded-full bg-impilo-50 px-2.5 py-1 text-xs font-medium text-impilo-700"
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
                className="text-sm text-impilo-500 hover:text-impilo-600"
              >
                Sign In
              </Link>
            )}
          </div>
        </header>
        {isAuthenticated ? <ClinicalSupportStrip /> : null}
        <OperationalContextStrip />
        <main className="flex-1 overflow-auto p-4 pb-[var(--shell-taskbar-height,0px)]">{children}</main>
      </div>
      <ProactiveAssistant />
      {isAuthenticated ? <FloatingClinicalAssist /> : null}
    </div>
  );
}
