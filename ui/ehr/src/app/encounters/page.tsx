"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import type { Encounter } from "@/stores/ehrStore";

const STATUS_OPTIONS: Array<{ label: string; value: string }> = [
  { label: "All", value: "" },
  { label: "Planned", value: "PLANNED" },
  { label: "In Progress", value: "IN_PROGRESS" },
  { label: "Completed", value: "COMPLETED" },
  { label: "Cancelled", value: "CANCELLED" },
];

const STATUS_BADGE: Record<string, string> = {
  PLANNED: "bg-blue-50 text-blue-700",
  IN_PROGRESS: "bg-green-50 text-green-700",
  COMPLETED: "bg-gray-100 text-gray-600",
  CANCELLED: "bg-red-50 text-red-600",
};

const TYPE_BADGE: Record<string, string> = {
  OPD: "bg-cyan-50 text-cyan-700",
  IPD: "bg-indigo-50 text-indigo-700",
  EMERGENCY: "bg-red-50 text-red-700",
  TELEHEALTH: "bg-purple-50 text-purple-700",
  HOME_VISIT: "bg-teal-50 text-teal-700",
};

export default function EncountersPage() {
  const [statusFilter, setStatusFilter] = useState("");

  const { data, isLoading, isError } = useQuery<Encounter[]>({
    queryKey: ["encounters-list", statusFilter],
    queryFn: () => apiClient.listEncounters(statusFilter || undefined),
    staleTime: 15_000,
  });

  return (
    <div className="min-h-screen bg-impilo-surface">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard" className="opacity-80 hover:opacity-100">
              Dashboard
            </Link>
            <Link href="/encounters" className="font-medium underline underline-offset-4">
              Encounters
            </Link>
            <Link href="/orders" className="opacity-80 hover:opacity-100">
              Orders
            </Link>
            <Link href="/results" className="opacity-80 hover:opacity-100">
              Results
            </Link>
          </nav>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-semibold text-gray-900">Encounters</h2>
            <p className="text-sm text-gray-500 mt-1">
              All encounters across the facility — filter by status to manage workload
            </p>
          </div>
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
                <th className="text-left py-3 px-4 font-medium text-gray-500">Encounter ID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Patient CPID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Type</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Chief Complaint</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Status</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Started</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-gray-400">
                    Loading encounters…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-impilo-danger">
                    Failed to load encounters. Clinical API may be unavailable.
                  </td>
                </tr>
              ) : data && data.length > 0 ? (
                data.map((enc) => (
                  <tr
                    key={enc.id}
                    className="border-b border-gray-50 hover:bg-gray-50 transition-colors"
                  >
                    <td className="py-3 px-4 font-mono text-xs text-gray-500">
                      {enc.id.slice(0, 8)}…
                    </td>
                    <td className="py-3 px-4">
                      <Link
                        href={`/patients/${enc.patientCpid}`}
                        className="font-mono text-xs text-impilo-primary hover:underline"
                      >
                        {enc.patientCpid}
                      </Link>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          TYPE_BADGE[enc.encounterType] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {enc.encounterType}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-700">
                      {enc.chiefComplaint ?? "—"}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATUS_BADGE[enc.status] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {enc.status.replace("_", " ")}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-500">
                      {new Date(enc.startedAt).toLocaleString("en-ZW", {
                        day: "2-digit",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-gray-400">
                    No encounters found
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
