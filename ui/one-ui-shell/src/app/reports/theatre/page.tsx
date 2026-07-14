"use client";

/**
 * Wave 4 §20 — Theatre utilisation / command reporting surface.
 * Runs the seeded `theatre-utilisation` report (reporting-service) and surfaces the metrics, which are
 * projected from real theatre.* events and reconcile with the cases actually run.
 */

import { useCallback, useEffect, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface TheatreUtilisation {
  total_cases?: number;
  completed_cases?: number;
  cancelled_cases?: number;
  cancellation_rate_pct?: number;
  anaesthesia_cases?: number;
  returns_to_theatre?: number;
  unplanned_icu_transfers?: number;
  complication_cases?: number;
  blood_units_used?: number;
  implants_used?: number;
  consumables_used?: number;
  avg_theatre_minutes?: number;
}

interface ReportRun {
  status?: string;
  result?: string;
  errorMessage?: string;
  error_message?: string;
}

const TILES: { key: keyof TheatreUtilisation; label: string; suffix?: string }[] = [
  { key: "total_cases", label: "Total cases" },
  { key: "completed_cases", label: "Completed" },
  { key: "cancelled_cases", label: "Cancelled" },
  { key: "cancellation_rate_pct", label: "Cancellation rate", suffix: "%" },
  { key: "anaesthesia_cases", label: "Anaesthesia cases" },
  { key: "returns_to_theatre", label: "Returns to theatre" },
  { key: "unplanned_icu_transfers", label: "Unplanned ICU" },
  { key: "complication_cases", label: "Complication cases" },
  { key: "avg_theatre_minutes", label: "Avg theatre time", suffix: " min" },
  { key: "blood_units_used", label: "Blood units" },
  { key: "implants_used", label: "Implants" },
  { key: "consumables_used", label: "Consumables" },
];

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-foreground">{value}</p>
    </div>
  );
}

export default function TheatreUtilisationReportPage() {
  const [metrics, setMetrics] = useState<TheatreUtilisation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const run = await apiClient.post<ReportRun>("/internal/v1/reports/theatre-utilisation/run", {});
      const payload = (run as { data?: ReportRun })?.data ?? run;
      if (payload?.result) {
        const rows = JSON.parse(payload.result) as TheatreUtilisation[];
        setMetrics(Array.isArray(rows) && rows.length > 0 ? rows[0] : {});
      } else {
        setError(payload?.errorMessage ?? payload?.error_message ?? "No report data");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to run theatre utilisation report");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void runReport();
  }, [runReport]);

  return (
    <AppLayout>
      <PageShell
        title="Theatre Utilisation"
        subtitle="Cases run, cancellations, complications and commodity usage — derived from live theatre events."
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Metrics reconcile with the cases actually run (theatre.* event projection).
          </p>
          <button
            type="button"
            onClick={() => void runReport()}
            disabled={loading}
            className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:bg-accent disabled:opacity-50"
          >
            {loading ? "Running…" : "Refresh"}
          </button>
        </div>

        {error && (
          <div role="alert" className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {metrics ? (
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {TILES.map((t) => (
              <StatCard
                key={t.key}
                label={t.label}
                value={
                  metrics[t.key] == null
                    ? "—"
                    : `${metrics[t.key]}${t.suffix ?? ""}`
                }
              />
            ))}
          </div>
        ) : (
          !error && <p className="text-sm text-muted-foreground">{loading ? "Loading theatre metrics…" : "No data."}</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
