"use client";

import Link from "next/link";
import { ArrowLeftRight, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useInventoryMovements } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

export default function StockMovementsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const movementsQuery = useInventoryMovements(facility?.id ?? "");
  const movements = movementsQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Stock Movements" subtitle="Track where stock moved, why it moved, and who executed the transfer for the current facility.">
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility to review stock transfers.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : movementsQuery.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading movements...
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Movement trail</div>
                  <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility.name}</h2>
                  <p className="mt-2 text-sm text-slate-600">Use movements to explain why a stock line changed before ordering more stock or reconciling a count discrepancy.</p>
                </div>
                <div className="flex flex-wrap gap-2 text-sm">
                  <Link href="/inventory/counts" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Open counts</Link>
                  <Link href="/inventory/requisitions" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Open requisitions</Link>
                </div>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              {movements.length === 0 ? (
                <div className="p-10 text-center text-sm text-slate-500">
                  <ArrowLeftRight className="mx-auto mb-3 h-10 w-10 text-slate-300" />
                  No stock movements recorded for this facility yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left text-slate-600">
                      <tr>
                        <th className="px-4 py-3 font-medium">Moved at</th>
                        <th className="px-4 py-3 font-medium">Item</th>
                        <th className="px-4 py-3 font-medium">From</th>
                        <th className="px-4 py-3 font-medium">To</th>
                        <th className="px-4 py-3 font-medium">Quantity</th>
                        <th className="px-4 py-3 font-medium">Reason</th>
                        <th className="px-4 py-3 font-medium">Performed by</th>
                      </tr>
                    </thead>
                    <tbody>
                      {movements.map((movement) => (
                        <tr key={movement.id} className="border-t border-slate-100 align-top">
                          <td className="px-4 py-3 text-slate-900">{formatDate(movement.movedAt)}</td>
                          <td className="px-4 py-3 font-medium text-slate-900">{movement.itemName}</td>
                          <td className="px-4 py-3 text-slate-600">{movement.fromLocation}</td>
                          <td className="px-4 py-3 text-slate-600">{movement.toLocation}</td>
                          <td className="px-4 py-3 text-slate-700">{movement.quantity}</td>
                          <td className="px-4 py-3 text-slate-600">{movement.reason}</td>
                          <td className="px-4 py-3 text-slate-600">{movement.performedBy}</td>
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
