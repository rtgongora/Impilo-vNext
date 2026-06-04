"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, ClipboardCheck, Loader2, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import { useInventoryItems } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { randomUUID } from "@/lib/uuid";

type RowStatus = "DRAFT" | "SUBMITTED" | "APPROVED";

interface CountRow {
  id: string;
  itemId: string;
  itemLabel: string;
  systemQty: number;
  countedQty: number;
  unit: string;
  status: RowStatus;
}

export default function InventoryReconciliationPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);

  const [selectedItemId, setSelectedItemId] = useState("");
  const [countedInput, setCountedInput] = useState("");
  const [rows, setRows] = useState<CountRow[]>([]);

  const items = useMemo(() => itemsQuery.data ?? [], [itemsQuery.data]);

  const selectedItem = useMemo(() => items.find((i) => i.id === selectedItemId), [items, selectedItemId]);

  function addRow(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedItem) return;
    const counted = Number(countedInput);
    if (Number.isNaN(counted)) return;
    const id = randomUUID();
    setRows((prev) => [
      {
        id,
        itemId: selectedItem.id,
        itemLabel: `${selectedItem.productCode} — ${selectedItem.productName}`,
        systemQty: selectedItem.quantityOnHand,
        countedQty: counted,
        unit: selectedItem.unit,
        status: "DRAFT",
      },
      ...prev,
    ]);
    setCountedInput("");
  }

  function submitForApproval() {
    setRows((prev) => prev.map((r) => (r.status === "DRAFT" ? { ...r, status: "SUBMITTED" as const } : r)));
  }

  function approveAllSubmitted() {
    setRows((prev) => prev.map((r) => (r.status === "SUBMITTED" ? { ...r, status: "APPROVED" as const } : r)));
  }

  return (
    <AppLayout>
      <PageShell
        title="Stock count & reconciliation"
        subtitle="Record physical counts, compare to system on-hand, and walk a simple approval workflow before posting adjustments."
      >
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility to reconcile stock.
            <div className="mt-3">
              <Link href="/workspace" className="font-medium underline">
                Choose facility work
              </Link>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <SupplyPlaneContextBar />
            <div className="mb-2">
              <Link href="/inventory" className="text-sm font-medium text-slate-600 hover:text-slate-900">
                ← Inventory dashboard
              </Link>
            </div>

            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-900">Physical count entry</h3>
              <p className="mt-1 text-sm text-slate-600">
                Variance = counted − system. Rows stay client-side until a count POST API is available; use this workspace to
                capture evidence and approvals.
              </p>
              <form onSubmit={addRow} className="mt-4 grid gap-4 md:grid-cols-[1fr_140px_auto] md:items-end">
                <label className="block text-sm">
                  <span className="mb-1 block font-medium text-slate-700">Item</span>
                  <select
                    value={selectedItemId}
                    onChange={(ev) => setSelectedItemId(ev.target.value)}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-2"
                    disabled={itemsQuery.isLoading}
                  >
                    <option value="">Select catalog line…</option>
                    {items.map((it) => (
                      <option key={it.id} value={it.id}>
                        {it.productCode} — {it.productName} (sys {it.quantityOnHand} {it.unit})
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block text-sm">
                  <span className="mb-1 block font-medium text-slate-700">Counted qty</span>
                  <input
                    value={countedInput}
                    onChange={(ev) => setCountedInput(ev.target.value)}
                    type="number"
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  />
                </label>
                <button
                  type="submit"
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 text-sm font-medium text-white"
                >
                  <ClipboardCheck className="h-4 w-4" />
                  Add line
                </button>
              </form>
              {itemsQuery.isLoading ? (
                <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading items…
                </p>
              ) : null}
              {itemsQuery.isError ? (
                <p className="mt-3 flex items-center gap-2 text-sm text-rose-700">
                  <AlertTriangle className="h-4 w-4" />
                  Could not load catalog for this facility.
                </p>
              ) : null}
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">Reconciliation lines</h3>
                  <p className="text-sm text-slate-600">Workflow: Draft → Submitted → Approved (local state).</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={submitForApproval}
                    className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  >
                    <Send className="h-4 w-4" />
                    Submit drafts
                  </button>
                  <button
                    type="button"
                    onClick={approveAllSubmitted}
                    className="inline-flex items-center gap-2 rounded-lg bg-emerald-700 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-800"
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    Approve submitted
                  </button>
                </div>
              </div>
              {rows.length === 0 ? (
                <div className="p-12 text-center text-sm text-slate-500">No count lines yet — add at least one row.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[760px] text-sm">
                    <thead className="bg-slate-50 text-left text-slate-600">
                      <tr>
                        <th className="px-3 py-2 font-medium">Item</th>
                        <th className="px-3 py-2 font-medium">System</th>
                        <th className="px-3 py-2 font-medium">Counted</th>
                        <th className="px-3 py-2 font-medium">Variance</th>
                        <th className="px-3 py-2 font-medium">Unit</th>
                        <th className="px-3 py-2 font-medium">Workflow</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((r) => {
                        const variance = r.countedQty - r.systemQty;
                        const badge =
                          r.status === "APPROVED"
                            ? "bg-emerald-100 text-emerald-900"
                            : r.status === "SUBMITTED"
                              ? "bg-amber-100 text-amber-900"
                              : "bg-slate-100 text-slate-700";
                        return (
                          <tr key={r.id} className="border-t border-slate-100">
                            <td className="px-3 py-2 text-slate-900">{r.itemLabel}</td>
                            <td className="px-3 py-2 text-slate-700">{r.systemQty}</td>
                            <td className="px-3 py-2 font-medium text-slate-900">{r.countedQty}</td>
                            <td className="px-3 py-2">
                              <span className={variance === 0 ? "text-slate-600" : variance < 0 ? "text-rose-700" : "text-teal-800"}>
                                {variance > 0 ? `+${variance}` : variance}
                              </span>
                            </td>
                            <td className="px-3 py-2 text-slate-600">{r.unit}</td>
                            <td className="px-3 py-2">
                              <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${badge}`}>{r.status}</span>
                            </td>
                          </tr>
                        );
                      })}
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
