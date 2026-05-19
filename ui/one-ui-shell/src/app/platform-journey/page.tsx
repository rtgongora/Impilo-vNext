"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import { CoreTransactionShell, PlatformJourneyMonitor } from "@/features/core-transaction/components";
import {
  failedPaymentTransaction,
  offlineSyncPendingTransaction,
  providerIncompleteEncounterTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function PlatformJourneyPage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const liveItems = items.slice(0, 2);
  const transactions =
    liveItems.length > 0
      ? liveItems
      : [failedPaymentTransaction, providerIncompleteEncounterTransaction];
  const monitorTransaction = transactions[0] ?? offlineSyncPendingTransaction;
  const isFixtureFallback = liveItems.length === 0;

  return (
    <AppLayout>
      <PageShell
        title="Platform Journey"
        subtitle="Back-of-house orchestration visibility: blockers, sync, financial gates, and reconciliation"
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={isFixtureFallback ? "partial" : "live"}
                detail={
                  isFixtureFallback
                    ? "Platform journey now queries /internal/v1/core-transactions and uses fixtures only as explicit fallback."
                    : "Platform journey blockers and transitions are sourced from live BFF composition."
                }
              />
              <span>
                {isLoading
                  ? "Loading platform journey telemetry from live core transactions..."
                  : isError
                    ? "Live platform telemetry unavailable; showing fixture fallback."
                    : isFixtureFallback
                      ? "No live platform transaction feed returned; showing fixture fallback."
                      : "Platform journey blockers and transitions are now sourced from live BFF composition."}
              </span>
            </div>
          </div>
          <PlatformJourneyMonitor transaction={monitorTransaction} />
          {transactions.map((transaction) => (
            <CoreTransactionShell
              key={transaction.transaction.id}
              transaction={transaction}
              status={isLoading ? "loading" : "ready"}
            />
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
