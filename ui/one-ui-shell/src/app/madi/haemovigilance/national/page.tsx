"use client";

import Link from "next/link";
import { ShieldAlert, Loader2, TrendingUp, TrendingDown, Minus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNationalHaemovigilanceDashboard } from "@/hooks/queries/useMadi";

function MetricCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-4">
      <p className="text-xs uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
    </div>
  );
}

function CountMap({ title, data }: { title: string; data?: Record<string, number> }) {
  const entries = Object.entries(data ?? {});
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-4">
      <h3 className="text-sm font-semibold text-gray-900 mb-3">{title}</h3>
      {entries.length === 0 ? (
        <p className="text-sm text-gray-500">No data recorded yet.</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {entries.map(([key, count]) => (
            <li key={key} className="flex justify-between">
              <span className="text-gray-700">{key.replace(/_/g, " ")}</span>
              <span className="font-medium">{count}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function NationalHaemovigilancePage() {
  const { data, isPending, isError } = useNationalHaemovigilanceDashboard();

  const trend = data?.trendIndicator ?? "STABLE";
  const TrendIcon =
    trend === "UP" ? TrendingUp : trend === "DOWN" ? TrendingDown : Minus;
  const trendColor =
    trend === "UP" ? "text-red-600" : trend === "DOWN" ? "text-green-600" : "text-gray-600";

  return (
    <AppLayout>
      <PageShell
        title="National haemovigilance"
        subtitle="Supervisory roll-up across facilities — reactions, cases, and trends"
        icon={<ShieldAlert className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-2 text-sm">
          <Link href="/madi/haemovigilance" className="text-rose-600 hover:underline">
            ← Facility reporting
          </Link>
        </div>

        {isPending && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading national dashboard…
          </div>
        )}

        {isError && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-xl p-3">
            Could not load national haemovigilance metrics.
          </p>
        )}

        {data && (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard label="Open cases" value={data.openCases ?? 0} />
              <MetricCard label="Closed (30 days)" value={data.closedCases30d ?? 0} />
              <div className="rounded-2xl border border-gray-200 bg-white p-4">
                <p className="text-xs uppercase tracking-wide text-gray-500">30-day reaction trend</p>
                <p className={`mt-1 flex items-center gap-2 text-2xl font-bold ${trendColor}`}>
                  <TrendIcon className="h-6 w-6" />
                  {trend}
                </p>
              </div>
              <MetricCard
                label="Facilities reporting"
                value={Object.keys(data.casesByFacility ?? {}).length}
              />
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <CountMap title="Reactions by type" data={data.reactionsByType} />
              <CountMap title="Reactions by severity" data={data.reactionsBySeverity} />
              <CountMap title="Cases by status" data={data.casesByStatus} />
              <CountMap title="Reactions by facility" data={data.casesByFacility} />
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
