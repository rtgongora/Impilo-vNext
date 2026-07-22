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
  // TM-B17: §24 aggregates derived from the event stream (outcome distribution + time-to-acceptance).
  const outcomeDistribution: Record<string, number> =
    (aggregates as { outcomeDistribution?: Record<string, number> }).outcomeDistribution ?? {};
  const avgTimeToAcceptance =
    (aggregates as { avgTimeToAcceptanceMinutes?: number | null }).avgTimeToAcceptanceMinutes ?? null;

  return (
    <AppLayout>
      <PageShell
        title="Telemedicine analytics"
        subtitle="Session lifecycle SLA aggregates from analytics-pipeline-service via BFF"
      >
        <div className="mb-4 flex flex-wrap gap-3">
          <Link
            href="/telemedicine"
            className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:border-primary/25"
          >
            <Activity className="h-4 w-4 text-primary" />
            Telemedicine hub
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
            <span className="ml-2 text-sm">Loading SLA aggregates…</span>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
            <p className="font-medium">Analytics unavailable</p>
            <p className="mt-1 text-xs">Verify analytics-pipeline-service and BFF proxy wiring.</p>
            <button type="button" onClick={() => void refetch()} className="mt-2 text-xs font-medium underline">
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Events accepted</p>
                <p className="mt-2 text-2xl font-semibold text-foreground">{aggregates.eventsAccepted ?? 0}</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Avg time to acceptance</p>
                <p className="mt-2 text-2xl font-semibold text-foreground">
                  {avgTimeToAcceptance == null ? "—" : `${avgTimeToAcceptance} min`}
                </p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Facility scope</p>
                <p className="mt-2 text-sm font-medium text-foreground">{facility?.name ?? "All facilities"}</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Generated</p>
                <p className="mt-2 text-sm text-foreground">{aggregates.generatedAt ?? "—"}</p>
              </div>
            </div>

            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
                <BarChart3 className="h-4 w-4 text-primary" />
                Events by type
              </h2>
              {Object.keys(eventsByType).length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">No telemedicine lifecycle events ingested yet.</p>
              ) : (
                <ul className="mt-3 divide-y divide-slate-100">
                  {Object.entries(eventsByType).map(([type, count]) => (
                    <li key={type} className="flex items-center justify-between py-2 text-sm">
                      <span className="font-medium text-foreground">{type}</span>
                      <span className="text-muted-foreground">{count}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* TM-B17 (G5): outcome distribution from completion notes, via the event stream. */}
            <section className="rounded-xl border border-border bg-card p-5 shadow-sm" data-testid="outcome-distribution">
              <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
                <BarChart3 className="h-4 w-4 text-primary" />
                Outcome distribution
              </h2>
              {Object.keys(outcomeDistribution).length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">No completed consultations with recorded outcomes yet.</p>
              ) : (
                <ul className="mt-3 divide-y divide-slate-100">
                  {Object.entries(outcomeDistribution).map(([outcome, count]) => (
                    <li key={outcome} className="flex items-center justify-between py-2 text-sm">
                      <span className="font-medium text-foreground">{outcome}</span>
                      <span className="text-muted-foreground">{count}</span>
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
