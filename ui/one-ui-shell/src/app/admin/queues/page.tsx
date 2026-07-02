"use client";

/**
 * Admin Queue Configuration — read-only viewer of a facility's queue materialisation status.
 * Queue definitions are owned by TUSO facility service-point/workspace configuration and materialised
 * into PCT for care operations; they are not authored/edited here. Admins can trigger an on-demand
 * reconcile from TUSO.
 * Route: /admin/queues
 */

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Clock, Loader2, RefreshCw, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useQueueMaterializationStatus,
  useFacilityQueuesAdmin,
  useReconcileQueues,
  type FacilityQueueRow,
} from "@/hooks/queries/useFacilityQueues";

const OVERALL_LABEL: Record<string, { label: string; style: string }> = {
  MATERIALIZED: { label: "Materialised from TUSO", style: "bg-green-100 text-green-700" },
  SEED_DEMO: { label: "Seed/demo only — not materialised from TUSO", style: "bg-amber-100 text-warning-foreground" },
  STALE: { label: "Stale configuration", style: "bg-amber-100 text-warning-foreground" },
  RETIRED: { label: "Retired", style: "bg-neutral-100 text-muted-foreground" },
  MISSING: { label: "No queues configured", style: "bg-neutral-100 text-muted-foreground" },
  FAILED: { label: "Materialisation failed", style: "bg-red-100 text-danger" },
};

const STATUS_STYLE: Record<string, string> = {
  MATERIALIZED: "bg-green-100 text-green-700",
  SEED_DEMO: "bg-amber-100 text-warning-foreground",
  RETIRED: "bg-neutral-100 text-muted-foreground",
  STALE: "bg-amber-100 text-warning-foreground",
  FAILED: "bg-red-100 text-danger",
};

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

export default function AdminQueuesPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id;
  const status = useQueueMaterializationStatus(facilityId);
  const queuesQuery = useFacilityQueuesAdmin(facilityId);
  const reconcile = useReconcileQueues(facilityId);
  const [msg, setMsg] = useState<string | null>(null);

  const summary = status.data?.data;
  const queues: FacilityQueueRow[] = queuesQuery.data?.data ?? [];
  const overall = summary?.overall ?? (queues.length === 0 ? "MISSING" : undefined);
  const overallView = overall ? OVERALL_LABEL[overall] ?? { label: overall, style: "bg-neutral-100 text-foreground" } : null;

  return (
    <AppLayout>
      <PageShell title="Queue Configuration" subtitle="Materialisation status of a facility's queues">
        <div className="mb-4 flex items-center justify-between">
          <Link href="/admin" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="w-4 h-4" /> Back to Admin
          </Link>
          <FeatureMaturityBadge status="connected" detail="PCT materialisation status via BFF" />
        </div>

        <p className="mb-4 text-xs text-muted-foreground">
          Queue definitions are owned by TUSO facility service-point/workspace configuration and
          materialised into PCT for care operations. This view is read-only; use “Reconcile from TUSO”
          to re-materialise.
        </p>

        {!facilityId ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <p className="text-muted-foreground text-sm">Select a facility to view its queue materialisation status.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Status + reconcile */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-center justify-between gap-4 flex-wrap">
                <div>
                  {overallView ? (
                    <span className={`inline-block px-2 py-1 text-xs rounded-full font-medium ${overallView.style}`}>
                      {overallView.label}
                    </span>
                  ) : status.isLoading ? (
                    <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
                  ) : (
                    <span className="text-sm text-muted-foreground">Status unavailable</span>
                  )}
                  {summary && (
                    <p className="text-xs text-muted-foreground mt-2">
                      {summary.materialisedFromTuso} materialised from TUSO · {summary.seedDemoOnly} seed/demo ·
                      last materialised {fmt(summary.lastMaterializedAt)}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {msg && <span className="text-xs text-muted-foreground">{msg}</span>}
                  <button
                    className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
                    disabled={reconcile.isPending}
                    onClick={() => {
                      setMsg(null);
                      reconcile
                        .mutateAsync()
                        .then((r) => {
                          const d = r.data;
                          setMsg(d ? `Reconciled: ${d.created} created, ${d.updated} updated, ${d.retired} retired` : "Reconciled");
                        })
                        .catch((e) =>
                          setMsg(e && typeof e === "object" && "message" in e ? String((e as { message: unknown }).message) : "Reconcile failed (TUSO may be unreachable)"),
                        );
                    }}
                  >
                    <RefreshCw className={`w-4 h-4 ${reconcile.isPending ? "animate-spin" : ""}`} />
                    {reconcile.isPending ? "Reconciling…" : "Reconcile from TUSO"}
                  </button>
                </div>
              </div>
            </div>

            {/* Queue list */}
            {queuesQuery.isLoading ? (
              <div className="flex items-center justify-center py-16">
                <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
              </div>
            ) : queues.length === 0 ? (
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <Users className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">
                  No queues configured. Materialise from TUSO once service points are set up.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {queues.map((q) => {
                  const isSeed = q.source === "SEED_DEMO";
                  const statusStyle = STATUS_STYLE[q.materializationStatus] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <div key={q.queueId} className="bg-card rounded-lg border border-border p-5">
                      <div className="flex items-center justify-between gap-3 flex-wrap">
                        <div className="flex items-center gap-3">
                          <Users className="w-5 h-5 text-muted-foreground" />
                          <div>
                            <p className="text-sm font-medium text-foreground">
                              {q.name}
                              {!q.active && <span className="ml-2 text-xs text-muted-foreground">(inactive)</span>}
                            </p>
                            <div className="flex items-center gap-2 mt-1 flex-wrap">
                              <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                                {q.materializationStatus}
                              </span>
                              <span className="text-xs text-muted-foreground">source: {q.source}</span>
                              {q.sourceRef && <span className="text-xs text-muted-foreground">ref: {q.sourceRef}</span>}
                              {q.queueType && <span className="text-xs text-muted-foreground">type: {q.queueType}</span>}
                            </div>
                          </div>
                        </div>
                        <div className="text-xs text-muted-foreground flex items-center gap-1">
                          <Clock className="w-3 h-3" /> materialised {fmt(q.lastMaterializedAt)}
                        </div>
                      </div>
                      {isSeed && (
                        <p className="mt-2 text-xs text-warning-foreground">
                          Seed/demo queue — not materialised from TUSO. Not production facility truth.
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
