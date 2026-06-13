"use client";

import Link from "next/link";
import { ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useInventoryCounts } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

const STATUS_STYLES: Record<string, string> = {
  COMPLETED: "bg-emerald-100 text-primary-hover",
  IN_PROGRESS: "bg-amber-100 text-warning-foreground",
  DRAFT: "bg-neutral-100 text-foreground",
};

export default function StockCountsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const countsQuery = useInventoryCounts(facility?.id ?? "");
  const counts = countsQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Stock Counts" subtitle="Close physical counts with the same facility context that drives pharmacy and requisition follow-through.">
        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility before reviewing stock counts.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : countsQuery.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading stock counts...
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Count workspace</div>
                  <h2 className="mt-1 text-xl font-semibold text-foreground">{facility.name}</h2>
                  <p className="mt-2 text-sm text-muted-foreground">Use completed counts to validate stock before approving requisitions or ordering through the marketplace.</p>
                </div>
                <div className="flex flex-wrap gap-2 text-sm">
                  <Link href="/inventory/requisitions" className="rounded-lg border border-border px-3 py-2 font-medium hover:bg-background">Open requisitions</Link>
                  <Link href="/inventory/movements" className="rounded-lg border border-border px-3 py-2 font-medium hover:bg-background">Review movements</Link>
                </div>
              </div>
            </section>

            <section className="grid gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Counts logged</div>
                <div className="mt-3 text-3xl font-semibold text-foreground">{counts.length}</div>
              </div>
              <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Open discrepancies</div>
                <div className="mt-3 text-3xl font-semibold text-foreground">{counts.reduce((sum, count) => sum + count.discrepancies, 0)}</div>
              </div>
              <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Next step</div>
                <div className="mt-3 text-sm text-foreground">Resolve variances, then issue or approve the corresponding requisition from the same facility workflow.</div>
              </div>
            </section>

            <section className="rounded-2xl border border-border bg-card shadow-sm">
              {counts.length === 0 ? (
                <div className="p-10 text-center text-sm text-muted-foreground">
                  <ClipboardList className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                  No stock counts recorded for this facility yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-background text-left text-muted-foreground">
                      <tr>
                        <th className="px-4 py-3 font-medium">Count date</th>
                        <th className="px-4 py-3 font-medium">Counted by</th>
                        <th className="px-4 py-3 font-medium">Items reviewed</th>
                        <th className="px-4 py-3 font-medium">Discrepancies</th>
                        <th className="px-4 py-3 font-medium">Status</th>
                        <th className="px-4 py-3 font-medium">Notes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {counts.map((count) => (
                        <tr key={count.id} className="border-t border-border align-top">
                          <td className="px-4 py-3 text-foreground">{formatDate(count.countDate)}</td>
                          <td className="px-4 py-3 text-foreground">{count.countedBy}</td>
                          <td className="px-4 py-3 text-foreground">{count.itemCount}</td>
                          <td className="px-4 py-3 font-medium text-foreground">{count.discrepancies}</td>
                          <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[count.status] ?? "bg-neutral-100 text-foreground"}`}>{count.status.replace(/_/g, " ")}</span></td>
                          <td className="px-4 py-3 text-muted-foreground">{count.notes || "No note captured"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
