"use client";

/**
 * Data Export — Report jobs from Experience BFF (`report_jobs` + outbox).
 * Route: /admin/data-export | pageTitle: "Data Export"
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { Download, Loader2, Plus, X, Clock, CheckCircle2, AlertCircle, FileText, Calendar, RefreshCw } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAdminReportJobs, type ReportJobResource } from "@/hooks/queries/useAdminReportJobs";
import { useGenerateReport } from "@/hooks/queries/useReports";

const ADMIN_EXPORT_REPORT_TYPE = "ADMIN_DATA_EXPORT";

const DATA_TYPE_OPTIONS = [
  "Demographics",
  "Encounters",
  "Diagnoses",
  "Lab Results",
  "Medications",
  "Vitals",
  "Procedures",
  "Immunizations",
  "Inventory",
] as const;

type UiStatus = "Completed" | "Running" | "Failed" | "Scheduled" | "Queued";

interface ExportJobRow {
  id: string;
  name: string;
  status: UiStatus;
  format: string;
  dataTypes: string[];
  dateRange: string;
  createdAt: string;
  completedAt: string | null;
  fileSize: string | null;
  records: number | null;
  progress: number;
  recurring: boolean;
  schedule: string | null;
  resultUrl: string | null;
  errorMessage: string | null;
  rawParameters: Record<string, unknown>;
  reportType: string;
}

const STATUS_STYLES: Record<string, { bg: string; icon: React.ElementType }> = {
  Completed: { bg: "bg-green-100 text-green-700", icon: CheckCircle2 },
  Running: { bg: "bg-blue-100 text-blue-700", icon: RefreshCw },
  Failed: { bg: "bg-red-100 text-red-700", icon: AlertCircle },
  Scheduled: { bg: "bg-purple-100 text-purple-700", icon: Clock },
  Queued: { bg: "bg-gray-100 text-gray-600", icon: Clock },
};

function parseParams(json: string | null): Record<string, unknown> {
  if (!json) return {};
  try {
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function paramString(p: Record<string, unknown>, key: string): string | undefined {
  const v = p[key];
  return typeof v === "string" ? v : undefined;
}

function paramStringArray(p: Record<string, unknown>, key: string): string[] {
  const v = p[key];
  if (!Array.isArray(v)) return [];
  return v.filter((x): x is string => typeof x === "string");
}

function mapBackendStatus(status: string): UiStatus {
  const s = status.toUpperCase();
  if (s === "COMPLETED") return "Completed";
  if (s === "FAILED") return "Failed";
  if (s === "RUNNING" || s === "PROCESSING") return "Running";
  if (s === "SCHEDULED") return "Scheduled";
  return "Queued";
}

function mapJobToRow(job: ReportJobResource): ExportJobRow {
  const a = job.attributes;
  const p = parseParams(a.parameters);
  const name = paramString(p, "export_name") ?? paramString(p, "name") ?? a.report_type;
  const format = paramString(p, "format") ?? "—";
  const dateFrom = paramString(p, "date_from") ?? "";
  const dateTo = paramString(p, "date_to") ?? "";
  const dateRange = dateFrom && dateTo ? `${dateFrom} → ${dateTo}` : dateFrom || dateTo || "—";
  const dataTypes = paramStringArray(p, "data_types");
  const recurring = p.recurring === true;
  const schedule = paramString(p, "schedule") ?? null;
  const uiStatus = mapBackendStatus(a.status);
  let progress = 0;
  if (uiStatus === "Completed") progress = 100;
  else if (uiStatus === "Running") progress = 50;

  return {
    id: job.id,
    name,
    status: uiStatus,
    format,
    dataTypes,
    dateRange,
    createdAt: a.queued_at ? new Date(a.queued_at).toLocaleString() : "—",
    completedAt: a.completed_at ? new Date(a.completed_at).toLocaleString() : null,
    fileSize: null,
    records: null,
    progress,
    recurring,
    schedule,
    resultUrl: a.result_url,
    errorMessage: a.error_message,
    rawParameters: p,
    reportType: a.report_type,
  };
}

export default function DataExportPage() {
  const queryClient = useQueryClient();
  const jobsQ = useAdminReportJobs({ page: 0, size: 50 });
  const generateReport = useGenerateReport();

  const jobs: ExportJobRow[] = useMemo(() => (jobsQ.data?.data ?? []).map(mapJobToRow), [jobsQ.data]);
  const [showForm, setShowForm] = useState(false);
  const [newName, setNewName] = useState("");
  const [newFormat, setNewFormat] = useState("CSV");
  const [newDateFrom, setNewDateFrom] = useState("");
  const [newDateTo, setNewDateTo] = useState("");
  const [newRecurring, setNewRecurring] = useState(false);
  const [types, setTypes] = useState<Record<string, boolean>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [lastQueuedJobId, setLastQueuedJobId] = useState<string | null>(null);

  const toggleType = (t: string) => {
    setTypes((prev) => ({ ...prev, [t]: !prev[t] }));
  };

  const selectedTypes = Object.entries(types).filter(([, v]) => v).map(([k]) => k);

  function invalidateJobs() {
    void queryClient.invalidateQueries({ queryKey: ["admin", "reports", "jobs"] });
  }

  function invalidateJobDetail(jobId: string) {
    void queryClient.invalidateQueries({ queryKey: ["reports", jobId] });
  }

  function startExport() {
    setFormError(null);
    if (!newName.trim()) {
      setFormError("Export name is required.");
      return;
    }
    if (!newDateFrom || !newDateTo) {
      setFormError("Date from and date to are required.");
      return;
    }
    generateReport.mutate(
      {
        report_type: ADMIN_EXPORT_REPORT_TYPE,
        parameters: {
          export_name: newName.trim(),
          format: newFormat,
          date_from: newDateFrom,
          date_to: newDateTo,
          data_types: selectedTypes.length > 0 ? selectedTypes : [],
          recurring: newRecurring,
        },
      },
      {
        onSuccess: (res) => {
          const newId = res.data.id;
          setLastQueuedJobId(newId);
          invalidateJobDetail(newId);
          invalidateJobs();
          setShowForm(false);
          setNewName("");
          setNewDateFrom("");
          setNewDateTo("");
          setNewRecurring(false);
          setTypes({});
        },
        onError: (e: unknown) => {
          const msg =
            e && typeof e === "object" && "error" in e
              ? String((e as { error?: { message?: string } }).error?.message ?? "Request failed")
              : "Could not queue export.";
          setFormError(msg);
        },
      }
    );
  }

  function retryJob(row: ExportJobRow) {
    generateReport.mutate(
      {
        report_type: row.reportType,
        parameters: {
          ...row.rawParameters,
          retried_from_job_id: row.id,
        },
      },
      {
        onSuccess: (res) => {
          invalidateJobDetail(res.data.id);
          invalidateJobs();
        },
      }
    );
  }

  const isLoading = jobsQ.isLoading;

  return (
    <AppLayout>
      <PageShell title="Data Export" subtitle="Queued report jobs (Experience BFF)">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading export jobs...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-slate-50/90 p-4 text-sm text-slate-800 flex gap-3">
              <FileText className="w-5 h-5 text-slate-500 shrink-0" />
              <div>
                <p className="font-medium text-slate-900">How exports work here</p>
                <p className="mt-1 text-xs text-slate-600 leading-relaxed">
                  Jobs are rows in <code className="text-[11px]">report_jobs</code> created via{" "}
                  <code className="text-[11px]">POST /internal/v1/reports/generate</code>, listed via{" "}
                  <code className="text-[11px]">GET /internal/v1/admin/reports/jobs</code>, and inspected per job via{" "}
                  <code className="text-[11px]">GET /internal/v1/reports/{"{id}"}</code> (tenant-scoped). Processing, file
                  artifacts, and <code className="text-[11px]">result_url</code> are populated by downstream workers when
                  implemented—not simulated in the UI.
                </p>
              </div>
            </div>

            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Download className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-semibold text-gray-900">Export Jobs</h2>
                <span className="text-xs text-gray-400">{jobs.length} total</span>
              </div>
              <button type="button" onClick={() => setShowForm(true)} className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                <Plus className="w-4 h-4" /> New Export
              </button>
            </div>

            {lastQueuedJobId && (
              <div className="rounded-lg border border-blue-200 bg-blue-50/90 px-4 py-3 text-sm text-blue-900 flex flex-wrap items-center justify-between gap-2">
                <span>Export queued.</span>
                <Link
                  href={`/reports/${lastQueuedJobId}`}
                  className="font-medium text-blue-700 hover:underline"
                >
                  Open job status →
                </Link>
                <button
                  type="button"
                  onClick={() => setLastQueuedJobId(null)}
                  className="text-xs text-blue-600 hover:text-blue-800 ml-auto"
                >
                  Dismiss
                </button>
              </div>
            )}

            {jobsQ.isError && (
              <div className="text-sm text-red-600 border border-red-200 rounded-lg p-3">
                Could not load report jobs. You need admin role access to{" "}
                <code className="text-xs">/internal/v1/admin/reports/jobs</code>.
              </div>
            )}

            {/* New Export Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">Create New Export</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
                {formError && <p className="text-sm text-red-600 mb-3">{formError}</p>}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Export Name</label>
                    <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g., Monthly Patient Summary" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Format</label>
                    <select value={newFormat} onChange={(e) => setNewFormat(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      <option>CSV</option>
                      <option>JSON</option>
                      <option>FHIR Bundle</option>
                      <option>HL7</option>
                    </select>
                  </div>
                  <div>
                    <label htmlFor="admin-export-date-from" className="block text-xs font-medium text-gray-600 mb-1">Date From</label>
                    <input id="admin-export-date-from" type="date" value={newDateFrom} onChange={(e) => setNewDateFrom(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label htmlFor="admin-export-date-to" className="block text-xs font-medium text-gray-600 mb-1">Date To</label>
                    <input id="admin-export-date-to" type="date" value={newDateTo} onChange={(e) => setNewDateTo(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                </div>
                <div className="mt-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Data Types</label>
                  <div className="flex flex-wrap gap-2">
                    {DATA_TYPE_OPTIONS.map((dt) => (
                      <label key={dt} className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-50 rounded-lg border border-gray-200 text-xs text-gray-700 cursor-pointer hover:bg-gray-100">
                        <input type="checkbox" className="rounded" checked={!!types[dt]} onChange={() => toggleType(dt)} />
                        {dt}
                      </label>
                    ))}
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2">
                  <input type="checkbox" id="recurring" checked={newRecurring} onChange={(e) => setNewRecurring(e.target.checked)} className="rounded" />
                  <label htmlFor="recurring" className="text-xs text-gray-600">Schedule as recurring export</label>
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button type="button" onClick={startExport} disabled={generateReport.isPending} className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50">Queue Export</button>
                  <button onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                </div>
              </div>
            )}

            {/* Jobs List */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Format</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date Range</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Size / Records</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.map((job) => {
                      const style = STATUS_STYLES[job.status];
                      const StatusIcon = style.icon;
                      return (
                        <tr key={job.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3">
                            <Link
                              href={`/reports/${job.id}`}
                              className="text-gray-900 font-medium hover:text-blue-600 hover:underline"
                            >
                              {job.name}
                            </Link>
                            <p className="text-[10px] text-gray-400">{job.dataTypes.join(", ")}</p>
                            {job.recurring && <span className="text-[10px] text-purple-500 flex items-center gap-0.5 mt-0.5"><Calendar className="w-2.5 h-2.5" /> {job.schedule}</span>}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1.5">
                              <StatusIcon className="w-3.5 h-3.5" />
                              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${style.bg}`}>{job.status}</span>
                            </div>
                            {job.status === "Running" && (
                              <div className="w-20 bg-gray-200 rounded-full h-1.5 mt-1.5">
                                <div className="bg-blue-500 h-1.5 rounded-full" style={{ width: `${job.progress}%` }} />
                              </div>
                            )}
                          </td>
                          <td className="px-4 py-3 text-gray-700">{job.format}</td>
                          <td className="px-4 py-3 text-gray-500 text-xs">{job.dateRange}</td>
                          <td className="px-4 py-3 text-gray-700">
                            {job.fileSize ? `${job.fileSize} / ${job.records?.toLocaleString()} records` : "—"}
                          </td>
                          <td className="px-4 py-3">
                            {job.resultUrl ? (
                              <a href={job.resultUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 px-2.5 py-1 text-xs text-blue-600 border border-blue-200 rounded hover:bg-blue-50 transition-colors">
                                <Download className="w-3 h-3" /> Download
                              </a>
                            ) : job.status === "Completed" ? (
                              <span className="text-xs text-gray-400" title="Worker did not set result_url">No URL</span>
                            ) : null}
                            {job.status === "Failed" && (
                              <button type="button" onClick={() => retryJob(job)} disabled={generateReport.isPending} className="ml-2 inline-flex items-center gap-1 px-2.5 py-1 text-xs text-amber-600 border border-amber-200 rounded hover:bg-amber-50 transition-colors disabled:opacity-50">
                                <RefreshCw className="w-3 h-3" /> Retry
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
