"use client";

/**
 * Owner-only work-eligibility panel (D-P6, PJ15/PJ17). Renders the authoritative
 * server-side summary of whether the signed-in provider can work right now, and
 * the remediation when they cannot. Authentication and authorisation are
 * separate: sign-in succeeded; Work may still be unavailable; My Life / My
 * Professional are never lost — the panel always keeps those routes reachable.
 */

import Link from "next/link";
import { AlertBanner, StatusBadge } from "shared-ui";
import { BriefcaseBusiness, IdCard, HeartPulse } from "lucide-react";
import { useWorkEligibility } from "@/hooks/queries/useWorkEligibility";

export function WorkEligibilityPanel() {
  const { eligibility, isLoading } = useWorkEligibility();

  if (isLoading) {
    return (
      <div
        className="rounded-2xl border border-border bg-card p-4 text-sm text-muted-foreground"
        data-testid="work-eligibility-loading"
      >
        Checking your work status…
      </div>
    );
  }

  if (!eligibility.resolved) {
    return (
      <AlertBanner variant="pending" title="Work status unavailable">
        We couldn&apos;t confirm your work status just now. Your personal and
        professional spaces are unaffected — try again shortly.
      </AlertBanner>
    );
  }

  // Unlinked person: no professional profile yet → route to Request Provider Access.
  if (!eligibility.linked) {
    return (
      <div className="space-y-3" data-testid="work-eligibility-unlinked">
        <AlertBanner variant="info" title="No professional profile linked">
          {eligibility.remediation ??
            "No professional profile is linked to your account. Use Request Provider Access to claim or register one."}
        </AlertBanner>
        <RemediationLinks showRequestAccess />
      </div>
    );
  }

  if (eligibility.workEligible) {
    return (
      <div
        className="rounded-2xl border border-success/35 bg-success-soft p-4"
        data-testid="work-eligibility-active"
      >
        <div className="flex items-center gap-2">
          <BriefcaseBusiness className="h-4 w-4 text-success" />
          <span className="text-sm font-medium text-foreground">
            Work access is active
          </span>
          <StatusBadge variant="success">Eligible</StatusBadge>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Select a workplace to start a work session.
        </p>
      </div>
    );
  }

  // Suspended / lapsed / restricted: authn OK, Work denied, remediation shown.
  return (
    <div className="space-y-3" data-testid="work-eligibility-denied">
      <AlertBanner variant="warning" title="Professional Work access is currently unavailable">
        <p>
          {eligibility.remediation ??
            "You have signed in successfully. Open My Professional to view the status and the next steps."}
        </p>
        <p className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          {eligibility.lifecycleStatus && (
            <StatusBadge variant="blocked">{eligibility.lifecycleStatus}</StatusBadge>
          )}
          {eligibility.licenceStatus && (
            <span className="uppercase tracking-wide text-muted-foreground">
              Licence: {eligibility.licenceStatus}
            </span>
          )}
        </p>
      </AlertBanner>
      <RemediationLinks />
    </div>
  );
}

function RemediationLinks({ showRequestAccess = false }: { showRequestAccess?: boolean }) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
      <Link
        href="/professional"
        className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
      >
        <IdCard className="h-4 w-4" />
        Open My Professional
      </Link>
      {showRequestAccess && (
        <Link
          href="/provider/get-access"
          className="inline-flex items-center justify-center gap-2 rounded-lg border border-border bg-card px-4 py-2.5 text-sm font-medium text-foreground hover:bg-background"
        >
          <BriefcaseBusiness className="h-4 w-4" />
          Request Provider Access
        </Link>
      )}
      <Link
        href="/home"
        className="inline-flex items-center justify-center gap-2 rounded-lg border border-border bg-card px-4 py-2.5 text-sm font-medium text-foreground hover:bg-background"
      >
        <HeartPulse className="h-4 w-4" />
        My Life / My Health
      </Link>
    </div>
  );
}
