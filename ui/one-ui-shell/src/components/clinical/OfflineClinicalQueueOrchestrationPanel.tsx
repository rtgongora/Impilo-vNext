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

  return (
    <section
      className="mb-4 rounded-xl border border-amber-200 bg-amber-50/80 p-4"
      data-testid="offline-clinical-queue-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-amber-900">
            <WifiOff className="h-4 w-4" />
            Offline clinical queue orchestration
          </p>
          <p className="mt-1 text-xs text-amber-800" data-testid="offline-queue-kpi-strip">
            {!facility
              ? "Facility context required to probe offline reconciliation queue"
              : queueQ.isLoading
                ? "Loading TSHEPO offline pack snapshot…"
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
              className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-white px-2.5 py-1.5 text-xs font-medium text-amber-900 hover:border-amber-400 disabled:opacity-50"
              data-testid="offline-reconcile-submit"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${reconcile.isPending ? "animate-spin" : ""}`} />
              Reconcile pending
            </button>
          )}
          <Link
            href="/clinical-tools"
            className="inline-flex items-center gap-1 rounded-lg border border-amber-200 bg-white px-2.5 py-1.5 text-xs font-medium text-amber-900 hover:border-amber-300"
          >
            Offline sync tab
          </Link>
        </div>
      </div>
      {pending.length > 0 && (
        <ul className="mt-3 space-y-1 text-xs text-amber-900" data-testid="offline-conflict-list">
          {pending.slice(0, 3).map((batch, idx) => {
            const row = batch && typeof batch === "object" ? (batch as Record<string, unknown>) : {};
            const id = String(row.id ?? row.batchId ?? `batch-${idx}`);
            return (
              <li key={id} className="rounded border border-amber-100 bg-white px-2 py-1">
                Pending reconcile batch {id}
              </li>
            );
          })}
        </ul>
      )}
      {(queueQ.isLoading || pendingQ.isLoading) && facility && (
        <div className="mt-2 flex items-center gap-2 text-xs text-amber-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Bridging web shell → TSHEPO offline federation path…
        </div>
      )}
    </section>
  );
}
