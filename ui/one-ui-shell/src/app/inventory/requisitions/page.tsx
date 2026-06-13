"use client";

import { useState } from "react";
import Link from "next/link";
import { FileInput, Loader2, Send, Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import {
  useCreateInventoryRequisition,
  useInventoryRequisitions,
  usePatchRequisitionStatus,
} from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-neutral-100 text-foreground",
  DRAFT: "bg-neutral-100 text-foreground",
  SUBMITTED: "bg-primary-soft text-primary-hover",
  APPROVED: "bg-emerald-100 text-primary-hover",
  DISPATCHED: "bg-indigo-100 text-primary-hover",
  RECEIVED: "bg-teal-100 text-teal-900",
  FULFILLED: "bg-teal-100 text-teal-800",
  REJECTED: "bg-rose-100 text-rose-800",
};

function formatDate(value: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString();
}

export default function RequisitionsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const facilityId = facility?.id ?? "";
  const requisitionsQuery = useInventoryRequisitions(facilityId);
  const createRequisition = useCreateInventoryRequisition();
  const patchStatus = usePatchRequisitionStatus();

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

  async function approve(reqId: string) {
    if (!facility) return;
    await patchStatus.mutateAsync({ facilityId: facility.id, requisitionId: reqId, status: "APPROVED" });
  }

  async function dispatch(reqId: string) {
    if (!facility) return;
    await patchStatus.mutateAsync({ facilityId: facility.id, requisitionId: reqId, status: "DISPATCHED" });
  }

  const canApprove = (status: string) => {
    const u = status.toUpperCase();
    return u === "PENDING" || u === "SUBMITTED" || u === "DRAFT";
  };

  const canDispatch = (status: string) => {
    const u = status.toUpperCase();
    return u === "APPROVED";
  };

  return (
    <AppLayout>
      <PageShell title="Requisitions" subtitle="Create and track stock requests — PENDING through RECEIVED — with approve and dispatch actions.">
        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility before creating requisitions.
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
              <Link href="/inventory" className="text-sm font-medium text-muted-foreground hover:text-foreground">
                ← Inventory dashboard
              </Link>
            </div>

            <section className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
              <form onSubmit={handleSubmit} className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">New requisition</div>
                <h2 className="mt-1 text-xl font-semibold text-foreground">{facility.name}</h2>
                <p className="mt-2 text-sm text-muted-foreground">Capture the request here; approve and dispatch when supply authorises issue.</p>
                <div className="mt-5 space-y-4">
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Requested by</span>
                    <input
                      value={requestedBy}
                      onChange={(event) => setRequestedBy(event.target.value)}
                      required
                      className="w-full rounded-lg border border-border px-3 py-2"
                      placeholder="Pharmacy, ward, or service point"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Number of line items</span>
                    <input
                      value={itemCount}
                      onChange={(event) => setItemCount(event.target.value)}
                      required
                      min={1}
                      type="number"
                      className="w-full rounded-lg border border-border px-3 py-2"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Needed by</span>
                    <input value={neededBy} onChange={(event) => setNeededBy(event.target.value)} type="date" className="w-full rounded-lg border border-border px-3 py-2" />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Notes</span>
                    <textarea
                      value={notes}
                      onChange={(event) => setNotes(event.target.value)}
                      rows={4}
                      className="w-full rounded-lg border border-border px-3 py-2"
                      placeholder="Why the request is needed and where the stock will be used"
                    />
                  </label>
                </div>
                <div className="mt-5 flex items-center justify-between gap-3">
                  <Link href="/marketplace/orders?source=inventory-requisition" className="text-sm font-medium text-muted-foreground underline">
                    Open marketplace orders
                  </Link>
                  <button disabled={createRequisition.isPending} type="submit" className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60">
                    {createRequisition.isPending ? "Submitting..." : "Submit requisition"}
                  </button>
                </div>
                {createRequisition.isError ? <p className="mt-3 text-sm text-danger">Unable to submit requisition right now.</p> : null}
              </form>

              <div className="rounded-2xl border border-border bg-card shadow-sm">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Current requests</h3>
                    <p className="text-sm text-muted-foreground">Requester, line count, status, and created date.</p>
                  </div>
                  {requisitionsQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
                </div>
                {patchStatus.isError ? (
                  <div className="border-b border-rose-100 bg-danger-soft px-5 py-2 text-xs text-rose-800">
                    Status update failed — the BFF may not expose PATCH yet.
                  </div>
                ) : null}
                {requisitions.length === 0 ? (
                  <div className="p-10 text-center text-sm text-muted-foreground">
                    <FileInput className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                    No requisitions recorded for this facility yet.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[960px] text-sm">
                      <thead className="bg-background text-left text-muted-foreground">
                        <tr>
                          <th className="px-4 py-3 font-medium">Req #</th>
                          <th className="px-4 py-3 font-medium">Requester</th>
                          <th className="px-4 py-3 font-medium">Items</th>
                          <th className="px-4 py-3 font-medium">Status</th>
                          <th className="px-4 py-3 font-medium">Date</th>
                          <th className="px-4 py-3 font-medium text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {requisitions.map((requisition) => (
                          <tr key={requisition.id} className="border-t border-border align-top">
                            <td className="px-4 py-3 font-mono text-xs text-foreground">{requisition.requisitionNumber}</td>
                            <td className="px-4 py-3 text-foreground">{requisition.requestedBy}</td>
                            <td className="px-4 py-3 text-foreground">{requisition.itemCount}</td>
                            <td className="px-4 py-3">
                              <span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[requisition.status] ?? "bg-neutral-100 text-foreground"}`}>
                                {requisition.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{formatDate(requisition.createdAt)}</td>
                            <td className="px-4 py-3 text-right">
                              <div className="inline-flex flex-wrap justify-end gap-2">
                                <button
                                  type="button"
                                  disabled={!canApprove(requisition.status) || patchStatus.isPending}
                                  onClick={() => approve(requisition.id)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-background disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                  <Send className="h-3.5 w-3.5" />
                                  Approve
                                </button>
                                <button
                                  type="button"
                                  disabled={!canDispatch(requisition.status) || patchStatus.isPending}
                                  onClick={() => dispatch(requisition.id)}
                                  className="inline-flex items-center gap-1 rounded-lg bg-primary px-2 py-1 text-xs font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                  <Truck className="h-3.5 w-3.5" />
                                  Dispatch
                                </button>
                              </div>
                            </td>
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
