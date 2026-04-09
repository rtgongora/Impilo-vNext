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
  COMPLETED: "bg-emerald-100 text-emerald-800",
  IN_PROGRESS: "bg-amber-100 text-amber-800",
  DRAFT: "bg-slate-100 text-slate-700",
};

export default function StockCountsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const countsQuery = useInventoryCounts(facility?.id ?? "");
  const counts = countsQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Stock Counts" subtitle="Close physical counts with the same facility context that drives pharmacy and requisition follow-through.">
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility before reviewing stock counts.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : countsQuery.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading stock counts...
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Count workspace</div>
                  <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility.name}</h2>
                  <p className="mt-2 text-sm text-slate-600">Use completed counts to validate stock before approving requisitions or ordering through the marketplace.</p>
                </div>
                <div className="flex flex-wrap gap-2 text-sm">
                  <Link href="/inventory/requisitions" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Open requisitions</Link>
                  <Link href="/inventory/movements" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Review movements</Link>
                </div>
              </div>
            </section>

            <section className="grid gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Counts logged</div>
                <div className="mt-3 text-3xl font-semibold text-slate-900">{counts.length}</div>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Open discrepancies</div>
                <div className="mt-3 text-3xl font-semibold text-slate-900">{counts.reduce((sum, count) => sum + count.discrepancies, 0)}</div>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Next step</div>
                <div className="mt-3 text-sm text-slate-700">Resolve variances, then issue or approve the corresponding requisition from the same facility workflow.</div>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              {counts.length === 0 ? (
                <div className="p-10 text-center text-sm text-slate-500">
                  <ClipboardList className="mx-auto mb-3 h-10 w-10 text-slate-300" />
                  No stock counts recorded for this facility yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left text-slate-600">
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
                        <tr key={count.id} className="border-t border-slate-100 align-top">
                          <td className="px-4 py-3 text-slate-900">{formatDate(count.countDate)}</td>
                          <td className="px-4 py-3 text-slate-700">{count.countedBy}</td>
                          <td className="px-4 py-3 text-slate-700">{count.itemCount}</td>
                          <td className="px-4 py-3 font-medium text-slate-900">{count.discrepancies}</td>
                          <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[count.status] ?? "bg-slate-100 text-slate-700"}`}>{count.status.replace(/_/g, " ")}</span></td>
                          <td className="px-4 py-3 text-slate-600">{count.notes || "No note captured"}</td>
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
