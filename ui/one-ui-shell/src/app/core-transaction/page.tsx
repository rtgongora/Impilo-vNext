"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import {
  CoreTransactionShell,
  ClientJourneyStepper,
  ProviderJourneyStepper,
} from "@/features/core-transaction/components";
import { facilityWalkInTransaction } from "@/features/core-transaction/fixtures/core-transactions";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function CoreTransactionPage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const transaction = items[0] ?? facilityWalkInTransaction;
  const isFixtureFallback = items.length === 0;

  return (
    <AppLayout>
      <PageShell
        title="Core Transaction"
        subtitle="Transaction-aware orchestration anchored to the Health Operating System doctrine"
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={isFixtureFallback ? "partial" : "live"}
                detail={
                  isFixtureFallback
                    ? "Live endpoint is queried first; fixture is used only when no transactions are returned."
                    : "Core transaction is loaded from /internal/v1/core-transactions."
                }
              />
              <span>
                {isLoading
                  ? "Loading live core transaction composition..."
                  : isError
                    ? "Live fetch failed; showing fixture fallback."
                    : isFixtureFallback
                      ? "No live transaction currently available; showing fixture fallback."
                      : "Live core transaction composition is active from the BFF endpoint."}
              </span>
            </div>
          </div>
          <ClientJourneyStepper />
          <ProviderJourneyStepper />
          <CoreTransactionShell
            transaction={transaction}
            status={isLoading ? "loading" : "ready"}
          />
        </div>
      </PageShell>
    </AppLayout>
  );
}
