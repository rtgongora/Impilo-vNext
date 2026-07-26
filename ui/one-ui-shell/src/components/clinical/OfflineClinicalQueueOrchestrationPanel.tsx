"use client";

import Link from "next/link";
import { Loader2, RefreshCw, WifiOff } from "lucide-react";
import {
  useOfflineClinicalQueue,
  usePendingOfflineReconcile,
  useSubmitOfflineReconcile,
} from "@/hooks/queries/useOfflineClinicalQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export function OfflineClinicalQueueOrchestrationPanel() {
  const facility = useFacilityStore((s) => s.facility);
  const queueQ = useOfflineClinicalQueue(facility?.id);
  const pendingQ = usePendingOfflineReconcile();
  const reconcile = useSubmitOfflineReconcile();
  const depth = Number(queueQ.data?.queue_depth ?? 0);
  const source = String(queueQ.data?.source ?? "unknown");
  const pending = pendingQ.data ?? [];
  // "0 queued item(s) · 0 pending reconcile batch(es)" is the signal a clinician uses to decide
  // that nothing captured offline is still waiting to reach the record. A failed read produces
  // the identical line, which is the one case where it is definitely wrong to trust it.
  const queueUnavailable = queueQ.isError;
  const pendingUnavailable = pendingQ.isError;
  const anyUnavailable = queueUnavailable || pendingUnavailable;

  return (
    <section
      className="mb-4 rounded-xl border border-warning/35 bg-warning-soft/80 p-4"
      data-testid="offline-clinical-queue-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-warning-foreground">
            <WifiOff className="h-4 w-4" />
            Offline clinical queue orchestration
          </p>
          <p className="mt-1 text-xs text-warning-foreground" data-testid="offline-queue-kpi-strip">
            {!facility
              ? "Facility context required to probe offline reconciliation queue"
              : queueQ.isLoading
                ? "Loading TSHEPO offline pack snapshot…"
                : anyUnavailable
                  ? "Offline queue depth could not be read — unknown, not zero. Work captured offline may still be unreconciled."
                  : `${depth} queued item(s) · ${pending.length} pending reconcile batch(es) · source ${source}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {facility && pending.length > 0 && (
            <button
              type="button"
              disabled={reconcile.isPending}
              onClick={() =>
                reconcile.mutate({
                  facility_id: facility.id,
                  conflictResolution: "user_prompted",
                })
              }
              className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-card px-2.5 py-1.5 text-xs font-medium text-warning-foreground hover:border-amber-400 disabled:opacity-50"
              data-testid="offline-reconcile-submit"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${reconcile.isPending ? "animate-spin" : ""}`} />
              Reconcile pending
            </button>
          )}
          <Link
            href="/clinical-tools"
            className="inline-flex items-center gap-1 rounded-lg border border-warning/35 bg-card px-2.5 py-1.5 text-xs font-medium text-warning-foreground hover:border-amber-300"
          >
            Offline sync tab
          </Link>
        </div>
      </div>
      {pending.length > 0 && (
        <ul className="mt-3 space-y-1 text-xs text-warning-foreground" data-testid="offline-conflict-list">
          {pending.slice(0, 3).map((batch, idx) => {
            const row = batch && typeof batch === "object" ? (batch as Record<string, unknown>) : {};
            const id = String(row.id ?? row.batchId ?? `batch-${idx}`);
            return (
              <li key={id} className="rounded border border-amber-100 bg-card px-2 py-1">
                Pending reconcile batch {id}
              </li>
            );
          })}
        </ul>
      )}
      {reconcile.isError && (
        <p className="mt-3 rounded border border-red-200 bg-red-50 px-2 py-1.5 text-xs font-medium text-red-700">
          Reconcile did not run. The pending batches are unchanged — nothing was submitted.
        </p>
      )}
      {(queueQ.isLoading || pendingQ.isLoading) && facility && (
        <div className="mt-2 flex items-center gap-2 text-xs text-warning-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Bridging web shell → TSHEPO offline federation path…
        </div>
      )}
      {reconcile.isError && (
        /* A failed reconcile submit left the button simply re-enabling, which reads as done.
           The batches are still outstanding and the offline data is still unmerged. */
        <p className="mt-2 rounded border border-red-200 bg-red-50 px-2 py-1.5 text-xs font-medium text-red-700">
          Reconciliation did not run. The pending batches are still outstanding.
        </p>
      )}
    </section>
  );
}
