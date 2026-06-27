"use client";

/**
 * Diagnostics Results Inbox — requester's orders with results ready to review (spec criterion A/F).
 * Route: /diagnostics/results-inbox | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS results inbox.
 */

import { useState } from "react";
import { Inbox, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useResultsInbox } from "@/hooks/queries/useDiagnosticsOrders";

export default function ResultsInboxPage() {
  const [requester, setRequester] = useState("");
  const inboxQ = useResultsInbox(requester.trim() || undefined);
  const orders = inboxQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Results Inbox" subtitle="Orders with diagnostic results ready to review">
        <div className="mb-4 max-w-sm">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Requester (optional)</span>
            <input value={requester} onChange={(e) => setRequester(e.target.value)}
              className="impilo-pill-input w-full" aria-label="Requester"
              placeholder="provider id — defaults to you" />
          </label>
        </div>

        {inboxQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading results inbox…
          </div>
        ) : inboxQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load the results inbox from OROS.</div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-12 text-center">
            <Inbox className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No results awaiting review.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Patient</th>
                  <th className="px-4 py-2">Accession</th>
                  <th className="px-4 py-2">Lifecycle</th>
                  <th className="px-4 py-2">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => (
                  <tr key={o.orderId} data-testid="results-inbox-row">
                    <td className="px-4 py-3 font-mono text-xs">{o.orderId}</td>
                    <td className="px-4 py-3">{o.patientCpid}</td>
                    <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                    <td className="px-4 py-3">{o.imagingState ?? o.status}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {o.updatedAt ? new Date(o.updatedAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
