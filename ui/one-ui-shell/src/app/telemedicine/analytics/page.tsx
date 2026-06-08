"use client";

import Link from "next/link";
import { Activity, BarChart3, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useTelemedicineSlaAggregates } from "@/hooks/queries/useTelemedicineAnalytics";

export default function TelemedicineAnalyticsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading, error, refetch } = useTelemedicineSlaAggregates({
    facilityId: facility?.id,
  });

  const aggregates = data?.data ?? {};
  const eventsByType = aggregates.eventsByType ?? {};

  return (
    <AppLayout>
      <PageShell
        title="Telemedicine analytics"
        subtitle="Session lifecycle SLA aggregates from analytics-pipeline-service via BFF"
      >
        <div className="mb-4 flex flex-wrap gap-3">
          <Link
            href="/telemedicine"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:border-impilo-200"
          >
            <Activity className="h-4 w-4 text-impilo-500" />
            Telemedicine hub
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16 text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin" />
            <span className="ml-2 text-sm">Loading SLA aggregates…</span>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
            <p className="font-medium">Analytics unavailable</p>
            <p className="mt-1 text-xs">Verify analytics-pipeline-service and BFF proxy wiring.</p>
            <button type="button" onClick={() => void refetch()} className="mt-2 text-xs font-medium underline">
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Events accepted</p>
                <p className="mt-2 text-2xl font-semibold text-slate-900">{aggregates.eventsAccepted ?? 0}</p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Facility scope</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{facility?.name ?? "All facilities"}</p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Generated</p>
                <p className="mt-2 text-sm text-slate-700">{aggregates.generatedAt ?? "—"}</p>
              </div>
            </div>

            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
                <BarChart3 className="h-4 w-4 text-impilo-500" />
                Events by type
              </h2>
              {Object.keys(eventsByType).length === 0 ? (
                <p className="mt-3 text-sm text-slate-500">No telemedicine lifecycle events ingested yet.</p>
              ) : (
                <ul className="mt-3 divide-y divide-slate-100">
                  {Object.entries(eventsByType).map(([type, count]) => (
                    <li key={type} className="flex items-center justify-between py-2 text-sm">
                      <span className="font-medium text-slate-800">{type}</span>
                      <span className="text-slate-500">{count}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
