"use client";

import Link from "next/link";
import { Loader2, Shield, ScrollText, AlertTriangle, Smartphone, Link2 } from "lucide-react";
import { DomainCollectionTable } from "@/components/common/DomainCollectionTable";
import {
  useBreakGlassReviews,
  useChainIntegrity,
  useTrustAuditLogs,
  type TrustAuditLogResource,
} from "@/hooks/queries/useTrustAdmin";

export function TrustGovernanceStrip() {
  const auditQ = useTrustAuditLogs(0, 5);
  const breakGlassQ = useBreakGlassReviews();
  const chainQ = useChainIntegrity();

  const pendingBreakGlass =
    breakGlassQ.data?.data?.filter((r) => r.attributes.reviewStatus === "PENDING").length ?? 0;
  const auditRows = auditQ.data?.data ?? [];
  const chain = chainQ.data?.data;

  return (
    <section className="mt-8 space-y-4 max-w-3xl">
      <div className="flex items-center gap-2">
        <Shield className="h-4 w-4 text-indigo-600" />
        <h3 className="text-sm font-semibold text-foreground">Trust governance</h3>
        <span className="text-xs text-muted-foreground">TSHEPO via BFF /internal/v1/admin/trust/*</span>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Link
          href="/admin/policies"
          className="rounded-lg border border-border bg-card p-4 hover:border-impilo-300 transition-colors"
        >
          <div className="flex items-center gap-2 text-sm font-medium text-foreground">
            <ScrollText className="h-4 w-4 text-primary" />
            Policy rules
          </div>
          <p className="mt-1 text-xs text-muted-foreground">ABAC policies and access effects</p>
        </Link>
        <Link
          href="/admin/break-glass"
          className="rounded-lg border border-border bg-card p-4 hover:border-impilo-300 transition-colors"
        >
          <div className="flex items-center gap-2 text-sm font-medium text-foreground">
            <AlertTriangle className="h-4 w-4 text-amber-500" />
            Break-glass reviews
            {pendingBreakGlass > 0 ? (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-warning-foreground">
                {pendingBreakGlass} pending
              </span>
            ) : null}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">Emergency access event adjudication</p>
        </Link>
        <Link
          href="/admin/devices"
          className="rounded-lg border border-border bg-card p-4 hover:border-impilo-300 transition-colors"
        >
          <div className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Smartphone className="h-4 w-4 text-muted-foreground" />
            Device trust profiles
          </div>
          <p className="mt-1 text-xs text-muted-foreground">Block/unblock device fingerprints</p>
        </Link>
        <Link
          href="/admin/audit"
          className="rounded-lg border border-border bg-card p-4 hover:border-impilo-300 transition-colors"
        >
          <div className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Link2 className="h-4 w-4 text-primary" />
            Trust audit log
          </div>
          <p className="mt-1 text-xs text-muted-foreground">Serialized audit chain and decisions</p>
        </Link>
      </div>

      {chainQ.isLoading ? (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3 w-3 animate-spin" />
          Verifying audit chain…
        </div>
      ) : chain ? (
        <div
          className={`rounded-lg border px-4 py-3 text-xs ${
            chain.valid
              ? "border-success/25 bg-success-soft text-primary-hover"
              : "border-danger/28 bg-danger-soft text-red-900"
          }`}
        >
          Audit chain: {chain.valid ? "valid" : "broken"} · {chain.entriesVerified} entries verified
          {!chain.valid && chain.brokenLinks.length > 0
            ? ` · broken at ${chain.brokenLinks.join(", ")}`
            : null}
        </div>
      ) : null}

      <div>
        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Recent trust decisions
        </h4>
        <DomainCollectionTable<TrustAuditLogResource>
          rows={auditRows}
          isLoading={auditQ.isLoading}
          error={auditQ.error as Error | null}
          emptyTitle="No trust audit entries yet"
          emptyHint="Decisions appear when TSHEPO policy engine evaluates access."
          rowKey={(row) => row.id}
          columns={[
            {
              key: "action",
              header: "Action",
              render: (row) => row.attributes.action,
            },
            {
              key: "resource",
              header: "Resource",
              render: (row) => `${row.attributes.resourceType}:${row.attributes.resourceId}`,
            },
            {
              key: "decision",
              header: "Decision",
              render: (row) => row.attributes.decision,
            },
            {
              key: "time",
              header: "When",
              render: (row) => new Date(row.attributes.timestamp).toLocaleString(),
            },
          ]}
        />
      </div>
    </section>
  );
}
