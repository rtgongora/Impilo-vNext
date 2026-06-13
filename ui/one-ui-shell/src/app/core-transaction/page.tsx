"use client";

import Link from "next/link";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function CoreTransactionOverviewPage() {
  const feed = useCoreTransactionFeed();

  return (
    <PlaneWorkspaceShell
      title="Core transactions"
      subtitle="Transaction state, audit correlation, trust context, and Nompilo handoff across Impilo journeys"
      plane="Transaction Plane"
      maturity="partial"
      maturityDetail="BFF /internal/v1/core-transactions"
      nompiloHint="Ask Nompilo to explain a transaction state, next action, or audit correlation."
      relatedLinks={[
        {
          label: "Rx transaction journey",
          href: "/pharmacy/transaction-journey",
          description: "End-to-end Rx · pay · dispatch demonstration path",
        },
        {
          label: "Data & intelligence — Audit",
          href: "/data-intelligence/audit",
          description: "Audit intelligence and access logs",
        },
        { label: "Production Command Centre", href: "/production-command-centre" },
        { label: "Clinical hub", href: "/clinical", description: "Clinical modules and inpatient workspace" },
      ]}
    >
      <TrustContextBanner purposeOfUse="TRANSACTION_AUDIT" />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <FeatureMaturityBadge status="partial" detail="List and detail via Experience BFF" />
      </div>

      {feed.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading transactions…</p>
      ) : feed.isError ? (
        <p className="rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
          Core transaction feed unavailable. Confirm Experience BFF is running and core-transaction endpoints are
          registered.
        </p>
      ) : feed.items.length === 0 ? (
        <p className="rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted-foreground">
          No transactions in this facility scope yet. Start from the Rx journey or queue to create activity.
        </p>
      ) : (
        <ul className="space-y-2" data-testid="core-transaction-feed">
          {feed.items.slice(0, 20).map((tx) => (
            <li
              key={tx.transaction.id}
              className="rounded-xl border border-border bg-card px-4 py-3 dark:border-border dark:bg-card"
            >
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <span className="font-mono text-xs text-muted-foreground">{tx.transaction.id}</span>
                <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground">
                  {tx.transaction.currentState}
                </span>
              </div>
              <p className="mt-1 text-sm font-medium text-foreground">
                {tx.transaction.type}
                {tx.clientSummary.clientAlias ? ` · ${tx.clientSummary.clientAlias}` : null}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Purpose: {tx.trustContext.purposeOfUse} · Correlation: {tx.auditSummary.correlationId}
              </p>
              {tx.nextActions.length > 0 ? (
                <p className="mt-1 text-xs text-primary">Next: {tx.nextActions[0].label}</p>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <p className="text-xs text-muted-foreground">
        <Link href="/pharmacy/transaction-journey?patientId=CPID-ZW-00001" className="text-primary hover:underline">
          Open golden-patient Rx journey
        </Link>{" "}
        to walk a full transaction path with live BFF probes.
      </p>
    </PlaneWorkspaceShell>
  );
}
