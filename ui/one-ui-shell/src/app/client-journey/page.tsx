"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import {
  ClientJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import {
  appointmentTransaction,
  telemedicineTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function ClientJourneyPage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const liveItems = items.slice(0, 2);
  const transactions = liveItems.length > 0 ? liveItems : [appointmentTransaction, telemedicineTransaction];
  const isFixtureFallback = liveItems.length === 0;

  return (
    <AppLayout>
      <PageShell title="Person Journey" subtitle="Person-centered transaction flow with Nompilo guidance">
        <div className="space-y-4">
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={isFixtureFallback ? "partial" : "live"}
                detail={
                  isFixtureFallback
                    ? "Queries /internal/v1/core-transactions and falls back to fixture examples when unavailable."
                    : "Person journey cards are sourced from live core-transaction composition."
                }
              />
              <span>
                {isLoading
                  ? "Loading person journey cards from live composition..."
                  : isError
                    ? "Live person journey fetch failed; displaying fixture fallback."
                    : isFixtureFallback
                      ? "No live person journey transactions returned; displaying fixture fallback."
                      : "Person journey cards are now backed by live BFF core-transaction data."}
              </span>
            </div>
          </div>
          <ClientJourneyStepper />
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
