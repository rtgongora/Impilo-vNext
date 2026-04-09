"use client";

import Link from "next/link";
import { AlertTriangle, ArrowRight, ClipboardList, FileText, Package, Pill, Warehouse } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useInventoryCounts,
  useInventoryItems,
  useInventoryMovements,
  useInventoryRequisitions,
} from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

function formatDate(value: string | null) {
  if (!value) return "Not recorded";
  return new Date(value).toLocaleString();
}

export default function InventoryPage() {
  const facility = useFacilityStore((state) => state.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);
  const countsQuery = useInventoryCounts(facilityId);
  const movementsQuery = useInventoryMovements(facilityId);
  const requisitionsQuery = useInventoryRequisitions(facilityId);

  const items = itemsQuery.data ?? [];
  const counts = countsQuery.data ?? [];
  const movements = movementsQuery.data ?? [];
  const requisitions = requisitionsQuery.data ?? [];

  const lowStockItems = items.filter((item) => item.quantityOnHand <= item.reorderLevel || item.status === "LOW_STOCK");
  const lastCount = counts[0] ?? null;
  const openRequisitions = requisitions.filter((req) => req.status !== "FULFILLED").length;
  const recentMovement = movements[0] ?? null;

  return (
    <AppLayout>
      <PageShell
        title="Inventory Operations"
        subtitle="Track stock position, count closure, movements, and replenishment in the same facility workspace."
      >
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Inventory work needs a facility in scope before counts, stock movement, and requisitions can stay aligned.
            <div className="mt-3">
              <Link href="/workspace" className="font-medium text-amber-950 underline">
                Enter Facility Work
              </Link>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Facility in scope</div>
                  <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility.name}</h2>
                  <p className="mt-2 max-w-3xl text-sm text-slate-600">
                    Inventory now stays connected to the active facility so low-stock signals, count closure, and requisition follow-through do not break away from pharmacy, wards, or marketplace ordering.
                  </p>
                </div>
                <div className="grid gap-2 text-sm text-slate-600 sm:grid-cols-2">
                  <Link href="/pharmacy/stock?source=inventory" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">
                    Open pharmacy stock
                  </Link>
                  <Link href="/marketplace/orders?source=inventory" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">
                    Order through marketplace
                  </Link>
                  <Link href="/inventory/requisitions" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">
                    Manage requisitions
                  </Link>
                  <Link href="/inventory/movements" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">
                    Review transfers
                  </Link>
                </div>
              </div>
            </section>

            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><Warehouse className="h-4 w-4" /> Active stock lines</div>
                <div className="mt-3 text-3xl font-semibold text-slate-900">{items.length}</div>
                <p className="mt-1 text-sm text-slate-600">{lowStockItems.length} need reorder attention now.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><ClipboardList className="h-4 w-4" /> Latest count</div>
                <div className="mt-3 text-lg font-semibold text-slate-900">{lastCount ? lastCount.status.replace(/_/g, " ") : "No count yet"}</div>
                <p className="mt-1 text-sm text-slate-600">{lastCount ? `${lastCount.itemCount} lines reviewed on ${formatDate(lastCount.countDate)}` : "Record the first count from the counts workspace."}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><FileText className="h-4 w-4" /> Open requisitions</div>
                <div className="mt-3 text-3xl font-semibold text-slate-900">{openRequisitions}</div>
                <p className="mt-1 text-sm text-slate-600">Current requests still waiting for issue, approval, or fulfilment.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><Package className="h-4 w-4" /> Latest movement</div>
                <div className="mt-3 text-lg font-semibold text-slate-900">{recentMovement ? recentMovement.itemName : "No movement yet"}</div>
                <p className="mt-1 text-sm text-slate-600">{recentMovement ? `${recentMovement.fromLocation} -> ${recentMovement.toLocation}` : "Transfers will appear here once recorded."}</p>
              </div>
            </section>

            <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">Low-stock watchlist</h3>
                    <p className="text-sm text-slate-600">Surface the next action before stock issues reach the dispensary or ward.</p>
                  </div>
                  <Link href="/inventory/counts" className="inline-flex items-center gap-1 text-sm font-medium text-slate-700 hover:text-slate-900">
                    Open counts
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </div>
                <div className="mt-4 space-y-3">
                  {lowStockItems.length === 0 ? (
                    <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
                      No immediate stock pressure is showing for the selected facility.
                    </div>
                  ) : (
                    lowStockItems.map((item) => (
                      <div key={item.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 px-4 py-3 md:flex-row md:items-center md:justify-between">
                        <div>
                          <div className="flex items-center gap-2 text-sm font-medium text-slate-900">
                            {item.productName}
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800">{item.status.replace(/_/g, " ")}</span>
                          </div>
                          <p className="mt-1 text-sm text-slate-600">{item.productCode} • {item.category} • last counted {formatDate(item.lastCountedAt)}</p>
                        </div>
                        <div className="flex items-center gap-6 text-sm">
                          <div>
                            <div className="text-slate-500">On hand</div>
                            <div className="font-semibold text-slate-900">{item.quantityOnHand} {item.unit}</div>
                          </div>
                          <div>
                            <div className="text-slate-500">Reorder level</div>
                            <div className="font-semibold text-slate-900">{item.reorderLevel} {item.unit}</div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <h3 className="text-lg font-semibold text-slate-900">Coordination loop</h3>
                <div className="mt-4 space-y-4 text-sm text-slate-600">
                  <div className="rounded-xl border border-slate-200 p-4">
                    <div className="flex items-center gap-2 font-medium text-slate-900"><AlertTriangle className="h-4 w-4 text-amber-600" /> Entry</div>
                    <p className="mt-1">The facility context comes from the same experience layer, so every stock view stays bound to the selected site.</p>
                  </div>
                  <div className="rounded-xl border border-slate-200 p-4">
                    <div className="flex items-center gap-2 font-medium text-slate-900"><Pill className="h-4 w-4 text-sky-600" /> Cross-surface continuity</div>
                    <p className="mt-1">Use inventory to decide the next supply action, then hand off to pharmacy stock, marketplace orders, or ward operations without losing context.</p>
                  </div>
                  <div className="rounded-xl border border-slate-200 p-4">
                    <div className="font-medium text-slate-900">Next best action</div>
                    <p className="mt-1">{openRequisitions > 0 ? "Review open requisitions before opening a new purchase order." : "If stock pressure is rising, create a requisition and then place a marketplace order from the same facility context."}</p>
                  </div>
                </div>
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
