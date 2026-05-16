"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  CoreTransactionShell,
  ClientJourneyStepper,
  ProviderJourneyStepper,
} from "@/features/core-transaction/components";
import { facilityWalkInTransaction } from "@/features/core-transaction/fixtures/core-transactions";

export default function CoreTransactionPage() {
  return (
    <AppLayout>
      <PageShell
        title="Core Transaction"
        subtitle="Transaction-aware orchestration anchored to the Health Operating System doctrine"
      >
        <div className="space-y-4">
          <ClientJourneyStepper />
          <ProviderJourneyStepper />
          <CoreTransactionShell transaction={facilityWalkInTransaction} status="ready" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
