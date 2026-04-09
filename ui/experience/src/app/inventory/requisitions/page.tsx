"use client";

import { useState } from "react";
import Link from "next/link";
import { FileInput, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateInventoryRequisition,
  useInventoryRequisitions,
} from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  SUBMITTED: "bg-blue-100 text-blue-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  FULFILLED: "bg-teal-100 text-teal-800",
  REJECTED: "bg-rose-100 text-rose-800",
};

function formatDate(value: string | null) {
  if (!value) return "Not set";
  return new Date(value).toLocaleDateString();
}

export default function RequisitionsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const requisitionsQuery = useInventoryRequisitions(facility?.id ?? "");
  const createRequisition = useCreateInventoryRequisition();
  const [requestedBy, setRequestedBy] = useState("");
  const [itemCount, setItemCount] = useState("3");
  const [neededBy, setNeededBy] = useState("");
  const [notes, setNotes] = useState("");
  const requisitions = requisitionsQuery.data ?? [];

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!facility) return;

    await createRequisition.mutateAsync({
      facilityId: facility.id,
      requestedBy,
      itemCount: Number(itemCount),
      neededBy: neededBy || undefined,
      notes: notes || undefined,
    });

    setRequestedBy("");
    setItemCount("3");
    setNeededBy("");
    setNotes("");
  }

  return (
    <AppLayout>
      <PageShell title="Requisitions" subtitle="Create and track stock requests without leaving the facility inventory workflow.">
        {!facility ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
            Select a facility before creating requisitions.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
              <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">New requisition</div>
                <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility.name}</h2>
                <p className="mt-2 text-sm text-slate-600">Capture the request here, then move straight into marketplace ordering or fulfilment review from the same context.</p>
                <div className="mt-5 space-y-4">
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-slate-700">Requested by</span>
                    <input value={requestedBy} onChange={(event) => setRequestedBy(event.target.value)} required className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Pharmacy, ward, or service point" />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-slate-700">Number of line items</span>
                    <input value={itemCount} onChange={(event) => setItemCount(event.target.value)} required min={1} type="number" className="w-full rounded-lg border border-slate-300 px-3 py-2" />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-slate-700">Needed by</span>
                    <input value={neededBy} onChange={(event) => setNeededBy(event.target.value)} type="date" className="w-full rounded-lg border border-slate-300 px-3 py-2" />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-slate-700">Notes</span>
                    <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={4} className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Why the request is needed and where the stock will be used" />
                  </label>
                </div>
                <div className="mt-5 flex items-center justify-between gap-3">
                  <Link href="/marketplace/orders?source=inventory-requisition" className="text-sm font-medium text-slate-600 underline">Open marketplace orders</Link>
                  <button disabled={createRequisition.isPending} type="submit" className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60">
                    {createRequisition.isPending ? "Submitting..." : "Submit requisition"}
                  </button>
                </div>
                {createRequisition.isError ? <p className="mt-3 text-sm text-rose-700">Unable to submit requisition right now.</p> : null}
              </form>

              <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">Current requests</h3>
                    <p className="text-sm text-slate-600">Follow request status before issuing stock or opening another order.</p>
                  </div>
                  {requisitionsQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin text-slate-400" /> : null}
                </div>
                {requisitions.length === 0 ? (
                  <div className="p-10 text-center text-sm text-slate-500">
                    <FileInput className="mx-auto mb-3 h-10 w-10 text-slate-300" />
                    No requisitions recorded for this facility yet.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-slate-50 text-left text-slate-600">
                        <tr>
                          <th className="px-4 py-3 font-medium">Req #</th>
                          <th className="px-4 py-3 font-medium">Requested by</th>
                          <th className="px-4 py-3 font-medium">Items</th>
                          <th className="px-4 py-3 font-medium">Needed by</th>
                          <th className="px-4 py-3 font-medium">Status</th>
                          <th className="px-4 py-3 font-medium">Created</th>
                        </tr>
                      </thead>
                      <tbody>
                        {requisitions.map((requisition) => (
                          <tr key={requisition.id} className="border-t border-slate-100 align-top">
                            <td className="px-4 py-3 font-mono text-xs text-slate-700">{requisition.requisitionNumber}</td>
                            <td className="px-4 py-3 text-slate-700">{requisition.requestedBy}</td>
                            <td className="px-4 py-3 text-slate-700">{requisition.itemCount}</td>
                            <td className="px-4 py-3 text-slate-600">{formatDate(requisition.neededBy)}</td>
                            <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[requisition.status] ?? "bg-slate-100 text-slate-700"}`}>{requisition.status}</span></td>
                            <td className="px-4 py-3 text-slate-600">{formatDate(requisition.createdAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
