"use client";

/**
 * Facility Reports — Generate facility-level reports.
 * Route: /reports/facility
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Building2, Calendar } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useGenerateReport } from "@/hooks/queries/useReports";

const REPORT_TYPES = [
  { value: "BED_OCCUPANCY", label: "Bed Occupancy", description: "Bed utilization and occupancy rates" },
  { value: "RESOURCE_UTILIZATION", label: "Resource Utilization", description: "Equipment and resource usage" },
  { value: "STAFF_ATTENDANCE", label: "Staff Attendance", description: "Staff attendance and shift coverage" },
];

export default function FacilityReportsPage() {
  const router = useRouter();
  const generateReport = useGenerateReport();
  const [selectedType, setSelectedType] = useState(REPORT_TYPES[0].value);
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  function handleGenerate() {
    generateReport.mutate(
      {
        reportType: selectedType,
        parameters: { dateFrom, dateTo, category: "facility" },
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
      <PageShell title="Facility Reports" subtitle="Generate facility operations reports">
        <div className="max-w-2xl space-y-6">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">
            Uses <code className="text-[10px]">POST /internal/v1/reports/generate</code> with facility report types; detail
            and artifacts follow the same job pipeline as other report surfaces.
          </div>
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3">Report Type</h3>
            <div className="space-y-2">
              {REPORT_TYPES.map((rt) => (
                <label
                  key={rt.value}
                  className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                    selectedType === rt.value
                      ? "border-impilo-400 bg-impilo-50"
                      : "border-gray-200 hover:border-gray-300"
                  }`}
                >
                  <input
                    type="radio"
                    name="reportType"
                    value={rt.value}
                    checked={selectedType === rt.value}
                    onChange={(e) => setSelectedType(e.target.value)}
                    className="mt-0.5"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-900">{rt.label}</span>
                    <p className="text-xs text-gray-500 mt-0.5">{rt.description}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

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

          <div className="flex justify-end">
            <button
              type="button"
              onClick={handleGenerate}
              disabled={!dateFrom || !dateTo || generateReport.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-impilo-500 text-white rounded-lg text-sm font-medium hover:bg-impilo-600 disabled:opacity-50"
            >
              {generateReport.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Building2 className="w-4 h-4" />
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
