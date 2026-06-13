"use client";

import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import {
  ClientJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";
import type { CoreTransactionView } from "@/features/core-transaction/types";

const PERSON_PRIORITY_TYPES = new Set([
  "APPOINTMENT",
  "TELEMEDICINE",
  "CHRONIC_CARE",
  "WELLNESS",
  "MARKETPLACE",
  "FACILITY_WALK_IN",
]);

function prioritizePersonJourney(items: CoreTransactionView[]): CoreTransactionView[] {
  const preferred = items.filter((item) => PERSON_PRIORITY_TYPES.has(item.transaction.type));
  if (preferred.length > 0) return preferred.slice(0, 2);
  return items.slice(0, 2);
}

export default function ClientJourneyPage() {
  const { items, isLoading, isError } = useCoreTransactionFeed();
  const transactions = prioritizePersonJourney(items);
  const isEmpty = !isLoading && !isError && transactions.length === 0;

  return (
    <AppLayout>
      <PageShell title="Person Journey" subtitle="Person-centered transaction flow with Nompilo guidance">
        <div className="space-y-4">
          <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-3 text-sm text-cyan-900">
            Person journey requests are trust-context aware through the global API client header set (tenant, pod,
            actor, purpose-of-use, and duty context where present).
          </div>
          <div className="rounded-xl border border-border bg-neutral-100 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={transactions.length > 0 ? "live" : isError ? "partial" : "connected"}
                detail={
                  transactions.length === 0
                    ? "Person journey renders only live transaction cards and does not inject sample data."
                    : "Person journey cards are sourced from live core-transaction composition."
                }
              />
              <span>
                {isLoading
                  ? "Loading person journey cards from live composition..."
                  : isError
                    ? "Live person journey fetch failed; retry to restore transaction cards."
                    : isEmpty
                      ? "No live person journey transactions returned by the BFF feed."
                      : "Person journey cards are now backed by live BFF core-transaction data."}
              </span>
            </div>
          </div>
          <ClientJourneyStepper />
          {transactions.length === 0 ? (
            <CoreTransactionShell status={isLoading ? "loading" : isError ? "error" : "empty"} />
          ) : (
            transactions.map((transaction) => (
              <CoreTransactionShell
                key={transaction.transaction.id}
                transaction={transaction}
                status={isLoading ? "loading" : "ready"}
              />
            ))
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
