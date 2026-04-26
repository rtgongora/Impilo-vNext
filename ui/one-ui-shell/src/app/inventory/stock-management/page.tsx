"use client";

/**
 * Stock management — facility on-hand lines and procurement signals (no fabricated KPIs).
 * Route: /inventory/stock-management
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, PackageCheck, PackagePlus, RefreshCw, ShieldAlert, Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useInventoryItems, useInventoryMovements } from "@/hooks/queries/useInventory";
import type { InventoryItem } from "@/hooks/queries/useInventory";
import { useProcPurchaseOrders } from "@/hooks/queries/useProcurement";
import { countEnvelopeList } from "@/lib/apiEnvelope";

type ActionModal = "order" | "receive" | "adjust" | "transfer" | "recall" | null;

const STATUS_BADGES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-800",
  LOW_STOCK: "bg-amber-100 text-amber-800",
  INACTIVE: "bg-slate-100 text-slate-700",
};

function formatWhen(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString();
}

export default function StockManagementPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);
  const movementsQuery = useInventoryMovements(facilityId);
  const posQuery = useProcPurchaseOrders();

  const [activeModal, setActiveModal] = useState<ActionModal>(null);

  const rows: InventoryItem[] = itemsQuery.data ?? [];
  const movementCount = (movementsQuery.data ?? []).length;
  const pendingPoCount = countEnvelopeList(posQuery.data);

  const lowStockRows = useMemo(() => rows.filter((r) => r.status === "LOW_STOCK" || r.quantityOnHand <= 0), [rows]);

  return (
    <AppLayout>
      <PageShell
        title="Stock management"
        subtitle="On-hand positions from inventory-service (BFF). Operational KPIs such as turnover or fill-rate are omitted until reporting APIs are available."
      >
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility to load stock lines.
            <div className="mt-3">
              <Link href="/facility" className="font-medium underline">
                Choose facility
              </Link>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">On-hand rows</div>
                <div className="mt-2 text-3xl font-semibold text-slate-900">
                  {itemsQuery.isLoading ? "…" : rows.length}
                </div>
                <p className="mt-1 text-sm text-slate-500">Paged projection rows for this facility.</p>
              </div>
              <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-amber-800">
                  <AlertTriangle className="h-4 w-4" aria-hidden />
                  Low / zero stock
                </div>
                <div className="mt-2 text-3xl font-semibold text-amber-950">
                  {itemsQuery.isLoading ? "…" : lowStockRows.length}
                </div>
                <p className="mt-1 text-sm text-amber-900">Derived from on-hand quantities and status flags.</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Ledger window</div>
                <div className="mt-2 text-3xl font-semibold text-slate-900">
                  {movementsQuery.isLoading ? "…" : movementCount}
                </div>
                <p className="mt-1 text-sm text-slate-500">Events returned on the first ledger page (facility scoped).</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Purchase orders (list)</div>
                <div className="mt-2 text-3xl font-semibold text-slate-900">
                  {posQuery.isLoading ? "…" : pendingPoCount}
                </div>
                <p className="mt-1 text-sm text-slate-500">Count of PO rows from procurement BFF (not validated workflow state).</p>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Stock actions</h2>
              <p className="mb-3 text-sm text-slate-600">
                Posting uses inventory ledger endpoints. Forms below are intentionally lightweight — capture data in the
                dedicated movements workspace or via integrations until wizard flows land.
              </p>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => setActiveModal("order")}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <PackagePlus className="h-4 w-4 text-sky-600" aria-hidden />
                  Order
                </button>
                <button
                  type="button"
                  onClick={() => setActiveModal("receive")}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <PackageCheck className="h-4 w-4 text-emerald-600" aria-hidden />
                  Receive
                </button>
                <button
                  type="button"
                  onClick={() => setActiveModal("adjust")}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <RefreshCw className="h-4 w-4 text-amber-600" aria-hidden />
                  Adjust
                </button>
                <button
                  type="button"
                  onClick={() => setActiveModal("transfer")}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <Truck className="h-4 w-4 text-indigo-600" aria-hidden />
                  Transfer
                </button>
                <button
                  type="button"
                  onClick={() => setActiveModal("recall")}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <ShieldAlert className="h-4 w-4 text-red-600" aria-hidden />
                  Recall
                </button>
              </div>
              {activeModal ? (
                <div className="mt-4 rounded-xl border border-sky-200 bg-sky-50 p-4 text-sm text-sky-950">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold capitalize">{activeModal}</span>
                    <button type="button" className="text-xs underline" onClick={() => setActiveModal(null)}>
                      Close
                    </button>
                  </div>
                  <p className="mt-2">
                    Use{" "}
                    <Link href="/inventory/movements" className="font-semibold underline">
                      movements
                    </Link>{" "}
                    with structured JSON for ledger posts, or extend this page with React Hook Form + `useInventoryLedger*`
                    mutations.
                  </p>
                </div>
              ) : null}
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 text-left text-slate-600">
                    <tr>
                      <th className="px-4 py-3 font-medium">Item</th>
                      <th className="px-4 py-3 font-medium">Code</th>
                      <th className="px-4 py-3 font-medium text-right">Qty</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {itemsQuery.isLoading ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                          Loading on-hand…
                        </td>
                      </tr>
                    ) : itemsQuery.error ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-6 text-center text-rose-700">
                          Unable to load inventory data.
                        </td>
                      </tr>
                    ) : rows.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                          No on-hand rows for this facility yet.
                        </td>
                      </tr>
                    ) : (
                      rows.map((item) => (
                        <tr key={item.id} className="border-t border-slate-100">
                          <td className="px-4 py-3 font-medium text-slate-900">{item.productName}</td>
                          <td className="px-4 py-3 text-slate-500">{item.productCode}</td>
                          <td className="px-4 py-3 text-right font-semibold text-slate-900">
                            {item.quantityOnHand} {item.unit}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_BADGES[item.status] ?? "bg-slate-100 text-slate-700"}`}
                            >
                              {item.status.replace(/_/g, " ")}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-500">{formatWhen(item.updatedAt)}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            <div className="flex flex-wrap gap-2 text-sm">
              <Link href="/enterprise" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">
                Enterprise dashboard
              </Link>
              <Link href="/inventory" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">
                Inventory overview
              </Link>
              <Link href="/inventory/movements" className="rounded-lg border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-50">
                Movements
              </Link>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
