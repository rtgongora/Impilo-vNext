"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import {
  ProviderJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import {
  emergencyProvisionalIdentityTransaction,
  referralTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function ProviderWorkspacePage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const liveItems = items.slice(0, 2);
  const transactions =
    liveItems.length > 0
      ? liveItems
      : [emergencyProvisionalIdentityTransaction, referralTransaction];
  const isFixtureFallback = liveItems.length === 0;

  return (
    <AppLayout>
      <PageShell
        title="Provider Workspace"
        subtitle="Provider-facing orchestration with journey alignment and Nompilo provider assist"
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={isFixtureFallback ? "partial" : "live"}
                detail={
                  isFixtureFallback
                    ? "Provider workspace queries live core-transaction composition first and uses fixtures as fallback."
                    : "Provider workspace examples are loaded from /internal/v1/core-transactions."
                }
              />
              <span>
                {isLoading
                  ? "Loading provider workspace transactions from live composition..."
                  : isError
                    ? "Live provider workspace fetch failed; showing fixture fallback."
                    : isFixtureFallback
                      ? "No provider workspace transactions returned; showing fixture fallback."
                      : "Provider workspace is now linked to live core-transaction APIs."}
              </span>
            </div>
          </div>
          <ProviderJourneyStepper />
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
