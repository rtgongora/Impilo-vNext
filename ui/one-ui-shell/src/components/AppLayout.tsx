"use client";

/**
 * AppLayout — Standard application layout with off-canvas zone navigation (Experience sidebar).
 * Layout variant: "app" (used by most non-EHR routes)
 *
 * Structure:
 *   [Off-canvas zone nav] [Main Content Area] [Header Bar]
 */

import { type ReactNode, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ArrowLeft, Home, Menu, SlidersHorizontal, X } from "lucide-react";
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

type AppLayoutChrome = "standard" | "learning-workbench";

function learningBackTarget(pathname: string): { href: string; label: string } | null {
  if (pathname === "/learning") return null;
  if (/^\/learning\/enrolments\/[^/]+\/lessons\/[^/]+/.test(pathname)) {
    const enrolmentId = pathname.split("/")[3];
    return { href: `/learning/enrolments/${enrolmentId}`, label: "Back to enrolment" };
  }
  if (/^\/learning\/enrolments\/[^/]+/.test(pathname)) {
    return { href: "/learning?focus=learning", label: "Back to My learning" };
  }
  if (/^\/learning\/courses\/[^/]+/.test(pathname)) {
    return { href: "/learning?focus=catalogue", label: "Back to Catalogue" };
  }
  if (pathname === "/learning/catalog") {
    return { href: "/learning?focus=catalogue", label: "Back to Catalogue" };
  }
  if (pathname === "/learning/my-learning") {
    return { href: "/learning?focus=learning", label: "Back to My learning" };
  }
  if (/^\/learning\/pathways\/[^/]+/.test(pathname)) {
    return { href: "/learning/pathways", label: "Back to Pathways" };
  }
  if (pathname === "/learning/pathways") {
    return { href: "/learning?focus=pathways", label: "Back to Pathways" };
  }
  if (/^\/learning\/certificates\/[^/]+/.test(pathname)) {
    return { href: "/learning/certificates", label: "Back to Certificates" };
  }
  if (pathname === "/learning/certificates" || pathname === "/learning/cpd" || pathname === "/learning/record") {
    return { href: "/learning?focus=evidence", label: "Back to Evidence" };
  }
  if (/^\/learning\/reports\/[^/]+/.test(pathname)) {
    return { href: "/learning/reports", label: "Back to Reports" };
  }
  if (pathname === "/learning/reports") {
    return { href: "/learning?focus=reports", label: "Back to Reports" };
  }
  if (/^\/learning\/admin\/courses\/new/.test(pathname) || /^\/learning\/admin\/courses\/[^/]+\/edit/.test(pathname)) {
    return { href: "/learning/admin/courses", label: "Back to Admin courses" };
  }
  if (/^\/learning\/admin\/pathways\/new/.test(pathname) || /^\/learning\/admin\/pathways\/[^/]+\/edit/.test(pathname)) {
    return { href: "/learning/admin/pathways", label: "Back to Admin pathways" };
  }
  if (/^\/learning\/admin\/assessments\/new/.test(pathname) || /^\/learning\/admin\/assessments\/[^/]+\/edit/.test(pathname)) {
    return { href: "/learning/admin/assessments", label: "Back to Admin assessments" };
  }
  if (
    pathname === "/learning/admin" ||
    pathname === "/learning/admin/courses" ||
    pathname === "/learning/admin/pathways" ||
    pathname === "/learning/admin/assessments"
  ) {
    return { href: "/learning?focus=authoring", label: "Back to Authoring" };
  }
  if (/^\/learning\/assessments\/[^/]+\/attempt/.test(pathname)) {
    const assessmentId = pathname.split("/")[3];
    return { href: `/learning/assessments/${assessmentId}`, label: "Back to assessment" };
  }
  if (/^\/learning\/assessments\/[^/]+/.test(pathname) || /^\/learning\/attempts\/[^/]+/.test(pathname)) {
    return { href: "/learning?focus=learning", label: "Back to My learning" };
  }
  return { href: "/learning", label: "Back to Impilo Fundo" };
}

