"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { canAccessVashandiPath, hasVashandiEntry } from "@/lib/vashandi/access";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface VashandiShellProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  requireDashboard?: boolean;
}

export function VashandiShell({ title, subtitle, children, requireDashboard = false }: VashandiShellProps) {
  const pathname = usePathname();
  const { contract, isLoading } = useSessionExperienceContract();

  if (isLoading || !contract) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <p className="text-sm text-muted-foreground">Loading session experience…</p>
        </PageShell>
      </AppLayout>
    );
  }

  if (!contract.tabs.work.visible) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <VashandiFriendlyBlockedState state="no_active_work_assignment" backHref="/home" backLabel="Home" />
        </PageShell>
      </AppLayout>
    );
  }

  if (requireDashboard && pathname === "/work/vashandi" && !hasVashandiEntry(contract)) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <VashandiFriendlyBlockedState state={contract.vashandiFriendlyResolutionState ?? "vashandi_no_workspace_authority"} />
        </PageShell>
      </AppLayout>
    );
  }

  if (!canAccessVashandiPath(contract, pathname)) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <VashandiFriendlyBlockedState state={contract.vashandiFriendlyResolutionState ?? "vashandi_scope_required"} />
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title={title} subtitle={subtitle}>
        <div className="mb-4">
          <Link href="/work/vashandi" className="text-sm text-muted-foreground hover:text-foreground">
            ← Vashandi Workforce
          </Link>
        </div>
        {children}
      </PageShell>
    </AppLayout>
  );
}
