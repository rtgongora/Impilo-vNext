"use client";

/**
 * Data Export — Export job management with format selection and scheduling.
 * Route: /admin/data-export | pageTitle: "Data Export"
 */

import { useState } from "react";
import { Download, Loader2, Plus, X, Clock, CheckCircle2, AlertCircle, FileText, Calendar, RefreshCw } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ExportJob {
  id: string;
  name: string;
  status: "Completed" | "Running" | "Failed" | "Scheduled" | "Queued";
  format: "CSV" | "JSON" | "FHIR Bundle" | "HL7";
  dataTypes: string[];
  dateRange: string;
  createdAt: string;
  completedAt: string | null;
  fileSize: string | null;
  records: number | null;
  progress: number;
  recurring: boolean;
  schedule: string | null;
}

const MOCK_JOBS: ExportJob[] = [
  { id: "ex-1", name: "Monthly Patient Summary", status: "Completed", format: "CSV", dataTypes: ["Demographics", "Encounters", "Diagnoses"], dateRange: "2026-03-01 to 2026-03-31", createdAt: "2026-04-01 06:00", completedAt: "2026-04-01 06:12", fileSize: "24.5 MB", records: 15420, progress: 100, recurring: true, schedule: "1st of each month at 06:00" },
  { id: "ex-2", name: "Lab Results Export", status: "Running", format: "FHIR Bundle", dataTypes: ["Lab Results", "Orders"], dateRange: "2026-01-01 to 2026-03-31", createdAt: "2026-04-06 08:30", completedAt: null, fileSize: null, records: null, progress: 62, recurring: false, schedule: null },
  { id: "ex-3", name: "DHIS2 Aggregate Report", status: "Completed", format: "JSON", dataTypes: ["Encounters", "Diagnoses", "Procedures", "Vitals"], dateRange: "2026-03-01 to 2026-03-31", createdAt: "2026-04-02 00:00", completedAt: "2026-04-02 00:35", fileSize: "8.2 MB", records: 5230, progress: 100, recurring: true, schedule: "2nd of each month at 00:00" },
  { id: "ex-4", name: "Pharmacy Stock Audit", status: "Failed", format: "CSV", dataTypes: ["Inventory", "Dispensing"], dateRange: "2026-03-15 to 2026-04-05", createdAt: "2026-04-05 14:00", completedAt: null, fileSize: null, records: null, progress: 45, recurring: false, schedule: null },
  { id: "ex-5", name: "Weekly Immunization Report", status: "Scheduled", format: "CSV", dataTypes: ["Immunizations"], dateRange: "2026-03-30 to 2026-04-06", createdAt: "2026-04-06 00:00", completedAt: null, fileSize: null, records: null, progress: 0, recurring: true, schedule: "Every Monday at 00:00" },
];

const STATUS_STYLES: Record<string, { bg: string; icon: React.ElementType }> = {
  Completed: { bg: "bg-green-100 text-green-700", icon: CheckCircle2 },
  Running: { bg: "bg-blue-100 text-blue-700", icon: RefreshCw },
  Failed: { bg: "bg-red-100 text-red-700", icon: AlertCircle },
  Scheduled: { bg: "bg-purple-100 text-purple-700", icon: Clock },
  Queued: { bg: "bg-gray-100 text-gray-600", icon: Clock },
};

export default function DataExportPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["data-exports"],
    queryFn: async () => ({ data: MOCK_JOBS }),
  });

  const jobs = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [newName, setNewName] = useState("");
  const [newFormat, setNewFormat] = useState("CSV");
  const [newDateFrom, setNewDateFrom] = useState("");
  const [newDateTo, setNewDateTo] = useState("");
  const [newRecurring, setNewRecurring] = useState(false);

  return (
    <AppLayout>
      <PageShell title="Data Export" subtitle="Export and schedule data extractions">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading exports...</span>
          </div>
        ) : (
          <div className="space-y-6">
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

            {/* New Export Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">Create New Export</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
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
                    <label className="block text-xs font-medium text-gray-600 mb-1">Date From</label>
                    <input type="date" value={newDateFrom} onChange={(e) => setNewDateFrom(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Date To</label>
                    <input type="date" value={newDateTo} onChange={(e) => setNewDateTo(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                </div>
                <div className="mt-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Data Types</label>
                  <div className="flex flex-wrap gap-2">
                    {["Demographics", "Encounters", "Diagnoses", "Lab Results", "Medications", "Vitals", "Procedures", "Immunizations", "Inventory"].map((dt) => (
                      <label key={dt} className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-50 rounded-lg border border-gray-200 text-xs text-gray-700 cursor-pointer hover:bg-gray-100">
                        <input type="checkbox" className="rounded" />
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
                  <button className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Start Export</button>
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
                            <p className="text-gray-900 font-medium">{job.name}</p>
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
                            {job.status === "Completed" && (
                              <button className="inline-flex items-center gap-1 px-2.5 py-1 text-xs text-blue-600 border border-blue-200 rounded hover:bg-blue-50 transition-colors">
                                <Download className="w-3 h-3" /> Download
                              </button>
                            )}
                            {job.status === "Failed" && (
                              <button className="inline-flex items-center gap-1 px-2.5 py-1 text-xs text-amber-600 border border-amber-200 rounded hover:bg-amber-50 transition-colors">
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
