"use client";

import Link from "next/link";
import { Loader2, ScanLine } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useLabWorklist } from "@/hooks/queries/useLabWorklist";

export default function FacilityImagingDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const worklistQ = useLabWorklist({
    facilityId: facility?.id,
    type: "IMAGING",
    size: 10,
  });

  const summary = worklistQ.data?.data?.summary;
  const items = worklistQ.data?.data?.items ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Facility imaging dashboard"
        subtitle="PACS worklist snapshot and imaging operations summary for the active facility"
        icon={<ScanLine className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <FeatureMaturityBadge status="live" detail="OROS imaging worklist + facility context" />
          <Link href="/imaging/worklist" className="text-sm font-medium text-primary hover:underline">
            Open full worklist →
          </Link>
        </div>

        {!facility?.id ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
            Select a facility to load imaging operations.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[
                { label: "Pending acquisition", value: summary?.pending_collection ?? 0 },
                { label: "In progress", value: summary?.in_progress ?? 0 },
                { label: "Completed", value: summary?.completed ?? 0 },
                { label: "Urgent", value: summary?.urgent ?? 0 },
              ].map((card) => (
                <div key={card.label} className="rounded-2xl border border-border bg-card p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{card.label}</p>
                  <p className="mt-2 text-3xl font-semibold text-foreground">{card.value}</p>
                </div>
              ))}
            </div>

            <section className="rounded-2xl border border-border bg-card p-5">
              <h2 className="text-sm font-semibold text-foreground">Recent imaging orders</h2>
              {worklistQ.isLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              ) : worklistQ.isError ? (
                <p className="mt-4 text-sm text-red-600">Could not load imaging worklist.</p>
              ) : items.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No imaging orders in scope.</p>
              ) : (
                <ul className="mt-4 divide-y divide-slate-100">
                  {items.map((item) => (
                    <li key={String(item.orderId ?? item.id)} className="flex items-center justify-between py-2 text-sm">
                      <span className="font-mono text-foreground">{String(item.orderId ?? item.id)}</span>
                      <span className="text-muted-foreground">{item.patientCpid ?? "—"}</span>
                      <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs">{item.status ?? "—"}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
