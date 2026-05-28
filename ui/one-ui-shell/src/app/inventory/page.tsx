"use client";

import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  ClipboardList,
  Layers,
  Loader2,
  Package,
  PackageSearch,
  Pill,
  Truck,
  Warehouse,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import {
  useInventoryItems,
  useInventoryMovements,
  useInventoryRequisitions,
} from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

function formatDate(value: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

const PENDING_REQ_STATUSES = new Set([
  "PENDING",
  "DRAFT",
  "SUBMITTED",
  "APPROVED",
  "OPEN",
  "IN_REVIEW",
]);

function isPendingRequisition(status: string) {
  const u = status.toUpperCase();
  if (PENDING_REQ_STATUSES.has(u)) return true;
  if (u === "FULFILLED" || u === "RECEIVED" || u === "REJECTED" || u === "CANCELLED" || u === "CLOSED") {
    return false;
  }
  return u !== "DISPATCHED";
}

export default function InventoryPage() {
  const facility = useFacilityStore((state) => state.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);
  const movementsQuery = useInventoryMovements(facilityId);
  const requisitionsQuery = useInventoryRequisitions(facilityId);

  const items = itemsQuery.data ?? [];
  const movements = movementsQuery.data ?? [];
  const requisitions = requisitionsQuery.data ?? [];

  const lowStockItems = items.filter((item) => item.quantityOnHand <= item.reorderLevel || item.status === "LOW_STOCK");
  const pendingRequisitions = requisitions.filter((r) => isPendingRequisition(r.status));
  const recentMovements = movements.slice(0, 12);

  const loading = !!facilityId && (itemsQuery.isLoading || movementsQuery.isLoading || requisitionsQuery.isLoading);
  const error = itemsQuery.error || movementsQuery.error || requisitionsQuery.error;

  return (
    <AppLayout>
      <PageShell
        title="Inventory Dashboard"
        subtitle="Supply Plane — stock summary, recent movements, and shortcuts to catalog, stock levels, requisitions, reconciliation, and assets"
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
            <SupplyPlaneContextBar />

            {error ? (
              <div className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                <div>
                  <p className="font-medium">Could not load inventory data</p>
                  <p className="mt-1 text-rose-800/90">Check your connection and try again. Partial views may be stale.</p>
                </div>
              </div>
            ) : null}

            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Facility in scope</div>
                  <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility.name}</h2>
                  <p className="mt-2 max-w-3xl text-sm text-slate-600">
                    Dashboard metrics refresh from the facility-scoped inventory API. Use the links below for catalog maintenance,
                    bin-level stock, replenishment, physical counts, and fixed assets.
                  </p>
                </div>
              </div>
            </section>

            <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                  <Warehouse className="h-4 w-4" aria-hidden />
                  Total items
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  {loading ? <Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Loading" /> : null}
                  <span className="text-3xl font-semibold text-slate-900">{items.length}</span>
                </div>
                <p className="mt-1 text-sm text-slate-600">Active catalog lines for this facility.</p>
              </div>
              <div className="rounded-2xl border border-amber-200/80 bg-amber-50/80 p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-amber-800">
                  <AlertTriangle className="h-4 w-4" aria-hidden />
                  Low stock alerts
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  {loading ? <Loader2 className="h-6 w-6 animate-spin text-amber-600/70" aria-label="Loading" /> : null}
                  <span className="text-3xl font-semibold text-amber-950">{lowStockItems.length}</span>
                </div>
                <p className="mt-1 text-sm text-amber-900/85">At or below reorder level, or flagged LOW_STOCK.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                  <ClipboardList className="h-4 w-4" aria-hidden />
                  Pending requisitions
                </div>
                <div className="mt-3 flex items-baseline gap-2">
                  {loading ? <Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Loading" /> : null}
                  <span className="text-3xl font-semibold text-slate-900">{pendingRequisitions.length}</span>
                </div>
                <p className="mt-1 text-sm text-slate-600">Not yet received or closed in the replenishment pipeline.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                  <Layers className="h-4 w-4" aria-hidden />
                  Recent movements
                </div>
                <div className="mt-3 text-lg font-semibold text-slate-900">{movements.length}</div>
                <p className="mt-1 text-sm text-slate-600">Total transfer events loaded for this facility.</p>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-slate-50/60 p-5 shadow-sm">
              <h3 className="text-sm font-semibold uppercase tracking-[0.12em] text-slate-600">Quick links</h3>
              <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                <Link
                  href="/inventory/items"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <PackageSearch className="h-4 w-4 text-teal-700" aria-hidden />
                    Item catalog
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
                <Link
                  href="/inventory/stock-management"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <Warehouse className="h-4 w-4 text-teal-700" aria-hidden />
                    Stock & movements
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
                <Link
                  href="/inventory/requisitions"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <Truck className="h-4 w-4 text-teal-700" aria-hidden />
                    Requisitions
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
                <Link
                  href="/inventory/reconciliation"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <ClipboardList className="h-4 w-4 text-teal-700" aria-hidden />
                    Reconciliation
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
                <Link
                  href="/operations/assets"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <Package className="h-4 w-4 text-teal-700" aria-hidden />
                    Asset registry
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
                <Link
                  href="/inventory/counts"
                  className="inline-flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-900 shadow-sm hover:bg-slate-50"
                >
                  <span className="inline-flex items-center gap-2">
                    <Pill className="h-4 w-4 text-sky-700" aria-hidden />
                    Legacy counts
                  </span>
                  <ArrowRight className="h-4 w-4 text-slate-400" aria-hidden />
                </Link>
              </div>
            </section>

            <section className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">Recent stock movements</h3>
                    <p className="text-sm text-slate-600">Receipts, issues, and transfers (newest first). FEFO picks align with movement history.</p>
                  </div>
                  <Link href="/inventory/stock-management" className="text-sm font-medium text-teal-800 hover:text-teal-950">
                    Open stock workspace
                  </Link>
                </div>
                {loading ? (
                  <div className="mt-8 flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
                    <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
                    Loading movements…
                  </div>
                ) : recentMovements.length === 0 ? (
                  <div className="mt-6 rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-500">
                    No movements recorded for this facility yet.
                  </div>
                ) : (
                  <div className="mt-4 overflow-x-auto">
                    <table className="w-full min-w-[640px] text-sm">
                      <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-600">
                        <tr>
                          <th className="px-3 py-2 font-medium">When</th>
                          <th className="px-3 py-2 font-medium">Item</th>
                          <th className="px-3 py-2 font-medium">Qty</th>
                          <th className="px-3 py-2 font-medium">From</th>
                          <th className="px-3 py-2 font-medium">To</th>
                          <th className="px-3 py-2 font-medium">By</th>
                        </tr>
                      </thead>
                      <tbody>
                        {recentMovements.map((m) => (
                          <tr key={m.id} className="border-b border-slate-100 last:border-0">
                            <td className="whitespace-nowrap px-3 py-2 text-slate-600">{formatDate(m.movedAt)}</td>
                            <td className="px-3 py-2 font-medium text-slate-900">{m.itemName}</td>
                            <td className="px-3 py-2 text-slate-800">{m.quantity}</td>
                            <td className="px-3 py-2 text-slate-600">{m.fromLocation}</td>
                            <td className="px-3 py-2 text-slate-600">{m.toLocation}</td>
                            <td className="px-3 py-2 text-slate-600">{m.performedBy}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <h3 className="text-lg font-semibold text-slate-900">Low-stock watchlist</h3>
                <p className="mt-1 text-sm text-slate-600">Next actions before wards or pharmacy run short.</p>
                <div className="mt-4 space-y-3">
                  {loading ? (
                    <div className="flex items-center gap-2 py-8 text-sm text-slate-500">
                      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      Loading items…
                    </div>
                  ) : lowStockItems.length === 0 ? (
                    <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
                      No immediate stock pressure for this facility.
                    </div>
                  ) : (
                    lowStockItems.slice(0, 6).map((item) => (
                      <div
                        key={item.id}
                        className="flex flex-col gap-2 rounded-xl border border-slate-200 px-3 py-3 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div>
                          <div className="text-sm font-medium text-slate-900">{item.productName}</div>
                          <div className="text-xs text-slate-500">
                            {item.productCode} • {item.category}
                          </div>
                        </div>
                        <div className="flex gap-6 text-xs sm:text-sm">
                          <div>
                            <div className="text-slate-500">On hand</div>
                            <div className="font-semibold text-slate-900">
                              {item.quantityOnHand} {item.unit}
                            </div>
                          </div>
                          <div>
                            <div className="text-slate-500">Reorder</div>
                            <div className="font-semibold text-slate-900">
                              {item.reorderLevel} {item.unit}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Link
                    href="/pharmacy/stock?source=inventory"
                    className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-800 hover:bg-slate-50"
                  >
                    Pharmacy stock
                  </Link>
                  <Link
                    href="/marketplace/orders?source=inventory"
                    className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-medium text-slate-800 hover:bg-slate-50"
                  >
                    Order through marketplace
                  </Link>
                </div>
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