export function AppLayout({
  children,
  chrome = "standard",
}: {
  children: ReactNode;
  chrome?: AppLayoutChrome;
}) {
  const { user, isAuthenticated } = useAuthStore();
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const router = useRouter();
  const pathname = usePathname();
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);
  const compact = chrome === "learning-workbench" || pathname === "/learning" || pathname.startsWith("/learning/");
  const [shellToolsOpen, setShellToolsOpen] = useState(false);
  const fundoBackTarget = compact ? learningBackTarget(pathname) : null;

  return (
    <div className="flex h-screen bg-background">
      <ExperienceSidebar />
      <div className="relative flex-1 flex flex-col min-w-0">
        <header className={`${compact ? "h-12" : "h-16"} border-b border-[color:var(--border-soft)] bg-[color:var(--surface)] px-4 sm:px-6 flex items-center justify-between gap-2 shrink-0`}>
          <div className="flex min-w-0 flex-1 items-center gap-2 text-sm text-[color:var(--text-muted)]">
            <button
              type="button"
              onClick={() => toggleNavDrawer()}
              className="impilo-btn-secondary shrink-0 gap-2 px-3 py-1.5 text-xs"
              title="Work zones — clinical, professional, and personal navigation"
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
              <span className="hidden shrink-0 rounded-full border border-[color:var(--warning)]/30 bg-[color:var(--warning-soft)] px-2.5 py-1 text-xs font-medium text-amber-700 xl:inline-flex">
                Shift Active
              </span>
            )}
            <ModuleBreadcrumb />
            {fundoBackTarget ? (
              <Link
                href={fundoBackTarget.href}
                className="ml-1 hidden shrink-0 items-center gap-1.5 rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1.5 text-xs font-medium text-[color:var(--text-secondary)] hover:bg-[color:var(--surface)] md:inline-flex"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                {fundoBackTarget.label}
              </Link>
            ) : null}
          </div>
          <div className="flex items-center gap-4">
            {compact ? (
              <button
                type="button"
                onClick={() => setShellToolsOpen((open) => !open)}
                className="inline-flex items-center gap-2 rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-1.5 text-xs font-medium text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)]"
                aria-expanded={shellToolsOpen}
                aria-controls="learning-shell-tools"
              >
                <SlidersHorizontal className="h-4 w-4" />
                Shell Tools
              </button>
            ) : null}
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
        {compact && shellToolsOpen ? (
          <div id="learning-shell-tools" className="absolute inset-x-0 top-12 z-50">
            <button
              type="button"
              className="absolute inset-x-0 top-0 h-screen cursor-default bg-slate-950/10"
              aria-label="Close shell tools"
              onClick={() => setShellToolsOpen(false)}
            />
            <div className="relative border-b border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-4 py-3 shadow-lg sm:px-6">
              <div className="mx-auto max-w-[1600px] space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-[color:var(--text-secondary)]">
                    Shell Tools
                  </p>
                  <button
                    type="button"
                    onClick={() => setShellToolsOpen(false)}
                    className="inline-flex items-center gap-1 rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-2.5 py-1.5 text-xs font-medium text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)]"
                  >
                    <X className="h-4 w-4" />
                    Close
                  </button>
                </div>
                <NompiloGlobalCommandBar />
                <RoleJourneyNavigation />
                <AccessibilityToolbar />
                <OperationalContextStrip />
              </div>
            </div>
          </div>
        ) : null}
        {!compact ? (
          <>
            <div className="border-b border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-4 py-3 sm:px-6">
              <div className="space-y-2">
                <NompiloGlobalCommandBar />
                <RoleJourneyNavigation />
                <AccessibilityToolbar />
              </div>
            </div>
            <OperationalContextStrip />
          </>
        ) : null}
        <main className={`${compact ? "p-3 pb-4 md:p-4" : "p-4 pb-[var(--shell-taskbar-height,0px)] md:p-5"} flex-1 overflow-auto`}>{children}</main>
      </div>
      {compact ? null : <ProactiveAssistant />}
    </div>
  );
}
