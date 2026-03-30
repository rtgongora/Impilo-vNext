"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type DashboardSummary } from "@/lib/apiClient";

function SummaryCard({
  label,
  value,
  href,
  accent,
}: {
  label: string;
  value: number | undefined;
  href: string;
  accent?: "warning" | "danger" | "normal";
}) {
  const accentClass =
    accent === "danger"
      ? "border-impilo-danger/30 bg-red-50"
      : accent === "warning"
        ? "border-impilo-warning/30 bg-amber-50"
        : "border-gray-200 bg-white";

  const valueClass =
    accent === "danger"
      ? "text-impilo-danger"
      : accent === "warning"
        ? "text-impilo-warning"
        : "text-impilo-primary";

  return (
    <Link href={href}>
      <div
        className={`rounded-lg border p-5 hover:shadow-sm transition-shadow cursor-pointer ${accentClass}`}
      >
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
          {label}
        </p>
        <p className={`text-3xl font-bold mt-2 ${valueClass}`}>
          {value ?? "—"}
        </p>
      </div>
    </Link>
  );
}

export default function DashboardPage() {
  const { data, isLoading, isError } = useQuery<DashboardSummary>({
    queryKey: ["dashboard-summary"],
    queryFn: () => apiClient.getDashboardSummary(),
    staleTime: 30_000,
  });

  return (
    <div className="min-h-screen bg-impilo-surface">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard" className="font-medium underline underline-offset-4">
              Dashboard
            </Link>
            <Link href="/encounters" className="opacity-80 hover:opacity-100">
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
        <span className="text-sm opacity-80">
          {new Date().toLocaleDateString("en-ZW", {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
          })}
        </span>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">
        <div className="mb-6">
          <h2 className="text-xl font-semibold text-gray-900">Clinical Dashboard</h2>
          <p className="text-sm text-gray-500 mt-1">
            Facility overview — active workload at a glance
          </p>
        </div>

        {isError && (
          <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
            Unable to load dashboard summary. The clinical API may be unavailable.
          </div>
        )}

        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 mb-8">
          <SummaryCard
            label="Active Journeys"
            value={isLoading ? undefined : data?.activeJourneys}
            href="/encounters"
            accent="normal"
          />
          <SummaryCard
            label="Pending Orders"
            value={isLoading ? undefined : data?.pendingOrders}
            href="/orders"
            accent="warning"
          />
          <SummaryCard
            label="Pending Results"
            value={isLoading ? undefined : data?.pendingResults}
            href="/results"
            accent="normal"
          />
          <SummaryCard
            label="Today's Encounters"
            value={isLoading ? undefined : data?.todayEncounters}
            href="/encounters"
            accent="normal"
          />
          <SummaryCard
            label="Critical Alerts"
            value={isLoading ? undefined : data?.criticalAlerts}
            href="/results"
            accent={data?.criticalAlerts ? "danger" : "normal"}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Link href="/encounters">
            <div className="bg-white rounded-lg border border-gray-200 p-5 hover:border-impilo-primary/40 hover:shadow-sm transition-all">
              <h3 className="font-semibold text-gray-800">Encounters</h3>
              <p className="text-sm text-gray-500 mt-1">
                View and manage patient encounters filtered by status
              </p>
              <span className="inline-block mt-3 text-xs font-medium text-impilo-primary">
                Open worklist →
              </span>
            </div>
          </Link>

          <Link href="/orders">
            <div className="bg-white rounded-lg border border-gray-200 p-5 hover:border-impilo-primary/40 hover:shadow-sm transition-all">
              <h3 className="font-semibold text-gray-800">Orders Worklist</h3>
              <p className="text-sm text-gray-500 mt-1">
                Lab, imaging, procedure, and referral orders across the facility
              </p>
              <span className="inline-block mt-3 text-xs font-medium text-impilo-primary">
                Open worklist →
              </span>
            </div>
          </Link>

          <Link href="/results">
            <div className="bg-white rounded-lg border border-gray-200 p-5 hover:border-impilo-primary/40 hover:shadow-sm transition-all">
              <h3 className="font-semibold text-gray-800">Results Inbox</h3>
              <p className="text-sm text-gray-500 mt-1">
                Incoming lab and imaging results awaiting review or sign-off
              </p>
              <span className="inline-block mt-3 text-xs font-medium text-impilo-primary">
                Open inbox →
              </span>
            </div>
          </Link>
        </div>
      </main>
    </div>
  );
}
