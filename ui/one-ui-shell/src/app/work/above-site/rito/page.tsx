"use client";

/**
 * Above-site quality & safety dashboard — tenant-wide rollups. Real data via experience-BFF.
 * Route: /work/above-site/rito
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface AboveSiteDashboard {
  totalCases?: number | null;
  byStatus?: Record<string, number> | null;
  bySeverity?: Record<string, number> | null;
  openCases?: number | null;
  overdueCases?: number | null;
  correctiveActions?: { byStatus?: Record<string, number> } | Record<string, number> | null;
  audits?: { byStatus?: Record<string, number> } | Record<string, number> | null;
  safetyIncidents?: { total?: number | null; sentinelCount?: number | null } | null;
}

interface SubDashboard {
  totalResponses?: number | null;
  averageScore?: number | null;
  totalCases?: number | null;
  total?: number | null;
  sentinelCount?: number | null;
  [k: string]: unknown;
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

function asStatusMap(v: unknown): Record<string, number> | undefined {
  if (!v || typeof v !== "object") return undefined;
  const obj = v as Record<string, unknown>;
  const nested = obj.byStatus;
  if (nested && typeof nested === "object") return nested as Record<string, number>;
  return obj as Record<string, number>;
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

function Breakdown({ title, map }: { title: string; map?: Record<string, number> }) {
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

export default function AboveSiteRitoDashboardPage() {
  const [data, setData] = useState<AboveSiteDashboard | null>(null);
  const [safety, setSafety] = useState<SubDashboard | null>(null);
  const [clientVoice, setClientVoice] = useState<SubDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/dashboards/above-site");
      setData(unwrap<AboveSiteDashboard>(res));
      // Inline sub-rollups — failures here don't break the main view.
      const [s, cv] = await Promise.all([
        apiClient.get<unknown>("/internal/v1/rito/dashboards/safety").catch(() => null),
        apiClient.get<unknown>("/internal/v1/rito/dashboards/client-voice").catch(() => null),
      ]);
      setSafety(unwrap<SubDashboard>(s));
      setClientVoice(unwrap<SubDashboard>(cv));
    } catch (e) {
      setError(errMessage(e, "Could not load the above-site dashboard."));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Above-site quality & safety"
        subtitle="Tenant-wide quality, safety and client-voice rollups"
        serviceSlug="rito"
      >
        <div className="mb-4 flex items-center gap-3">
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {loading ? (
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
            No above-site dashboard data available.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
              <StatCard label="Total cases" value={data.totalCases} />
              <StatCard label="Open cases" value={data.openCases} />
              <StatCard label="Overdue cases" value={data.overdueCases} />
              <StatCard
                label="Safety incidents"
                value={data.safetyIncidents?.total}
              />
              <StatCard
                label="Sentinel events"
                value={data.safetyIncidents?.sentinelCount}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Breakdown title="Cases by status" map={data.byStatus ?? undefined} />
              <Breakdown title="Cases by severity" map={data.bySeverity ?? undefined} />
              <Breakdown title="CAPAs by status" map={asStatusMap(data.correctiveActions)} />
              <Breakdown title="Audits by status" map={asStatusMap(data.audits)} />
            </div>

            {(safety || clientVoice) && (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {safety && (
                  <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                    <div className="mb-2 text-sm font-semibold text-foreground">
                      Safety rollup
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <StatCard
                        label="Incidents"
                        value={(safety.total as number | null | undefined) ?? null}
                      />
                      <StatCard
                        label="Sentinel"
                        value={(safety.sentinelCount as number | null | undefined) ?? null}
                      />
                    </div>
                  </div>
                )}
                {clientVoice && (
                  <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                    <div className="mb-2 text-sm font-semibold text-foreground">
                      Client-voice rollup
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <StatCard
                        label="Responses"
                        value={(clientVoice.totalResponses as number | null | undefined) ?? null}
                      />
                      <StatCard
                        label="Avg score"
                        value={(clientVoice.averageScore as number | null | undefined) ?? null}
                      />
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
