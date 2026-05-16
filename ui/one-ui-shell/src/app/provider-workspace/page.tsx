"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  ProviderJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import {
  emergencyProvisionalIdentityTransaction,
  referralTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";

export default function ProviderWorkspacePage() {
  return (
    <AppLayout>
      <PageShell
        title="Provider Workspace"
        subtitle="Provider-facing orchestration with journey alignment and Nompilo provider assist"
      >
        <div className="space-y-4">
          <ProviderJourneyStepper />
          <CoreTransactionShell transaction={emergencyProvisionalIdentityTransaction} status="ready" />
          <CoreTransactionShell transaction={referralTransaction} status="ready" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
