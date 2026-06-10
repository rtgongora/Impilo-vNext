"use client";

import { type ReactNode, useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { canAccessAdministrationPath, hasAdministrationGovernanceEntry } from "@/lib/administration-governance";
import { FriendlyBlockedState } from "./FriendlyBlockedState";

interface AdministrationGovernanceShellProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  requireGovernanceEntry?: boolean;
}

export function AdministrationGovernanceShell({
  title,
  subtitle,
  children,
  requireGovernanceEntry = true,
}: AdministrationGovernanceShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { contract, isLoading } = useSessionExperienceContract();

  useEffect(() => {
    if (isLoading || !contract) return;
    if (!contract.tabs.work.visible) {
      router.replace(`/auth/resolving?reason=no_work&returnTo=${encodeURIComponent(pathname)}`);
      return;
    }
    if (requireGovernanceEntry && !hasAdministrationGovernanceEntry(contract) && pathname === "/work/administration-governance") {
      return;
    }
    if (!canAccessAdministrationPath(contract, pathname)) {
      return;
    }
  }, [contract, isLoading, pathname, requireGovernanceEntry, router]);

  if (isLoading || !contract) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <p className="text-sm text-slate-500">Loading session experience…</p>
        </PageShell>
      </AppLayout>
    );
  }

  if (!contract.tabs.work.visible) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <FriendlyBlockedState state="no_active_work_assignment" backHref="/home" backLabel="Home" />
        </PageShell>
      </AppLayout>
    );
  }

  if (requireGovernanceEntry && pathname === "/work/administration-governance" && !hasAdministrationGovernanceEntry(contract)) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <FriendlyBlockedState state={contract.friendlyResolutionState ?? "policy_denied"} />
        </PageShell>
      </AppLayout>
    );
  }

  if (!canAccessAdministrationPath(contract, pathname)) {
    return (
      <AppLayout>
        <PageShell title={title} subtitle={subtitle}>
          <FriendlyBlockedState state={contract.friendlyResolutionState ?? "policy_denied"} />
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title={title} subtitle={subtitle}>
        {children}
      </PageShell>
    </AppLayout>
  );
}
