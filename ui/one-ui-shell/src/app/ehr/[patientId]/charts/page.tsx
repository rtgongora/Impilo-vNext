"use client";

/**
 * Ward Charts Hub — overview of all available inpatient charting templates.
 * Route: /ehr/[patientId]/charts | pageTitle: "Ward Charts"
 *
 * Shows available chart types from wardChartTypes with links into each chart,
 * plus an active-charts section surfacing charts with recent entries.
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { Activity, ClipboardList, Clock, Droplets, FileText, Pill, RotateCw, Utensils } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { WARD_CHARTS } from "@/data/wardChartTypes";
import { useWardChartActivity } from "@/hooks/queries/useWardCharts";

const CHART_ICONS: Record<string, React.ReactNode> = {
  "fluid-balance": <Droplets className="h-5 w-5" />,
  "drug-chart": <Pill className="h-5 w-5" />,
  "fit-chart": <Activity className="h-5 w-5" />,
  "turn-chart": <RotateCw className="h-5 w-5" />,
  "diet-chart": <Utensils className="h-5 w-5" />,
  "obs-chart": <ClipboardList className="h-5 w-5" />,
};

function formatActivity(iso: string): string {
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 60) return `Last entry ${mins} min ago`;
  return `Last entry ${Math.round(mins / 60)} h ago`;
}

export default function WardChartsPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const [filter, setFilter] = useState("");
  const { data: activity = {} } = useWardChartActivity(patientId);

  const activeCharts = WARD_CHARTS.filter((c) => activity[c.id]);
  const filtered = WARD_CHARTS.filter((c) =>
    c.name.toLowerCase().includes(filter.toLowerCase()),
  );

  return (
    <EHRLayout>
      <PageShell title="Ward Charts" subtitle="Select a charting template to record or review inpatient observations, fluid balance, medications, and more.">
        {/* Active charts with recent entries */}
        {activeCharts.length > 0 && (
          <section className="mb-6">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">Active Charts</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {activeCharts.map((chart) => (
                <Link key={chart.id} href={`/ehr/${patientId}/charts/${chart.id}`}
                  className="flex items-center gap-3 rounded-xl border border-success/25 bg-success-soft px-4 py-3 text-sm hover:bg-emerald-100 transition-colors">
                  <div className="text-primary-hover">{CHART_ICONS[chart.id] ?? <FileText className="h-5 w-5" />}</div>
                  <div className="min-w-0 flex-1">
                    <div className="font-medium text-foreground">{chart.shortName}</div>
                    <div className="flex items-center gap-1 text-xs text-primary-hover">
                      <Clock className="h-3 w-3" /> {formatActivity(String(activity[chart.id]))}
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </section>
        )}

        {/* Filter */}
        <div className="mb-4">
          <input type="text" placeholder="Filter charts..." value={filter} onChange={(e) => setFilter(e.target.value)}
            className="w-full max-w-sm rounded-lg border border-border px-3 py-2 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
        </div>

        {/* All chart types grid */}
        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((chart) => (
            <div key={chart.id} className="flex flex-col justify-between rounded-2xl border border-border bg-card p-5 shadow-sm">
              <div>
                <div className="flex items-center gap-2 text-foreground">
                  {CHART_ICONS[chart.id] ?? <FileText className="h-5 w-5" />}
                  <h3 className="text-base font-semibold text-foreground">{chart.name}</h3>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">{chart.description}</p>
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-muted-foreground">
                  <span className="rounded-full bg-neutral-100 px-2 py-0.5">{chart.frequency}</span>
                  <span className="rounded-full bg-neutral-100 px-2 py-0.5">{chart.recordedBy}</span>
                </div>
              </div>
              <Link href={`/ehr/${patientId}/charts/${chart.id}`}
                className="mt-4 inline-flex items-center justify-center rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover transition-colors">
                Open Chart
              </Link>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="col-span-full rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
              No chart types match your filter.
            </div>
          )}
        </section>
      </PageShell>
    </EHRLayout>
  );
}
