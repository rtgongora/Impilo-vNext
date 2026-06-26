"use client";

/**
 * Facility quality & safety dashboard. Real data via experience-BFF.
 * Route: /work/facility/rito
 * Facility id comes from the facility context store; a manual input is offered as fallback.
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";

interface FacilityDashboard {
  totalCases?: number | null;
  byStatus?: Record<string, number> | null;
  bySeverity?: Record<string, number> | null;
  openCases?: number | null;
  overdueCases?: number | null;
  openCorrectiveActions?: number | null;
  audits?: Record<string, number> | number | null;
  averageSatisfactionScore?: number | null;
}

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return (payload as T) ?? null;
}

function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}

function StatCard({ label, value }: { label: string; value: number | null | undefined }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-foreground">
        {value == null ? "—" : value}
      </div>
    </div>
  );
}

function Breakdown({ title, map }: { title: string; map?: Record<string, number> | null }) {
  const entries = map ? Object.entries(map) : [];
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="mb-2 text-sm font-semibold text-foreground">{title}</div>
      {entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">No data.</p>
      ) : (
        <ul className="space-y-1 text-sm">
          {entries.map(([k, v]) => (
            <li key={k} className="flex justify-between">
              <span className="text-muted-foreground">{k.replace(/_/g, " ")}</span>
              <span className="font-medium text-foreground">{v}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function FacilityRitoDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [manualId, setManualId] = useState("");
  const facilityId = facility?.id ?? manualId.trim();

  const [data, setData] = useState<FacilityDashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/rito/dashboards/facility?facility_id=${encodeURIComponent(facilityId)}`,
      );
      setData(unwrap<FacilityDashboard>(res));
    } catch (e) {
      setError(errMessage(e, "Could not load the facility dashboard."));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    if (facilityId) void load();
  }, [facilityId, load]);

  const auditsValue =
    data && typeof data.audits === "number" ? data.audits : undefined;
  const auditsMap =
    data && data.audits && typeof data.audits === "object"
      ? (data.audits as Record<string, number>)
      : undefined;

  return (
    <AppLayout>
      <PageShell
        title="Facility quality & safety"
        subtitle="Site-level quality, safety and client-voice rollups"
        serviceSlug="rito"
      >
        {!facility && (
          <div className="mb-4 rounded-xl border border-border bg-card p-4">
            <label className="mb-1 block text-sm font-medium text-foreground">Facility ID</label>
            <p className="mb-2 text-xs text-muted-foreground">
              No facility is selected in your work context. Enter a facility ID to load its
              dashboard.
            </p>
            <div className="flex flex-wrap gap-2">
              <input
                value={manualId}
                onChange={(e) => setManualId(e.target.value)}
                placeholder="Facility ID"
                className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
              />
              <button
                type="button"
                onClick={() => void load()}
                disabled={!manualId.trim()}
                className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
              >
                Load
              </button>
            </div>
          </div>
        )}

        {!facilityId ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            Select or enter a facility to view its quality &amp; safety dashboard.
          </div>
        ) : loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading dashboard…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load dashboard
            </div>
            <p className="mb-3">{error}</p>
            <button
              type="button"
              onClick={() => void load()}
              className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            No dashboard data available for this facility.
          </div>
        ) : (
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-5">
              <StatCard label="Total cases" value={data.totalCases} />
              <StatCard label="Open cases" value={data.openCases} />
              <StatCard label="Overdue cases" value={data.overdueCases} />
              <StatCard label="Open CAPAs" value={data.openCorrectiveActions} />
              {auditsValue !== undefined && <StatCard label="Audits" value={auditsValue} />}
              <StatCard label="Avg satisfaction" value={data.averageSatisfactionScore} />
            </div>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Breakdown title="By status" map={data.byStatus} />
              <Breakdown title="By severity" map={data.bySeverity} />
              {auditsMap && <Breakdown title="Audits" map={auditsMap} />}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
