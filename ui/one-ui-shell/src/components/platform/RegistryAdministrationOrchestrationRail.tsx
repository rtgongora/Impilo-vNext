"use client";

import Link from "next/link";
import { GitMerge, IdCard, Loader2, Shield, UserCheck } from "lucide-react";
import { useProviders } from "@/hooks/queries/useRegistry";
import { useIssuanceQueue } from "@/hooks/queries/useVitoIssuance";
import {
  usePendingProvisionalIds,
  usePendingRegistryDedupCases,
} from "@/hooks/queries/useVitoRegistryAdmin";

function pagedTotal(payload: { data?: { totalElements?: number } } | undefined): number | null {
  const total = payload?.data?.totalElements;
  return typeof total === "number" ? total : null;
}

export function RegistryAdministrationOrchestrationRail() {
  const providersQ = useProviders({ status: "PENDING_VERIFICATION" });
  const provisionalQ = usePendingProvisionalIds(undefined, 0, 1);
  const dedupQ = usePendingRegistryDedupCases(0, 1);
  const issuanceQ = useIssuanceQueue(undefined, 0, 1);

  const pendingProviders = providersQ.data?.data ?? [];
  const provisionalTotal = pagedTotal(provisionalQ.data);
  const dedupTotal = pagedTotal(dedupQ.data);
  const issuanceTotal = pagedTotal(issuanceQ.data);

  const queueLoading =
    providersQ.isLoading || provisionalQ.isLoading || dedupQ.isLoading || issuanceQ.isLoading;
  const queueError =
    providersQ.isError || provisionalQ.isError || dedupQ.isError || issuanceQ.isError;

  return (
    <section
      className="rounded-xl border border-info/25 bg-info-soft/50 px-4 py-4"
      data-testid="registry-administration-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-primary-hover">
          <Shield className="mt-0.5 h-4 w-4 shrink-0 text-indigo-600" />
          <div>
            <p className="font-medium">Registry administration orchestration</p>
            <p>
              Reconcile provider registry truth, VITO provisional IDs, dedup cases, and Health ID issuance queues — each
              linked to its live operations surface (not placeholder shells).
            </p>
            {queueLoading ? (
              <p className="mt-1 flex items-center gap-1 text-xs text-primary-hover">
                <Loader2 className="h-3 w-3 animate-spin" />
                Loading reconciliation queues…
              </p>
            ) : queueError ? (
              <p className="mt-1 text-xs text-warning-foreground">
                One or more registry queues unavailable — use plane cards and VITO ops links below.
              </p>
            ) : (
              <ul className="mt-1 space-y-0.5 text-xs text-primary-hover" data-testid="registry-queue-summary">
                <li>{pendingProviders.length} provider(s) pending verification (current page)</li>
                <li>
                  {provisionalTotal ?? "—"} provisional ID(s) awaiting reconciliation
                </li>
                <li>{dedupTotal ?? "—"} registry dedup case(s) open</li>
                <li>{issuanceTotal ?? "—"} issuance request(s) in queue</li>
              </ul>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            href="/registry/providers/verification"
            className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-800"
          >
            <UserCheck className="h-3.5 w-3.5" />
            Verification queue
          </Link>
          <Link
            href="/operations/vito/registry-admin"
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-info-soft"
          >
            Provisional reconciliation
          </Link>
          <Link
            href="/operations/vito/dedup"
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-info-soft"
          >
            <GitMerge className="h-3.5 w-3.5" />
            Dedup queue
          </Link>
          <Link
            href="/operations/vito/match"
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-info-soft"
          >
            OpenCR match
          </Link>
          <Link
            href="/operations/vito/issuance"
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-info-soft"
          >
            <IdCard className="h-3.5 w-3.5" />
            Issuance queue
          </Link>
        </div>
      </div>
    </section>
  );
}
