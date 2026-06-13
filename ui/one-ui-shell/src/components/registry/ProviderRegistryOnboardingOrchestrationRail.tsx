"use client";

import Link from "next/link";
import { CheckCircle2, Loader2, UserCheck, Users } from "lucide-react";
import {
  useChangeProviderStatus,
  useDecideReconciliationCase,
  useProviderReconciliationQueue,
  useProviders,
} from "@/hooks/queries/useRegistry";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

export function ProviderRegistryOnboardingOrchestrationRail() {
  const providersQ = useProviders();
  const pendingQ = useProviders({ status: "PENDING" });
  const reconciliationQ = useProviderReconciliationQueue("PENDING");
  const decide = useDecideReconciliationCase();
  const changeStatus = useChangeProviderStatus();
  const providers = providersQ.data?.data ?? [];
  const pendingProviders = pendingQ.data?.data ?? [];
  const queuePayload = reconciliationQ.data ?? {};
  const queueItems = Array.isArray(queuePayload.items)
    ? queuePayload.items
    : Array.isArray((queuePayload as { data?: unknown }).data)
      ? ((queuePayload as { data: unknown[] }).data)
      : [];
  const queueCount = queueItems.length
    || Number(queuePayload.total_elements ?? queuePayload.totalElements ?? 0);

  return (
    <section
      className="mb-4 rounded-2xl border border-info/25 bg-info-soft/80 p-4"
      data-testid="provider-registry-onboarding-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-primary-hover">
            <Users className="h-4 w-4" />
            Provider registry onboarding orchestration
          </p>
          <p className="mt-1 text-xs text-primary-hover" data-testid="registry-provider-kpi-strip">
            {providersQ.isLoading
              ? "Loading VARAPI provider registry…"
              : `${providers.length} provider row(s) in current search scope`}
          </p>
          <p className="mt-1 text-xs text-primary-hover" data-testid="registry-reconciliation-probe">
            {reconciliationQ.isLoading
              ? "Reconciliation queue: probing VARAPI…"
              : `${queueCount} reconciliation case(s) awaiting council review`}
          </p>
          <p className="mt-1 text-xs text-primary-hover" data-testid="registry-pending-providers-probe">
            {pendingQ.isLoading
              ? "Pending providers: loading…"
              : `${pendingProviders.length} provider application(s) awaiting verification`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/registry/providers/new"
            className="inline-flex items-center gap-1 rounded-lg border border-info/25 bg-card px-2.5 py-1.5 font-medium text-primary-hover hover:border-indigo-300"
            data-testid="registry-provider-apply-link"
          >
            <UserCheck className="h-3.5 w-3.5" />
            Apply / register
          </Link>
          <Link
            href="/registry/providers/verification"
            className="inline-flex items-center gap-1 rounded-lg border border-info/25 bg-card px-2.5 py-1.5 font-medium text-primary-hover hover:border-indigo-300"
            data-testid="registry-provider-verify-queue-link"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            Verification queue
          </Link>
          <Link
            href="/registry"
            className="rounded-lg border border-info/25 bg-card px-2.5 py-1.5 font-medium text-primary-hover hover:border-indigo-300"
          >
            Registry hub
          </Link>
        </div>
      </div>

      {pendingProviders.length > 0 && (
        <ul className="mt-3 space-y-2" data-testid="registry-pending-provider-actions">
          {pendingProviders.slice(0, 3).map((provider) => {
            const attrs = provider.attributes ?? {};
            const name = String(attrs.displayName ?? provider.id);
            return (
              <li
                key={provider.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-indigo-100 bg-card px-3 py-2 text-xs text-primary-hover"
              >
                <span>{name}</span>
                <div className="flex gap-1">
                  <button
                    type="button"
                    disabled={changeStatus.isPending}
                    onClick={() =>
                      changeStatus.mutate({
                        id: provider.id,
                        newStatus: "ACTIVE",
                        reason: "Provider verified from registry onboarding rail",
                      })
                    }
                    className="rounded border border-success/25 px-2 py-1 text-primary-hover hover:bg-success-soft disabled:opacity-50"
                    data-testid={`registry-provider-verify-${provider.id}`}
                  >
                    Verify
                  </button>
                  <Link
                    href={`/registry/providers/${encodeURIComponent(provider.id)}`}
                    className="rounded border border-info/25 px-2 py-1 text-primary-hover hover:bg-info-soft"
                  >
                    Review
                  </Link>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {queueItems.length > 0 && (
        <ul className="mt-3 space-y-2" data-testid="registry-reconciliation-actions">
          {queueItems.slice(0, 3).map((raw, idx) => {
            const row = asRecord(raw);
            const caseId = Number(row.id ?? row.caseId ?? idx);
            const label = String(row.sourceRef ?? row.providerPublicId ?? row.caseType ?? `Case ${caseId}`);
            return (
              <li
                key={caseId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-indigo-100 bg-card px-3 py-2 text-xs text-primary-hover"
              >
                <span>{label}</span>
                <div className="flex gap-1">
                  <button
                    type="button"
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ caseId, action: "ACCEPT", reason: "Council import accepted from rail" })}
                    className="rounded border border-success/25 px-2 py-1 text-primary-hover hover:bg-success-soft disabled:opacity-50"
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ caseId, action: "REJECT", reason: "Council import rejected from rail" })}
                    className="rounded border border-danger/28 px-2 py-1 text-rose-800 hover:bg-danger-soft disabled:opacity-50"
                  >
                    Reject
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {(providersQ.isLoading || reconciliationQ.isLoading || pendingQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-primary-hover">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding registry → council reconciliation chain…
        </div>
      )}
    </section>
  );
}
