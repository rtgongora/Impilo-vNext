"use client";

/**
 * Ruvimbo Performance workspace (/ruvimbo/performance) — R5 scaffold.
 *
 * The oversight workspace (coverage, provider, payer, claims, authorisation, payment,
 * programme, claims-switch, integrity and service-level scorecards with drill-down and
 * corrective-action tracking) is built in wave R5. It depends on new aggregated
 * analytics endpoints — the largest remaining backend seam — so this is an honest
 * placeholder rather than an invented dashboard.
 */

import { BarChart3 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";

export default function RuvimboPerformancePage() {
  return (
    <AppLayout>
      <PageShell title="Ruvimbo Performance" subtitle="Financing, claims, payer, provider and programme monitoring" serviceSlug="ruvimbo">
        <RuvimboEmptyState
          icon={<BarChart3 className="h-6 w-6" aria-hidden />}
          title="The Ruvimbo Performance workspace is being built"
          explanation="Active scorecards and drill-down dashboards for coverage, providers, payers, claims, authorisations, payments, programmes, the claims switch, integrity and service levels — with corrective-action tracking — will land here."
          when="This workspace depends on new aggregated analytics endpoints being added to the coverage service; it will appear once those reads are live."
          actions={[{ label: "Back to Ruvimbo", href: "/ruvimbo", variant: "secondary" }]}
        />
      </PageShell>
    </AppLayout>
  );
}
