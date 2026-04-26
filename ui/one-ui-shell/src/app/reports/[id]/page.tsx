"use client";

/**
 * Report job detail — `GET /internal/v1/reports/{id}` (ReportJob resource).
 * Route: /reports/[id]
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import {
  Loader2,
  AlertTriangle,
  FileText,
  Download,
  ArrowLeft,
  Clock,
  CheckCircle2,
  RefreshCw,
  Copy,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useReportJob } from "@/hooks/queries/useAdminReportJobs";
import { useGenerateReport } from "@/hooks/queries/useReports";

function parseJobParameters(raw: string | null): Record<string, unknown> {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

type ParametersView =
  | { kind: "empty" }
  | { kind: "object"; value: Record<string, unknown> }
  | { kind: "jsonNonObject"; text: string }
  | { kind: "invalid"; text: string };

function classifyParameters(raw: string | null): ParametersView {
  const t = raw?.trim() ?? "";
  if (!t) return { kind: "empty" };
  try {
    const v = JSON.parse(t) as unknown;
    if (v !== null && typeof v === "object" && !Array.isArray(v)) {
      return { kind: "object", value: v as Record<string, unknown> };
    }
    return { kind: "jsonNonObject", text: JSON.stringify(v, null, 2) };
  } catch {
    return { kind: "invalid", text: t };
  }
}

function statusLabel(status: string): string {
  const s = status.toUpperCase();
  if (s === "COMPLETED") return "Completed";
  if (s === "FAILED") return "Failed";
  if (s === "RUNNING" || s === "PROCESSING" || s === "GENERATING") return "In progress";
  if (s === "SCHEDULED") return "Scheduled";
  return "Queued";
}

function statusStyle(status: string): { className: string; icon: typeof Clock } {
  const s = status.toUpperCase();
  if (s === "COMPLETED") return { className: "bg-green-100 text-green-700", icon: CheckCircle2 };
  if (s === "FAILED") return { className: "bg-red-100 text-red-700", icon: AlertTriangle };
  return { className: "bg-amber-100 text-amber-700", icon: Clock };
}

function isInProgress(status: string): boolean {
  const s = status.toUpperCase();
  return ["QUEUED", "RUNNING", "PROCESSING", "GENERATING", "SCHEDULED"].includes(s);
}

export default function ReportDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");
  const { data, isLoading, error, isError } = useReportJob(id);
  const generateReport = useGenerateReport();

  const job = data?.data;
  const attrs = job?.attributes;
  const status = attrs?.status ?? "";
  const st = statusStyle(status);
  const StatusIcon = st.icon;

  const is404 =
    isError &&
    error &&
    typeof error === "object" &&
    "status" in error &&
    (error as { status?: number }).status === 404;

  async function copyJobId(jobId: string) {
    setCopyState("idle");
    try {
      await navigator.clipboard.writeText(jobId);
      setCopyState("copied");
      window.setTimeout(() => setCopyState("idle"), 2000);
    } catch {
      setCopyState("failed");
      window.setTimeout(() => setCopyState("idle"), 2000);
    }
  }

  function retry() {
    if (!attrs || !id) return;
    generateReport.mutate(
      {
        report_type: attrs.report_type,
        parameters: {
          ...parseJobParameters(attrs.parameters),
          retried_from_job_id: id,
        },
      },
      {
        onSuccess: (res) => {
          void queryClient.invalidateQueries({ queryKey: ["admin", "reports", "jobs"] });
          router.push(`/reports/${res.data.id}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Report job" subtitle="Status, timestamps, and download when the worker sets a file URL">
        <div className="mb-4">
          <Link
            href="/reports"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Reports
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading job…</span>
          </div>
        ) : is404 ? (
          <div className="bg-amber-50 rounded-lg border border-amber-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-amber-500 mx-auto mb-2" />
            <p className="text-amber-900 text-sm font-medium">Report job not found</p>
            <p className="text-amber-800/80 text-xs mt-1">
              There is no job with this id for your tenant, or the id is not a valid UUID.
            </p>
          </div>
        ) : isError || !job || !attrs ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Could not load this report job.</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {attrs.report_type === "ADMIN_DATA_EXPORT" && (
              <p className="text-xs text-gray-600">
                <Link href="/admin/data-export" className="font-medium text-impilo-600 hover:underline">
                  All data export jobs
                </Link>
                <span className="text-gray-500">
                  {" "}
                  — full list uses <code className="text-[11px]">GET /internal/v1/admin/reports/jobs</code> (admin access).
                </span>
              </p>
            )}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-lg bg-impilo-100 flex items-center justify-center shrink-0">
                    <FileText className="w-5 h-5 text-impilo-500" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-medium text-gray-900">
                      {attrs.report_type.replace(/_/g, " ")}
                    </h3>
                    <p className="text-xs text-gray-500 mt-0.5 break-all">Job ID: {job.id}</p>
                    <button
                      type="button"
                      aria-label="Copy report job id"
                      onClick={() => void copyJobId(job.id)}
                      className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-impilo-600 hover:text-impilo-800"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      {copyState === "copied" ? "Copied" : copyState === "failed" ? "Copy unavailable" : "Copy job ID"}
                    </button>
                  </div>
                </div>
                <span
                  className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full font-medium shrink-0 ${st.className}`}
                >
                  <StatusIcon className="w-3 h-3" />
                  {statusLabel(status)}
                </span>
              </div>
            </div>

            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-2">Queued parameters</h3>
              <p className="text-xs text-gray-500 mb-3">
                Stored on the job as JSON (<code className="text-[11px]">attributes.parameters</code> from the BFF).
              </p>
              {(() => {
                const pv = classifyParameters(attrs.parameters);
                if (pv.kind === "empty") {
                  return <p className="text-sm text-gray-500">No parameters were stored for this job.</p>;
                }
                if (pv.kind === "object") {
                  return (
                    <pre className="text-xs bg-slate-50 border border-slate-200 rounded-lg p-3 overflow-x-auto text-slate-800 font-mono">
                      {JSON.stringify(pv.value, null, 2)}
                    </pre>
                  );
                }
                if (pv.kind === "jsonNonObject") {
                  return (
                    <pre className="text-xs bg-slate-50 border border-slate-200 rounded-lg p-3 overflow-x-auto text-slate-800 font-mono">
                      {pv.text}
                    </pre>
                  );
                }
                return (
                  <pre className="text-xs bg-amber-50 border border-amber-200 rounded-lg p-3 overflow-x-auto text-amber-950 font-mono whitespace-pre-wrap">
                    {pv.text}
                  </pre>
                );
              })()}
            </div>

            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Details</h3>
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Status</dt>
                  <dd className="text-gray-900 font-medium text-right">{attrs.status}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Queued</dt>
                  <dd className="text-gray-900 text-right">
                    {attrs.queued_at ? new Date(attrs.queued_at).toLocaleString() : "—"}
                  </dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Started</dt>
                  <dd className="text-gray-900 text-right">
                    {attrs.started_at ? new Date(attrs.started_at).toLocaleString() : "—"}
                  </dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Completed</dt>
                  <dd className="text-gray-900 text-right">
                    {attrs.completed_at ? new Date(attrs.completed_at).toLocaleString() : "—"}
                  </dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Requested by</dt>
                  <dd className="text-gray-900 text-right break-all">{attrs.requested_by}</dd>
                </div>
              </dl>
            </div>

            {attrs.status.toUpperCase() === "COMPLETED" && attrs.result_url && (
              <div className="flex flex-wrap items-center justify-end gap-2">
                <a
                  href={attrs.result_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 px-5 py-2.5 bg-impilo-500 text-white rounded-lg text-sm font-medium hover:bg-impilo-600"
                >
                  <Download className="w-4 h-4" />
                  Open download
                </a>
              </div>
            )}

            {attrs.status.toUpperCase() === "COMPLETED" && !attrs.result_url && (
              <p className="text-sm text-gray-600 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
                This job is marked completed, but no <code className="text-xs">result_url</code> was set.
                The export file link appears here when a worker updates the job.
              </p>
            )}

            {isInProgress(status) && (
              <div className="flex items-center justify-center gap-2 py-4 text-sm text-amber-700">
                <Loader2 className="w-4 h-4 animate-spin" />
                Job is not finished yet. This page refreshes every few seconds.
              </div>
            )}

            {attrs.status.toUpperCase() === "FAILED" && (
              <div className="space-y-3">
                <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
                  {attrs.error_message?.trim()
                    ? attrs.error_message
                    : "Generation failed (no error_message on this job)."}
                </div>
                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={() => retry()}
                    disabled={generateReport.isPending}
                    className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-amber-800 border border-amber-300 rounded-lg hover:bg-amber-50 disabled:opacity-50"
                  >
                    <RefreshCw className="w-4 h-4" />
                    Queue again with same parameters
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
