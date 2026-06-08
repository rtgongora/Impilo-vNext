"use client";

/**
 * Reports hub — National reporting dashboard + links to operational report workspaces.
 * Route: /reports
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  BarChart3,
  Building2,
  FileText,
  HeartPulse,
  Syringe,
  Wrench,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ReportingDashboardOrchestrationPanel } from "@/components/platform/ReportingDashboardOrchestrationPanel";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type NationalTab =
  | "dhis2"
  | "facility"
  | "disease"
  | "mortality"
  | "immunization";

const REPORT_CATEGORIES = [
  {
    title: "Clinical Reports",
    description: "Patient census, diagnosis summary, lab tests, prescriptions",
    href: "/reports/clinical",
    icon: FileText,
    color: "bg-impilo-100 text-impilo-500",
  },
  {
    title: "Facility Reports",
    description: "Bed occupancy, resource utilization, staff attendance",
    href: "/reports/facility",
    icon: Building2,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Operational Reports",
    description: "Queue wait times, encounter duration, patient flow",
    href: "/reports/operational",
    icon: BarChart3,
    color: "bg-amber-100 text-amber-600",
  },
  {
    title: "Custom Reports",
    description: "Build custom reports with flexible parameters and filters",
    href: "/reports/custom",
    icon: Wrench,
    color: "bg-green-100 text-green-600",
  },
];

function TabTable({ rows, columns }: { rows: Record<string, string>[]; columns: { key: string; label: string }[] }) {
  if (rows.length === 0) {
    return <p className="text-sm text-slate-500 py-6 text-center">No rows for this view (connect upstream analytics).</p>;
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full text-sm min-w-[520px]">
        <thead className="bg-slate-50 text-left text-xs text-slate-600">
          <tr>
            {columns.map((c) => (
              <th key={c.key} className="px-3 py-2">
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-t border-slate-100">
              {columns.map((c) => (
                <td key={c.key} className="px-3 py-2 text-slate-800">
                  {row[c.key] ?? "—"}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function ReportsHubPage() {
  const searchParams = useSearchParams();
  const fromOrg = searchParams.get("from") === "organization-admin";
  const withPlane = (href: string) => (fromOrg ? `${href}?from=organization-admin` : href);

  const [tab, setTab] = useState<NationalTab>("dhis2");
  const nationalKpiQuery = useQuery({
    queryKey: ["reports", "national-kpis"],
    queryFn: () => apiClient.get<{ data: { gold_stats?: Record<string, unknown> } }>("/internal/v1/operations/national-kpis"),
  });
  const goldStats = useMemo(
    () => (nationalKpiQuery.data?.data?.gold_stats ?? {}) as Record<string, unknown>,
    [nationalKpiQuery.data?.data?.gold_stats],
  );
  const numberOrUnavailable = (value: unknown) => (typeof value === "number" ? value.toLocaleString() : "Unavailable");

  const dhis2Rows = useMemo(
    () =>
      Object.entries(goldStats)
        .filter(([key, value]) => key !== "last_receipt_id" && key !== "last_stored_at" && key !== "watermark_updated_at" && typeof value !== "object")
        .map(([key, value]) => ({
          indicator: key,
          value: typeof value === "number" ? value.toLocaleString() : String(value ?? "—"),
          trend: "Warehouse snapshot",
          district: "National",
        })),
    [goldStats]
  );

  const facilityRows = useMemo(() => [], []);

  const diseaseRows = useMemo(() => [], []);

  const mortalityRows = useMemo(() => [], []);

  const immRows = useMemo(() => [], []);

  return (
    <AppLayout>
      <PageShell title="Reports" subtitle="National reporting dashboard and facility report workspaces">
        <OrganizationPlaneContextBar />

        <div className="mb-6">
          <ReportingDashboardOrchestrationPanel />
        </div>

        <section className="mb-10 space-y-4">
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-indigo-600" />
            <h2 className="text-lg font-semibold text-slate-900">National reporting dashboard</h2>
          </div>
          <p className="text-sm text-slate-600">
            National aggregate reporting is now wired to governed Data Plane warehouse statistics where available.
            Tabs without governed feeds continue to show explicit unavailable states (no synthetic values).
          </p>

          <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-2">
            {(
              [
                ["dhis2", "DHIS2 indicators", BarChart3],
                ["facility", "Facility performance", Building2],
                ["disease", "Disease trends", HeartPulse],
                ["mortality", "Mortality", Activity],
                ["immunization", "Immunization coverage", Syringe],
              ] as const
            ).map(([id, label, Icon]) => (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id)}
                className={`inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  tab === id ? "bg-indigo-100 text-indigo-900" : "text-slate-600 hover:bg-slate-100"
                }`}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>

          {tab === "dhis2" && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Gold encounters</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">{numberOrUnavailable(goldStats.gold_encounters)}</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Gold medications</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">{numberOrUnavailable(goldStats.gold_medications)}</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Gold labs</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">{numberOrUnavailable(goldStats.gold_labs)}</p>
                </div>
              </div>
              <TabTable
                rows={dhis2Rows}
                columns={[
                  { key: "indicator", label: "Indicator" },
                  { key: "value", label: "Value" },
                  { key: "trend", label: "Trend" },
                  { key: "district", label: "Scope" },
                ]}
              />
            </div>
          )}

          {tab === "facility" && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Facilities on target</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Avg. wait (OPD)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Stock-out alerts (7d)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
              </div>
              <TabTable
                rows={facilityRows}
                columns={[
                  { key: "facility", label: "Facility" },
                  { key: "score", label: "Performance score" },
                  { key: "wait", label: "OPD wait" },
                  { key: "throughput", label: "Throughput" },
                ]}
              />
            </div>
          )}

          {tab === "disease" && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">IDSR alerts open</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Signals above threshold</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Clusters under review</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
              </div>
              <TabTable
                rows={diseaseRows}
                columns={[
                  { key: "condition", label: "Condition" },
                  { key: "period", label: "Period" },
                  { key: "rate", label: "Rate" },
                  { key: "flag", label: "Status" },
                ]}
              />
            </div>
          )}

          {tab === "mortality" && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">U5 deaths (MTD)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Maternal deaths (YTD)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Audit coverage</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
              </div>
              <TabTable
                rows={mortalityRows}
                columns={[
                  { key: "cohort", label: "Cohort" },
                  { key: "deaths", label: "Deaths" },
                  { key: "pmr", label: "PMR / notes" },
                  { key: "notes", label: "Context" },
                ]}
              />
            </div>
          )}

          {tab === "immunization" && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Full course (DTP3)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Drop-out (Penta1→3)</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">Zero-dose catch-up</p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900">Unavailable</p>
                </div>
              </div>
              <TabTable
                rows={immRows}
                columns={[
                  { key: "antigen", label: "Antigen" },
                  { key: "coverage", label: "Coverage" },
                  { key: "drop", label: "Drop-out / change" },
                  { key: "cohort", label: "Cohort" },
                ]}
              />
            </div>
          )}
        </section>

        <section>
          <h2 className="text-sm font-semibold text-slate-900 mb-3">Report workspaces</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {REPORT_CATEGORIES.map((cat) => (
              <Link
                key={cat.href}
                href={withPlane(cat.href)}
                className="bg-white rounded-lg border border-gray-200 p-5 hover:border-impilo-200 hover:shadow-md transition-all group"
              >
                <div className="flex items-start gap-4">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 ${cat.color}`}>
                    <cat.icon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900 group-hover:text-impilo-600">{cat.title}</h3>
                    <p className="text-xs text-gray-500 mt-1">{cat.description}</p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}
