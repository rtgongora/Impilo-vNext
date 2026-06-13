"use client";

/**
 * Courier / Driver Console
 *
 * For couriers, CHWs, facility runners and drone/robot operators to view and
 * progress their own assigned deliveries from the web shell. Mobile parity is
 * provided by the citizen/provider apps, but this surface lets dispatch teams
 * cover for absent couriers from a desk when needed.
 */

import Link from "next/link";
import { PackageCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeStatusChip, NhumePriorityChip } from "@/components/nhume/NhumeStatusChip";
import { useNhumeDeliveries } from "@/hooks/useNhume";

const COURIER_STATUSES = ["ASSIGNED", "ACCEPTED", "EN_ROUTE_TO_PICKUP", "PICKED_UP", "IN_TRANSIT", "AT_DESTINATION"];

export default function NhumeCourierConsolePage() {
  return (
    <AppLayout>
      <PageShell
        title="Courier / Driver Console"
        subtitle="Your assigned deliveries — accept, pickup, transit, deliver"
        icon={<PackageCheck className="h-6 w-6" />}
      >
        <p className="text-sm text-muted-foreground mb-4">
          Assigned deliveries are filtered to the current actor by the backend. Click any row to
          progress its lifecycle, capture proof of delivery or record chain-of-custody events.
        </p>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {COURIER_STATUSES.map((s) => <StatusBucket key={s} status={s} />)}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function StatusBucket({ status }: { status: string }) {
  const { data, isPending } = useNhumeDeliveries({ status, size: 25 });
  const rows = data?.data ?? [];
  return (
    <div className="rounded-2xl border border-border bg-card">
      <div className="border-b border-border px-5 py-3 flex items-center justify-between">
        <h3 className="font-semibold text-foreground">
          {status.replace(/_/g, " ").toLowerCase()}
        </h3>
        <NhumeStatusChip status={status} />
      </div>
      {isPending ? (
        <div className="px-5 py-6 text-sm text-muted-foreground text-center">
          <Loader2 className="inline-block h-4 w-4 animate-spin text-teal-500 mr-2" />
          Loading…
        </div>
      ) : rows.length === 0 ? (
        <div className="px-5 py-6 text-sm text-muted-foreground text-center">Nothing here.</div>
      ) : (
        <ul className="divide-y divide-gray-100">
          {rows.map((d) => (
            <li key={d.delivery_id} className="px-5 py-3 flex items-center justify-between">
              <div>
                <Link href={`/nhume/deliveries/${d.delivery_id}`} className="font-medium text-teal-700 hover:text-teal-900">
                  {d.reference}
                </Link>
                <div className="text-xs text-muted-foreground mt-1">
                  {d.origin_label ?? "?"} → {d.destination_label ?? "?"}
                </div>
              </div>
              <NhumePriorityChip priority={String(d.priority ?? "")} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
