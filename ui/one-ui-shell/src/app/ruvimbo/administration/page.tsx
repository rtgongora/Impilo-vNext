"use client";

/**
 * Ruvimbo Administration workspace (/ruvimbo/administration) — R4 scaffold.
 *
 * The national/platform administration workspace (payer onboarding, claims-switch
 * monitor with real trace/ack/retry/replay, routing, rules, terminology, integration
 * health, data quality, security & audit, corrective actions) is built in wave R4.
 * Payer/plan administration is available now in the operations console.
 */

import { Settings2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";

export default function RuvimboAdministrationPage() {
  return (
    <AppLayout>
      <PageShell title="Ruvimbo Administration" subtitle="Payers, claims switch, standards and platform governance" serviceSlug="ruvimbo">
        <RuvimboEmptyState
          icon={<Settings2 className="h-6 w-6" aria-hidden />}
          title="The Ruvimbo Administration workspace is being built"
          explanation="Payer onboarding, claims-switch operations, routing, message mapping, rules, terminology, integration health, data quality, security & audit and corrective actions will land here."
          when="Payer, scheme and plan administration are available now in the operations console while the platform-governance surfaces are completed."
          actions={[{ label: "Open operations console", href: "/coverage/operations" }]}
        />
      </PageShell>
    </AppLayout>
  );
}
