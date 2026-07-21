"use client";

/**
 * Ruvimbo Provider workspace (/ruvimbo/provider) — R2 scaffold.
 *
 * The dedicated provider dashboard (eligibility exceptions, authorisations, claim
 * create/scrub/submit, denials, remittances, reconciliation, patient estimates,
 * performance) is built in wave R2 on the shared Ruvimbo scaffold. Until then this is an
 * honest placeholder that routes to the real surfaces that already exist, rather than a
 * fake dashboard.
 */

import { Stethoscope } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";

export default function RuvimboProviderPage() {
  return (
    <AppLayout>
      <PageShell title="Ruvimbo Provider" subtitle="Eligibility, authorisations, claims and settlement" serviceSlug="ruvimbo">
        <RuvimboEmptyState
          icon={<Stethoscope className="h-6 w-6" aria-hidden />}
          title="The Ruvimbo Provider workspace is being built"
          explanation="Eligibility resolution, authorisation and referral management, claim creation and scrubbing, denials, remittances and reconciliation are coming to this workspace."
          when="Provider contracting and networks are already available in the operations console while this dedicated dashboard is completed."
          actions={[
            { label: "Provider contracts", href: "/coverage/contracts" },
            { label: "Operations console", href: "/coverage/operations", variant: "secondary" },
          ]}
        />
      </PageShell>
    </AppLayout>
  );
}
