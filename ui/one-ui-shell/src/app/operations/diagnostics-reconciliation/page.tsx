"use client";

/**
 * Diagnostics Reconciliation & Turnaround — operational dashboards (spec criterion H).
 * Route: /operations/diagnostics-reconciliation | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS reconciliation + metrics.
 */

import { Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useDiagnosticsReconcileSummary,
  useDiagnosticsTurnaround,
} from "@/hooks/queries/useDiagnosticsOrders";

function humanize(key: string): string {
  return key.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export default function DiagnosticsReconciliationPage() {
  const summaryQ = useDiagnosticsReconcileSummary();
  const turnaroundQ = useDiagnosticsTurnaround();

  const buckets = summaryQ.data?.data ?? {};
  const byState = turnaroundQ.data?.data?.byState ?? {};
  const total = turnaroundQ.data?.data?.totalImaging ?? 0;

  return (
    <AppLayout>
      <PageShell title="Diagnostics Reconciliation" subtitle="Stuck-order cohorts, unacknowledged criticals, and turnaround">
        <section className="mb-8">
          <h2 className="mb-3 text-sm font-semibold uppercase text-muted-foreground">Reconciliation buckets</h2>
          {summaryQ.isLoading ? (
            <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : summaryQ.isError ? (
            <div className="p-6 text-sm text-danger">Unable to load reconciliation summary.</div>
          ) : (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {Object.entries(buckets).map(([key, count]) => (
                <div key={key} className={`rounded-lg border p-4 ${count > 0 ? "border-warning/40 bg-warning-soft" : "border-gray-100"}`}>
                  <div className="text-2xl font-semibold">{count}</div>
                  <div className="text-xs text-muted-foreground">{humanize(key)}</div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase text-muted-foreground">
            Imaging workload {total ? `(${total} active)` : ""}
          </h2>
          {turnaroundQ.isLoading ? (
            <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : turnaroundQ.isError ? (
            <div className="p-6 text-sm text-danger">Unable to load turnaround metrics.</div>
          ) : Object.keys(byState).length === 0 ? (
            <p className="p-6 text-sm text-muted-foreground">No imaging orders in flight.</p>
          ) : (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {Object.entries(byState).map(([state, count]) => (
                <div key={state} className="rounded-lg border border-gray-100 p-4">
                  <div className="text-2xl font-semibold">{count}</div>
                  <div className="text-xs text-muted-foreground">{humanize(state)}</div>
                </div>
              ))}
            </div>
          )}
        </section>
      </PageShell>
    </AppLayout>
  );
}
