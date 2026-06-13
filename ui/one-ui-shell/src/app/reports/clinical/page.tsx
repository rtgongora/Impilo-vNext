"use client";

/**
 * Clinical Reports — Generate clinical reports with date range.
 * Route: /reports/clinical
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, FileText, Calendar } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useGenerateReport } from "@/hooks/queries/useReports";

const REPORT_TYPES = [
  { value: "DAILY_PATIENT_CENSUS", label: "Daily Patient Census", description: "Count of patients seen per day" },
  { value: "DIAGNOSIS_SUMMARY", label: "Diagnosis Summary", description: "Summary of diagnoses recorded" },
  { value: "LAB_TEST_SUMMARY", label: "Lab Test Summary", description: "Lab tests ordered and results" },
  { value: "PRESCRIPTION_SUMMARY", label: "Prescription Summary", description: "Prescriptions issued and dispensed" },
];

export default function ClinicalReportsPage() {
  const router = useRouter();
  const generateReport = useGenerateReport();
  const [selectedType, setSelectedType] = useState(REPORT_TYPES[0].value);
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  function handleGenerate() {
    generateReport.mutate(
      {
        reportType: selectedType,
        parameters: { dateFrom, dateTo, category: "clinical" },
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
      <PageShell title="Clinical Reports" subtitle="Generate clinical reports for your facility">
        <div className="max-w-2xl space-y-6">
          <div className="rounded-lg border border-border bg-background p-3 text-xs text-foreground">
            Queues a <code className="text-[10px]">report_jobs</code> row via{" "}
            <code className="text-[10px]">POST /internal/v1/reports/generate</code>. Open the report detail route for status
            and <code className="text-[10px]">result_url</code> when workers populate it.
          </div>
          {/* Report Type Selection */}
          <div className="bg-card rounded-lg border border-border p-5">
            <h3 className="font-medium text-foreground mb-3">Report Type</h3>
            <div className="space-y-2">
              {REPORT_TYPES.map((rt) => (
                <label
                  key={rt.value}
                  className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                    selectedType === rt.value
                      ? "border-impilo-400 bg-primary-soft"
                      : "border-border hover:border-border"
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
                    <span className="text-sm font-medium text-foreground">{rt.label}</span>
                    <p className="text-xs text-muted-foreground mt-0.5">{rt.description}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* Date Range */}
          <div className="bg-card rounded-lg border border-border p-5">
            <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
              <Calendar className="w-4 h-4" /> Date Range
            </h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-muted-foreground mb-1">From</label>
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
              <div>
                <label className="block text-sm text-muted-foreground mb-1">To</label>
                <input
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
            </div>
          </div>

          {/* Generate */}
          <div className="flex justify-end">
            <button
              type="button"
              onClick={handleGenerate}
              disabled={!dateFrom || !dateTo || generateReport.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
            >
              {generateReport.isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <FileText className="w-4 h-4" />
              )}
              Generate Report
            </button>
          </div>

          {generateReport.isError && (
            <div className="p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
              Failed to generate report. Please try again.
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
