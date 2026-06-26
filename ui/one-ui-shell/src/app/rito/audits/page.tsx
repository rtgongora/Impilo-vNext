"use client";

/**
 * Rito audits list — real data via experience-BFF (/internal/v1/rito/audits).
 * Route: /rito/audits
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, RefreshCw, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Audit {
  id: string;
  auditReference?: string;
  auditType?: string;
  status?: string;
  facilityId?: string;
  scorePercent?: number | null;
  scheduledAt?: string;
}

function asArray(payload: unknown): Audit[] {
  if (Array.isArray(payload)) return payload as Audit[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as Audit[];
  }
  return [];
}

function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}

export default function RitoAuditsPage() {
  const [data, setData] = useState<Audit[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/audits");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e, "Could not load audits."));
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
        title="Quality audits"
        subtitle="Audits, supportive supervision and findings"
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
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading audits…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load audits
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
            <ShieldCheck className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No audits have been scheduled yet.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Score</th>
                  <th className="px-4 py-3">Scheduled</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((a) => (
                  <tr key={a.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-mono text-xs">
                      <Link
                        href={`/rito/audits/${encodeURIComponent(a.id)}`}
                        className="font-semibold text-teal-700 hover:underline"
                      >
                        {a.auditReference ?? a.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{a.auditType ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-700">
                        {a.status ?? "—"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {a.scorePercent != null ? `${a.scorePercent}%` : "—"}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {a.scheduledAt ? new Date(a.scheduledAt).toLocaleString() : "—"}
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
