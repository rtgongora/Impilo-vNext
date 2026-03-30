"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ClinicalResult } from "@/lib/apiClient";

const STATUS_OPTIONS: Array<{ label: string; value: string }> = [
  { label: "All", value: "" },
  { label: "Preliminary", value: "PRELIMINARY" },
  { label: "Final", value: "FINAL" },
  { label: "Amended", value: "AMENDED" },
];

const STATUS_BADGE: Record<string, string> = {
  PRELIMINARY: "bg-amber-50 text-amber-700",
  FINAL: "bg-green-50 text-green-700",
  AMENDED: "bg-blue-50 text-blue-700",
  CANCELLED: "bg-gray-100 text-gray-500",
};

const TYPE_BADGE: Record<string, string> = {
  LAB: "bg-indigo-50 text-indigo-700",
  IMAGING: "bg-purple-50 text-purple-700",
  PROCEDURE: "bg-teal-50 text-teal-700",
};

export default function ResultsPage() {
  const [statusFilter, setStatusFilter] = useState("");

  const { data, isLoading, isError } = useQuery<ClinicalResult[]>({
    queryKey: ["results-inbox", statusFilter],
    queryFn: () => apiClient.listResults(statusFilter || undefined),
    staleTime: 15_000,
  });

  const criticalCount = data?.filter((r) => r.critical).length ?? 0;

  return (
    <div className="min-h-screen bg-impilo-surface">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard" className="opacity-80 hover:opacity-100">
              Dashboard
            </Link>
            <Link href="/encounters" className="opacity-80 hover:opacity-100">
              Encounters
            </Link>
            <Link href="/orders" className="opacity-80 hover:opacity-100">
              Orders
            </Link>
            <Link href="/results" className="font-medium underline underline-offset-4">
              Results
            </Link>
          </nav>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-semibold text-gray-900">Results Inbox</h2>
            <p className="text-sm text-gray-500 mt-1">
              Incoming lab and imaging results awaiting clinician review or sign-off
            </p>
          </div>
          {criticalCount > 0 && (
            <div className="flex items-center gap-2 px-4 py-2 bg-red-50 border border-red-200 rounded-lg">
              <span className="text-xs font-semibold text-red-700 uppercase tracking-wide">
                Critical
              </span>
              <span className="text-lg font-bold text-impilo-danger">{criticalCount}</span>
            </div>
          )}
        </div>

        <div className="mb-4 flex gap-2 flex-wrap">
          {STATUS_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              onClick={() => setStatusFilter(opt.value)}
              className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                statusFilter === opt.value
                  ? "bg-impilo-primary text-white"
                  : "bg-white border border-gray-200 text-gray-700 hover:border-impilo-primary/40"
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100">
                <th className="text-left py-3 px-4 font-medium text-gray-500">Result ID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Patient CPID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Type</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Summary</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Flags</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Status</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Reported</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Signed Off By</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-10 text-center text-gray-400">
                    Loading results…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={8} className="py-10 text-center text-impilo-danger">
                    Failed to load results. Clinical API may be unavailable.
                  </td>
                </tr>
              ) : data && data.length > 0 ? (
                data.map((result) => (
                  <tr
                    key={result.id}
                    className={`border-b border-gray-50 hover:bg-gray-50 transition-colors ${
                      result.critical ? "bg-red-50/30" : ""
                    }`}
                  >
                    <td className="py-3 px-4 font-mono text-xs text-gray-500">
                      {result.id.slice(0, 8)}…
                    </td>
                    <td className="py-3 px-4">
                      <Link
                        href={`/patients/${result.patientCpid}`}
                        className="font-mono text-xs text-impilo-primary hover:underline"
                      >
                        {result.patientCpid}
                      </Link>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          TYPE_BADGE[result.resultType] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {result.resultType}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-700 max-w-xs truncate">
                      {result.summary}
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex gap-1">
                        {result.critical && (
                          <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-700">
                            CRITICAL
                          </span>
                        )}
                        {result.abnormal && !result.critical && (
                          <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-orange-50 text-orange-700">
                            ABNORMAL
                          </span>
                        )}
                        {!result.abnormal && !result.critical && (
                          <span className="text-xs text-gray-400">—</span>
                        )}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATUS_BADGE[result.status] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {result.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-500 text-xs">
                      {new Date(result.reportedAt).toLocaleString("en-ZW", {
                        day: "2-digit",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="py-3 px-4 text-gray-500 text-xs">
                      {result.signedOffBy ?? (
                        <span className="text-amber-600 font-medium">Pending</span>
                      )}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="py-10 text-center text-gray-400">
                    No results found
                    {statusFilter ? ` with status "${statusFilter}"` : ""}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}
