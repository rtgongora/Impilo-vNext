"use client";

/**
 * Facility Import Batch detail — summary, validation/import result, exclusions, duplicates,
 * acceptable-missing, source provenance.
 * Route: /admin/facility-imports/[runId]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { FacilityImportRowBrowser } from "@/components/admin/FacilityImportRowBrowser";
import { useFacilityImportRun } from "@/hooks/queries/useFacilityImports";

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="bg-card rounded-lg border border-border p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-xl font-bold text-foreground mt-0.5">{value}</p>
    </div>
  );
}

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

export default function FacilityImportBatchDetailPage() {
  const params = useParams();
  const runId = params.runId as string;
  const { data, isLoading, isError } = useFacilityImportRun(runId);
  const run = data?.data;

  return (
    <AppLayout>
      <PageShell title="Facility Import Batch" subtitle="Absorption run detail and outcome breakdown">
        <div className="mb-4">
          <Link
            href="/admin/facility-imports"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Import Batches
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : isError || !run ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load import batch</p>
          </div>
        ) : (
          <div className="max-w-4xl space-y-6">
            {/* Summary + provenance */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-medium text-foreground">Batch summary</h3>
                <FeatureMaturityBadge
                  status="connected"
                  detail="Persisted facility_import_run via TUSO"
                />
              </div>
              <dl className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
                <div>
                  <dt className="text-muted-foreground">Source label</dt>
                  <dd className="font-medium text-foreground mt-0.5">{run.sourceLabel || "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Source file</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {run.sourceFileName || "Not captured per-run yet"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Pack</dt>
                  <dd className="font-medium text-foreground mt-0.5">{run.packId}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="font-medium text-foreground mt-0.5">{run.status}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Mode</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {run.dryRun ? "Dry-run (no persistence)" : "Applied"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Initiated by</dt>
                  <dd className="font-medium text-foreground mt-0.5">{run.initiatedBy || "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Started</dt>
                  <dd className="font-medium text-foreground mt-0.5">{fmtDate(run.startedAt)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Completed</dt>
                  <dd className="font-medium text-foreground mt-0.5">{fmtDate(run.completedAt)}</dd>
                </div>
              </dl>
            </div>

            {/* Import result summary */}
            <div>
              <h3 className="font-medium text-foreground mb-3">Import result</h3>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <Stat label="Total rows" value={run.totals.recordsTotal} />
                <Stat label="Imported" value={run.totals.recordsImported} />
                <Stat label="Created" value={run.totals.recordsCreated} />
                <Stat label="Updated" value={run.totals.recordsUpdated} />
                <Stat label="Skipped" value={run.totals.recordsSkipped} />
                <Stat label="Failed" value={run.totals.recordsFailed} />
                <Stat label="Warnings" value={run.totals.warningsCount} />
                <Stat label="With coordinates" value={run.totals.withValidCoordinates} />
              </div>
            </div>

            {/* Exclusions / duplicates / acceptable-missing */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-medium text-foreground">Exclusions & review</h3>
                <Link
                  href={`/admin/facility-imports/${run.runId}/review`}
                  className="text-sm text-primary hover:text-primary-hover"
                >
                  Open review queues →
                </Link>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                <Stat label="Excluded (total)" value={run.totals.excludedTotal} />
                <Stat label="Missing code" value={run.totals.missingFacilityCode} />
                <Stat label="Duplicate code" value={run.totals.duplicateFacilityCode} />
                <Stat label="Duplicate name" value={run.totals.duplicateFacilityName} />
                <Stat label="Acceptable-missing" value={run.totals.acceptableMissing} />
              </div>
              <p className="text-xs text-muted-foreground mt-3">
                Excluded rows (missing code, duplicate code, duplicate name) are held for human review —
                never auto-imported or auto-merged. Acceptable-missing rows are imported but keep their
                gaps visible. Imported facilities remain{" "}
                <span className="font-medium">IMPORTED_PENDING_CONFIGURATION</span> until real
                configuration exists.
              </p>
            </div>

            {/* Row-level detail (real persisted rows) */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-medium text-foreground">Rows</h3>
                <FeatureMaturityBadge
                  status="connected"
                  detail="Real persisted facility_import_row detail"
                />
              </div>
              <FacilityImportRowBrowser runId={String(run.runId)} />
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
