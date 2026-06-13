"use client";

/**
 * Nhume Analytics
 *
 * Derives quick analytics by tallying the live delivery feed. For deep
 * analytics, the same numbers are also published as events that the data
 * platform can roll up — this page intentionally focuses on what is useful
 * inside the operational loop (counts, breakdowns) and does not duplicate the
 * data warehouse's role.
 */

import { useMemo } from "react";
import { BarChart3, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeDeliveries } from "@/hooks/useNhume";

export default function NhumeAnalyticsPage() {
  const { data, isPending } = useNhumeDeliveries({ size: 250 });

  const rollups = useMemo(() => buildRollups((data?.data ?? []) as unknown as Array<Record<string, unknown>>), [data]);

  return (
    <AppLayout>
      <PageShell title="Nhume Analytics" subtitle="Operational counts, breakdowns and SLA signals" icon={<BarChart3 className="h-6 w-6" />}>
        {isPending ? (
          <div className="rounded-2xl border border-border bg-card p-10 text-center text-muted-foreground">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Tally title="By status" rollup={rollups.byStatus} />
            <Tally title="By type" rollup={rollups.byType} />
            <Tally title="By priority" rollup={rollups.byPriority} />
            <Tally title="By source" rollup={rollups.bySource} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function buildRollups(rows: Array<Record<string, unknown>>) {
  const byStatus: Record<string, number> = {};
  const byType: Record<string, number> = {};
  const byPriority: Record<string, number> = {};
  const bySource: Record<string, number> = {};
  for (const r of rows) {
    inc(byStatus, String(r.status ?? "UNKNOWN"));
    inc(byType, String(r.delivery_type ?? "UNKNOWN"));
    inc(byPriority, String(r.priority ?? "STANDARD"));
    inc(bySource, String(r.request_source ?? "UNKNOWN"));
  }
  return { byStatus, byType, byPriority, bySource };
}

function inc(bag: Record<string, number>, k: string) {
  bag[k] = (bag[k] ?? 0) + 1;
}

function Tally({ title, rollup }: { title: string; rollup: Record<string, number> }) {
  const entries = Object.entries(rollup).sort((a, b) => b[1] - a[1]);
  const max = entries.reduce((m, [, v]) => Math.max(m, v), 1);
  return (
    <div className="rounded-2xl border border-border bg-card">
      <div className="border-b border-border px-5 py-3"><h3 className="font-semibold text-foreground">{title}</h3></div>
      {entries.length === 0 ? (
        <div className="px-5 py-8 text-sm text-muted-foreground text-center">No data.</div>
      ) : (
        <ul className="divide-y divide-gray-100">
          {entries.map(([key, value]) => (
            <li key={key} className="px-5 py-2">
              <div className="flex justify-between text-sm">
                <span className="font-medium text-foreground">{key}</span>
                <span className="text-muted-foreground">{value}</span>
              </div>
              <div className="mt-1 h-1.5 rounded-full bg-neutral-100 overflow-hidden">
                <div
                  className="h-full bg-teal-500"
                  style={{ width: `${(value / max) * 100}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
