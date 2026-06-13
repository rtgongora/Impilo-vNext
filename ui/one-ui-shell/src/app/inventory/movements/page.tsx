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
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility to review stock transfers.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : movementsQuery.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading movements...
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Movement trail</div>
                  <h2 className="mt-1 text-xl font-semibold text-foreground">{facility.name}</h2>
                  <p className="mt-2 text-sm text-muted-foreground">Use movements to explain why a stock line changed before ordering more stock or reconciling a count discrepancy.</p>
                </div>
                <div className="flex flex-wrap gap-2 text-sm">
                  <Link href="/inventory/counts" className="rounded-lg border border-border px-3 py-2 font-medium hover:bg-background">Open counts</Link>
                  <Link href="/inventory/requisitions" className="rounded-lg border border-border px-3 py-2 font-medium hover:bg-background">Open requisitions</Link>
                </div>
              </div>
            </section>

            <section className="rounded-2xl border border-border bg-card shadow-sm">
              {movements.length === 0 ? (
                <div className="p-10 text-center text-sm text-muted-foreground">
                  <ArrowLeftRight className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                  No stock movements recorded for this facility yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-background text-left text-muted-foreground">
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
                        <tr key={movement.id} className="border-t border-border align-top">
                          <td className="px-4 py-3 text-foreground">{formatDate(movement.movedAt)}</td>
                          <td className="px-4 py-3 font-medium text-foreground">{movement.itemName}</td>
                          <td className="px-4 py-3 text-muted-foreground">{movement.fromLocation}</td>
                          <td className="px-4 py-3 text-muted-foreground">{movement.toLocation}</td>
                          <td className="px-4 py-3 text-foreground">{movement.quantity}</td>
                          <td className="px-4 py-3 text-muted-foreground">{movement.reason}</td>
                          <td className="px-4 py-3 text-muted-foreground">{movement.performedBy}</td>
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
