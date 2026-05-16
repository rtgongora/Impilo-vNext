"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CoreTransactionShell, PlatformJourneyMonitor } from "@/features/core-transaction/components";
import {
  failedPaymentTransaction,
  offlineSyncPendingTransaction,
  providerIncompleteEncounterTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";

export default function PlatformJourneyPage() {
  return (
    <AppLayout>
      <PageShell
        title="Platform Journey"
        subtitle="Back-of-house orchestration visibility: blockers, sync, financial gates, and reconciliation"
      >
        <div className="space-y-4">
          <PlatformJourneyMonitor transaction={offlineSyncPendingTransaction} />
          <CoreTransactionShell transaction={failedPaymentTransaction} status="ready" />
          <CoreTransactionShell transaction={providerIncompleteEncounterTransaction} status="ready" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
