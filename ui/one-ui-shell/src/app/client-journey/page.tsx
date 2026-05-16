"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  ClientJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import {
  appointmentTransaction,
  telemedicineTransaction,
} from "@/features/core-transaction/fixtures/core-transactions";

export default function ClientJourneyPage() {
  return (
    <AppLayout>
      <PageShell title="Person Journey" subtitle="Person-centered transaction flow with Nompilo guidance">
        <div className="space-y-4">
          <ClientJourneyStepper />
          <CoreTransactionShell transaction={appointmentTransaction} status="ready" />
          <CoreTransactionShell transaction={telemedicineTransaction} status="ready" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
