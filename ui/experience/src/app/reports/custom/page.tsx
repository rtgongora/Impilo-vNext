"use client";

/**
 * Custom Report Builder — Build custom reports with flexible parameters.
 * Route: /reports/custom
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Wrench, Calendar } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useGenerateReport } from "@/hooks/queries/useReports";

const REPORT_TYPES = [
  { value: "CUSTOM_CLINICAL", label: "Clinical" },
  { value: "CUSTOM_FACILITY", label: "Facility" },
  { value: "CUSTOM_OPERATIONAL", label: "Operational" },
  { value: "CUSTOM_FINANCIAL", label: "Financial" },
  { value: "CUSTOM_INVENTORY", label: "Inventory" },
];

const OUTPUT_FORMATS = [
  { value: "PDF", label: "PDF" },
  { value: "CSV", label: "CSV" },
  { value: "XLSX", label: "Excel" },
];

export default function CustomReportsPage() {
  const router = useRouter();
  const generateReport = useGenerateReport();
  const [reportType, setReportType] = useState(REPORT_TYPES[0].value);
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [outputFormat, setOutputFormat] = useState("PDF");
  const [groupBy, setGroupBy] = useState("day");
  const [includeCharts, setIncludeCharts] = useState(true);

  function handleGenerate() {
    generateReport.mutate(
      {
        reportType,
        parameters: {
          dateFrom,
          dateTo,
          outputFormat,
          groupBy,
          includeCharts,
          category: "custom",
        },
      },
      {
        onSuccess: (res) => {
          router.push(`/reports/${res.data.id}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Custom Report Builder" subtitle="Build reports with custom parameters and filters">
        <div className="max-w-2xl space-y-6">
          {/* Report Type */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3">Report Type</h3>
            <select
              value={reportType}
              onChange={(e) => setReportType(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            >
              {REPORT_TYPES.map((rt) => (
                <option key={rt.value} value={rt.value}>{rt.label}</option>
              ))}
            </select>
          </div>

          {/* Date Range */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <Calendar className="w-4 h-4" /> Date Range
            </h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-gray-600 mb-1">From</label>
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                />
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">To</label>
                <input
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                />
              </div>
            </div>
          </div>

          {/* Parameters */}
          <div className="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
            <h3 className="font-medium text-gray-900">Parameters</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-gray-600 mb-1">Group By</label>
                <select
                  value={groupBy}
                  onChange={(e) => setGroupBy(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                >
                  <option value="day">Day</option>
                  <option value="week">Week</option>
                  <option value="month">Month</option>
                  <option value="quarter">Quarter</option>
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">Output Format</label>
                <select
                  value={outputFormat}
                  onChange={(e) => setOutputFormat(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                >
                  {OUTPUT_FORMATS.map((f) => (
                    <option key={f.value} value={f.value}>{f.label}</option>
                  ))}
                </select>
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input
                type="checkbox"
                checked={includeCharts}
                onChange={(e) => setIncludeCharts(e.target.checked)}
                className="rounded"
              />
              Include charts and visualizations
            </label>
          </div>

          {/* Generate */}
          <div className="flex justify-end gap-3">
            <button
              onClick={handleGenerate}
              disabled={!dateFrom || !dateTo || generateReport.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-impilo-500 text-white rounded-lg text-sm font-medium hover:bg-impilo-600 disabled:opacity-50"
            >
              {generateReport.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Wrench className="w-4 h-4" />
              )}
              Generate Report
            </button>
          </div>

          {generateReport.isError && (
            <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              Failed to generate report. Please try again.
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
