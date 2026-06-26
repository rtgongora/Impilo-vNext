"use client";

/**
 * AppLayout - Standard application layout with off-canvas zone navigation.
 * Slim top chrome: navigation + global account controls. Workspace tools live on the bottom dock.
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Home, Menu } from "lucide-react";
import { ExperienceSidebar } from "./navigation/ExperienceSidebar";
import { ModuleBreadcrumb } from "./navigation/ModuleBreadcrumb";
import { ActingForBanner } from "./citizen/ActingForBanner";
import { ShellTopAccountActions } from "./shell/ShellTopAccountActions";
import { useShellStore } from "@/hooks/useShellStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function AppLayout({ children }: { children: ReactNode }) {
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const router = useRouter();
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);

  return (
    <div className="flex h-screen bg-background-deep">
      <ExperienceSidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-11 shrink-0 items-center justify-between gap-2 border-b border-[color:var(--border-soft)] bg-[color:var(--surface)]/90 px-3 sm:px-4">
          <div className="flex min-w-0 flex-1 items-center gap-1.5 text-sm text-[color:var(--text-muted)]">
            <button
              type="button"
              onClick={() => toggleNavDrawer()}
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)] transition hover:text-[color:var(--primary-hover)]"
              title="Work zones menu"
              aria-label="Open work zones menu"
            >
              <Menu className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[color:var(--text-muted)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]"
              title="Go back"
              aria-label="Go back"
            >
              <ArrowLeft className="h-4 w-4" />
            </button>
            <Link
              href="/home"
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[color:var(--text-muted)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]"
              title="Home"
              aria-label="Home"
            >
              <Home className="h-4 w-4" />
            </Link>
            {facility && (
              <Link
                href="/facility"
                className="hidden max-w-[8rem] truncate rounded-full border border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] px-2 py-0.5 text-[11px] font-medium text-[color:var(--primary-hover)] sm:inline-flex"
              >
                {facility.name}
              </Link>
            )}
            {workspace && (
              <Link
                href="/workspace"
                className="hidden max-w-[8rem] truncate rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2 py-0.5 text-[11px] font-medium text-[color:var(--text-secondary)] lg:inline-flex"
              >
                {workspace.name}
              </Link>
            )}
            {shiftActive && (
              <span className="hidden rounded-full border border-[color:var(--warning)]/30 bg-[color:var(--warning-soft)] px-2 py-0.5 text-[11px] font-medium text-warning-foreground xl:inline-flex">
                On shift
              </span>
            )}
            <ModuleBreadcrumb />
          </div>
          <ShellTopAccountActions />
        </header>
        <ActingForBanner />
        <main className="impilo-canvas flex-1 overflow-auto px-3 pb-[var(--shell-taskbar-height,0px)] pt-0 md:px-4">{children}</main>
      </div>
    </div>
  );
}
