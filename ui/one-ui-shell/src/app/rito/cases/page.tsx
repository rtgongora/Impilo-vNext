"use client";

/**
 * Rito case worklist — real data via experience-BFF (/internal/v1/rito/cases).
 * Route: /rito/cases
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, ClipboardList, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface RitoCase {
  id: string;
  caseReference?: string;
  caseType?: string;
  pillar?: string;
  severity?: string;
  status?: string;
  title?: string;
  createdAt?: string;
  facilityId?: string;
}

interface ApiError {
  code: string;
  message: string;
}

const STATUS_OPTIONS = [
  "ALL",
  "SUBMITTED",
  "TRIAGED",
  "ASSIGNED",
  "UNDER_REVIEW",
  "RESOLVED",
  "CLOSED",
] as const;

function asArray(payload: unknown): RitoCase[] {
  if (Array.isArray(payload)) return payload as RitoCase[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as RitoCase[];
  }
  return [];
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: ApiError; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load cases. Please try again.";
}

function Badge({ value, tone }: { value?: string; tone: "severity" | "status" }) {
  if (!value) return <span className="text-muted-foreground">—</span>;
  const sev: Record<string, string> = {
    CRITICAL: "bg-red-100 text-red-700",
    HIGH: "bg-orange-100 text-orange-700",
    MEDIUM: "bg-amber-100 text-amber-700",
    LOW: "bg-slate-100 text-slate-600",
  };
  const cls =
    tone === "severity"
      ? sev[value.toUpperCase()] ?? "bg-slate-100 text-slate-600"
      : "bg-teal-50 text-teal-700";
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{value}</span>
  );
}

export default function RitoCasesPage() {
  const [status, setStatus] = useState<(typeof STATUS_OPTIONS)[number]>("ALL");
  const [data, setData] = useState<RitoCase[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = status === "ALL" ? "" : `?status=${encodeURIComponent(status)}`;
      const res = await apiClient.get<unknown>(`/internal/v1/rito/cases${qs}`);
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Case worklist"
        subtitle="Complaints, compliments, suggestions and safety reports — routed through Rito"
        serviceSlug="rito"
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Status
          </label>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as (typeof STATUS_OPTIONS)[number])}
            className="rounded-lg border border-border bg-card px-3 py-1.5 text-sm"
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s === "ALL" ? "All statuses" : s.replace(/_/g, " ")}
              </option>
            ))}
          </select>
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
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading cases…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load cases
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
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ClipboardList className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No cases match this view yet.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Severity</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((c) => (
                  <tr key={c.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-mono text-xs">
                      <Link
                        href={`/rito/cases/${encodeURIComponent(c.id)}`}
                        className="font-semibold text-teal-700 hover:underline"
                      >
                        {c.caseReference ?? c.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.caseType ?? "—"}</td>
                    <td className="px-4 py-3">
                      <Badge value={c.severity} tone="severity" />
                    </td>
                    <td className="px-4 py-3">
                      <Badge value={c.status} tone="status" />
                    </td>
                    <td className="px-4 py-3 text-foreground">{c.title ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {c.createdAt ? new Date(c.createdAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
