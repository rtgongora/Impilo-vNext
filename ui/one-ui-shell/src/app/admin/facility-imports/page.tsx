"use client";

/**
 * Facility Import Batches — admin list of persisted facility master-absorption runs.
 * Route: /admin/facility-imports
 */

import Link from "next/link";
import { Loader2, AlertTriangle, Database } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityImportRuns } from "@/hooks/queries/useFacilityImports";

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

export default function FacilityImportBatchesPage() {
  const { data, isLoading, isError, refetch } = useFacilityImportRuns();
  const runs = data?.data?.runs ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Facility Import Batches"
        subtitle="National facility master absorption runs (TUSO source of truth)"
      >
        <div className="mb-4 flex items-center justify-between">
          <FeatureMaturityBadge
            status="connected"
            detail="Backed by persisted facility_import_run state via TUSO"
          />
          <button
            onClick={() => refetch()}
            className="text-sm text-primary hover:text-primary-hover"
          >
            Refresh
          </button>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : isError ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load facility import batches</p>
          </div>
        ) : runs.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Database className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">
              No facility import batches recorded yet. Runs appear here once a master pack is imported
              (dry-run or applied).
            </p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background text-left">
                  <th className="px-4 py-3 font-medium text-muted-foreground">Source label</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Mode</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Total</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Imported</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Excluded</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Failed</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Initiated by</th>
                  <th className="px-4 py-3 font-medium text-muted-foreground">Started</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {runs.map((run) => (
                  <tr key={run.runId} className="hover:bg-background transition-colors">
                    <td className="px-4 py-3">
                      <Link
                        href={`/admin/facility-imports/${run.runId}`}
                        className="font-medium text-primary hover:text-primary-hover"
                      >
                        {run.sourceLabel || run.packId || `Run ${run.runId}`}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-neutral-100 text-foreground">
                        {run.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {run.dryRun ? "Dry-run" : "Applied"}
                    </td>
                    <td className="px-4 py-3 text-foreground">{run.totals.recordsTotal}</td>
                    <td className="px-4 py-3 text-foreground">{run.totals.recordsImported}</td>
                    <td className="px-4 py-3 text-foreground">{run.totals.excludedTotal}</td>
                    <td className="px-4 py-3 text-foreground">{run.totals.recordsFailed}</td>
                    <td className="px-4 py-3 text-muted-foreground">{run.initiatedBy || "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{fmtDate(run.startedAt)}</td>
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
