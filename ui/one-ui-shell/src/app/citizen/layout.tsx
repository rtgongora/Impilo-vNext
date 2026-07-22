"use client";

import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CitizenSelfServiceGate } from "@/components/citizen/CitizenSelfServiceGate";

export default function CitizenLayout({ children }: { children: ReactNode }) {
  return (
    <AppLayout>
      <PageShell
        title="Citizen self-service"
        subtitle="Impilo ID, delegated pickup, recovery, and document claim — consolidated in Experience (same gateway contracts as legacy portal/self-service apps)"
      >
        <CitizenSelfServiceGate>{children}</CitizenSelfServiceGate>
      </PageShell>
    </AppLayout>
  );
}
