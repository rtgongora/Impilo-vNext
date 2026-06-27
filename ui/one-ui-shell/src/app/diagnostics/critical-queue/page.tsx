"use client";

/**
 * Critical Results Dashboard — unacknowledged critical results requiring follow-up (criterion B).
 * Route: /diagnostics/critical-queue | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS; acknowledgement closes the loop.
 */

import { AlertTriangle, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCriticalUnacknowledged,
  useAcknowledgeCritical,
} from "@/hooks/queries/useDiagnosticsOrders";

export default function CriticalQueuePage() {
  const criticalQ = useCriticalUnacknowledged();
  const ackMut = useAcknowledgeCritical();
  const results = criticalQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Critical Results" subtitle="Unacknowledged critical results requiring follow-up">
        {criticalQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading critical results…
          </div>
        ) : criticalQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load critical results from OROS.</div>
        ) : results.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-12 text-center">
            <AlertTriangle className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No unacknowledged critical results.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-danger/30">
            <table className="w-full text-sm">
              <thead className="bg-danger-soft text-left text-xs uppercase text-danger-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Reason</th>
                  <th className="px-4 py-2">Reported by</th>
                  <th className="px-4 py-2">Flagged</th>
                  <th className="px-4 py-2">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {results.map((r) => (
                  <tr key={r.resultId} data-testid="critical-row">
                    <td className="px-4 py-3 font-mono text-xs">{r.orderId}</td>
                    <td className="px-4 py-3">{r.criticalReason ?? "—"}</td>
                    <td className="px-4 py-3">{r.reportedBy ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {r.createdAt ? new Date(r.createdAt).toLocaleString() : "—"}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => ackMut.mutate({ resultId: r.resultId })}
                        disabled={ackMut.isPending && ackMut.variables?.resultId === r.resultId}
                        className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50">
                        {ackMut.isPending && ackMut.variables?.resultId === r.resultId ? "Acknowledging…" : "Acknowledge"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {ackMut.isError && (
          <p className="mt-3 text-sm text-danger" role="alert">Failed to acknowledge. Please retry.</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
