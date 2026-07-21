"use client";

/**
 * Ruvimbo Payer workspace (/ruvimbo/payer) — R3 scaffold.
 *
 * The dedicated payer dashboard is largely an upgrade of the existing 10-tab operations
 * console (/coverage/operations) onto the shared Ruvimbo scaffold. Until R3 lands, this
 * routes payer operators to that live console rather than presenting an empty shell.
 */

import { Landmark } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";

export default function RuvimboPayerPage() {
  return (
    <AppLayout>
      <PageShell title="Ruvimbo Payer" subtitle="Membership, benefits, adjudication, payments and appeals" serviceSlug="ruvimbo">
        <RuvimboEmptyState
          icon={<Landmark className="h-6 w-6" aria-hidden />}
          title="The Ruvimbo Payer workspace is being built"
          explanation="Membership, schemes and plans, provider networks, authorisation queues, claim adjudication, payments, appeals and integrity review will land here on the shared Ruvimbo workspace."
          when="These operations are available now in the Ruvimbo operations console while the dedicated payer dashboard is completed."
          actions={[{ label: "Open operations console", href: "/coverage/operations" }]}
        />
      </PageShell>
    </AppLayout>
  );
}
