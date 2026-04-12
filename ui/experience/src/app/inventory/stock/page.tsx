"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import { useInventoryItems, useInventoryMovements } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

type Tab = "current" | "movements";

function formatWhen(value: string) {
  return new Date(value).toLocaleString();
}

/** Deterministic pseudo-bin from item id for demo layout when API has no bin dimension. */
function binForItem(id: string) {
  const n = parseInt(id.replace(/\D/g, "").slice(-3) || "0", 10) || 0;
  const aisle = String.fromCharCode(65 + (n % 6));
  return `${aisle}-${String((n % 20) + 1).padStart(2, "0")}`;
}

export default function InventoryStockPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);
  const movementsQuery = useInventoryMovements(facilityId);
  const [tab, setTab] = useState<Tab>("current");

  const items = itemsQuery.data ?? [];
  const movements = movementsQuery.data ?? [];

  const fefoNote = useMemo(
    () =>
      "FEFO (first-expiry, first-out): picking prioritises the earliest-expiring inbound lots. Lot and expiry metadata will surface here when the BFF exposes batch attributes; until then, use movement timestamps as a coarse pick sequence.",
    [],
  );

  return (
    <AppLayout>
      <PageShell
        title="Stock levels & movements"
        subtitle="Current on-hand by default store/bin view, plus transfer history with FEFO-oriented guidance."
      >
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility to view stock.
            <div className="mt-3">
              <Link href="/workspace" className="font-medium underline">
                Choose facility work
              </Link>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <SupplyPlaneContextBar />
            <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
              <Link href="/inventory" className="text-sm font-medium text-slate-600 hover:text-slate-900">
                ← Inventory dashboard
              </Link>
              <Link href="/inventory/movements" className="text-xs text-slate-500 hover:text-slate-800">
                Legacy movements route
              </Link>
            </div>

            <div className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-slate-50 p-1">
              <button
                type="button"
                onClick={() => setTab("current")}
                className={`rounded-lg px-4 py-2 text-sm font-medium ${
                  tab === "current" ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Current stock
              </button>
              <button
                type="button"
                onClick={() => setTab("movements")}
                className={`rounded-lg px-4 py-2 text-sm font-medium ${
                  tab === "movements" ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Movements
              </button>
            </div>

            <div className="rounded-xl border border-teal-200/80 bg-teal-50/70 px-4 py-3 text-sm text-teal-950">
              <div className="flex items-start gap-2">
                <Package className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                <p>{fefoNote}</p>
              </div>
            </div>

            {tab === "current" ? (
              <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 px-4 py-3">
                  <h3 className="text-lg font-semibold text-slate-900">On-hand by location / bin</h3>
                  <p className="text-sm text-slate-600">
                    Default location is the facility central store. Bin codes are placeholders until warehouse master data is
                    wired.
                  </p>
                </div>
                {itemsQuery.isLoading ? (
                  <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Loading stock…
                  </div>
                ) : itemsQuery.isError ? (
                  <div className="flex items-start gap-2 p-6 text-sm text-rose-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    Failed to load items.
                  </div>
                ) : items.length === 0 ? (
                  <div className="p-12 text-center text-sm text-slate-500">No catalog lines — add items first.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[800px] text-sm">
                      <thead className="bg-slate-50 text-left text-slate-600">
                        <tr>
                          <th className="px-3 py-2 font-medium">Item</th>
                          <th className="px-3 py-2 font-medium">Code</th>
                          <th className="px-3 py-2 font-medium">Location</th>
                          <th className="px-3 py-2 font-medium">Bin</th>
                          <th className="px-3 py-2 font-medium">On hand</th>
                          <th className="px-3 py-2 font-medium">FEFO / pick hint</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((it) => (
                          <tr key={it.id} className="border-t border-slate-100">
                            <td className="px-3 py-2 font-medium text-slate-900">{it.productName}</td>
                            <td className="px-3 py-2 font-mono text-xs text-slate-600">{it.productCode}</td>
                            <td className="px-3 py-2 text-slate-700">{facility.name} — Central store</td>
                            <td className="px-3 py-2 font-mono text-xs text-slate-600">{binForItem(it.id)}</td>
                            <td className="px-3 py-2 text-slate-900">
                              {it.quantityOnHand} {it.unit}
                            </td>
                            <td className="px-3 py-2 text-slate-600">
                              Pick earliest-expiring lot first; confirm against goods receipt.
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            ) : (
              <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 px-4 py-3">
                  <h3 className="text-lg font-semibold text-slate-900">Movement history</h3>
                  <p className="text-sm text-slate-600">Transfers, receipts, and issues — newest first.</p>
                </div>
                {movementsQuery.isLoading ? (
                  <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Loading movements…
                  </div>
                ) : movementsQuery.isError ? (
                  <div className="flex items-start gap-2 p-6 text-sm text-rose-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    Failed to load movements.
                  </div>
                ) : movements.length === 0 ? (
                  <div className="p-12 text-center text-sm text-slate-500">No movements for this facility.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[900px] text-sm">
                      <thead className="bg-slate-50 text-left text-slate-600">
                        <tr>
                          <th className="px-3 py-2 font-medium">When</th>
                          <th className="px-3 py-2 font-medium">Item</th>
                          <th className="px-3 py-2 font-medium">Qty</th>
                          <th className="px-3 py-2 font-medium">From</th>
                          <th className="px-3 py-2 font-medium">To</th>
                          <th className="px-3 py-2 font-medium">Reason</th>
                          <th className="px-3 py-2 font-medium">FEFO note</th>
                        </tr>
                      </thead>
                      <tbody>
                        {movements.map((m) => (
                          <tr key={m.id} className="border-t border-slate-100">
                            <td className="whitespace-nowrap px-3 py-2 text-slate-600">{formatWhen(m.movedAt)}</td>
                            <td className="px-3 py-2 font-medium text-slate-900">{m.itemName}</td>
                            <td className="px-3 py-2 text-slate-800">{m.quantity}</td>
                            <td className="px-3 py-2 text-slate-600">{m.fromLocation}</td>
                            <td className="px-3 py-2 text-slate-600">{m.toLocation}</td>
                            <td className="px-3 py-2 text-slate-600">{m.reason}</td>
                            <td className="px-3 py-2 text-slate-600">
                              Sequence pick: {formatWhen(m.movedAt)} — verify lot/expiry at pick.
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
