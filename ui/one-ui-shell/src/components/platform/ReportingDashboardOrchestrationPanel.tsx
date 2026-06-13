"use client";

import Link from "next/link";
import { useState } from "react";
import { BarChart3, Database, Loader2, Map } from "lucide-react";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";
import { useGenerateReport, useReportJob } from "@/hooks/queries/useReports";

function ndilaProbeLabel(
  isLoading: boolean,
  isError: boolean,
  config: { provider?: string; tileUrlTemplate?: string } | undefined,
): string {
  if (isLoading) return "Ndila tiles: probing…";
  if (isError) return "Ndila tiles: unavailable (continue via /ndila when service is up)";
  if (config?.provider || config?.tileUrlTemplate) {
    return `Ndila tiles: live${config.provider ? ` (${config.provider})` : ""}`;
  }
  return "Ndila tiles: reachable but empty config";
}

export function ReportingDashboardOrchestrationPanel() {
  const [lastJobId, setLastJobId] = useState<string | null>(null);
  const generate = useGenerateReport();
  const jobQ = useReportJob(lastJobId);
  const ndilaQ = useNdilaTileConfig();

  const jobStatus =
    jobQ.data?.data?.attributes?.status ??
    (generate.data?.data?.attributes?.status as string | undefined) ??
    null;

  return (
    <section
      className="rounded-xl border border-violet-200 bg-violet-50/50 px-4 py-4"
      data-testid="reporting-dashboard-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-violet-950">
          <BarChart3 className="mt-0.5 h-4 w-4 shrink-0 text-violet-600" />
          <div>
            <p className="font-medium">Reporting dashboard orchestration</p>
            <p>
              Run governed report jobs via Experience BFF, poll job status, and drill into clinical/facility/operational
              workspaces. Full Ndila map UX lives on <Link href="/ndila" className="font-medium underline">/ndila</Link>;
              NDR bronze/gold queries live on{" "}
              <Link href="/data-intelligence/pipelines" className="font-medium underline">
                data-intelligence pipelines
              </Link>
              .
            </p>
            {lastJobId ? (
              <p className="mt-1 text-xs font-mono text-violet-800">
                Last job: {lastJobId}
                {jobStatus ? ` · ${jobStatus}` : jobQ.isLoading ? " · polling…" : ""}
              </p>
            ) : (
              <p className="mt-1 text-xs text-violet-800">No report job queued in this session yet.</p>
            )}
            <p className="mt-1 text-xs text-violet-800" data-testid="reporting-ndila-probe">
              {ndilaProbeLabel(ndilaQ.isLoading, ndilaQ.isError, ndilaQ.data)}
            </p>
            <p className="mt-1 text-xs text-violet-800" data-testid="reporting-ndr-probe">
              NDR warehouse: open pipelines for live bronze/gold query panels (not inlined here).
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={generate.isPending}
            onClick={() =>
              generate.mutate(
                { report_type: "clinical_summary", parameters: { scope: "facility" } },
                {
                  onSuccess: (res) => {
                    const id = res.data?.id;
                    if (id) setLastJobId(id);
                  },
                },
              )
            }
            className="inline-flex items-center gap-1 rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800 disabled:opacity-50"
          >
            {generate.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
            Run clinical summary
          </button>
          <Link
            href="/reports/custom"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-300 bg-card px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
          >
            Custom reports
          </Link>
          <Link
            href="/ndila"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-300 bg-card px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
          >
            <Map className="h-3.5 w-3.5" />
            Ndila map
          </Link>
          <Link
            href="/data-intelligence/pipelines"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-300 bg-card px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
          >
            <Database className="h-3.5 w-3.5" />
            NDR pipelines
          </Link>
          <Link
            href="/data-intelligence"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-300 bg-card px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
          >
            Data &amp; intelligence
          </Link>
        </div>
      </div>
    </section>
  );
}
