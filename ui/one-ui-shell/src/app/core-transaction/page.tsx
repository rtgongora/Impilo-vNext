"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import {
  CoreTransactionShell,
  ClientJourneyStepper,
  ProviderJourneyStepper,
} from "@/features/core-transaction/components";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function CoreTransactionPage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const transaction = items[0];
  const isEmpty = !isLoading && !isError && items.length === 0;

  return (
    <AppLayout>
      <PageShell
        title="Core Transaction"
        subtitle="Transaction-aware orchestration anchored to the Health Operating System doctrine"
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-3 text-sm text-cyan-900">
            Core transaction composition uses trust/context headers through the shared API client contract (tenant,
            pod, actor, purpose-of-use, assurance, and duty context when available).
          </div>
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={transaction ? "live" : isError ? "partial" : "connected"}
                detail={
                  transaction
                    ? "Core transaction is loaded from /internal/v1/core-transactions."
                    : "Core transaction page only renders live BFF data and never injects fixture records."
                }
              />
              <span>
                {isLoading
                  ? "Loading live core transaction composition..."
                  : isError
                    ? "Live fetch failed; retry to restore real core-transaction composition."
                    : isEmpty
                      ? "No live transaction currently available from the BFF feed."
                      : "Live core transaction composition is active from the BFF endpoint."}
              </span>
            </div>
          </div>
          <ClientJourneyStepper />
          <ProviderJourneyStepper />
          <CoreTransactionShell
            transaction={transaction}
            status={isLoading ? "loading" : isError ? "error" : isEmpty ? "empty" : "ready"}
          />
        </div>
      </PageShell>
    </AppLayout>
  );
}
