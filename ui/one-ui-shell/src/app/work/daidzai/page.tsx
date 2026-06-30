"use client";

/**
 * Daidzai command board — provider entry. Real incident queue via experience-BFF
 * (GET /internal/v1/daidzai/incidents). Triage a request, then dispatch from the
 * incident. No fake dashboards.
 * Route: /work/daidzai
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, RefreshCw, Siren } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface Incident {
  id: string;
  incidentReference?: string;
  incidentType?: string;
  emergencyCategory?: string;
  severity?: string;
  triageCategory?: string;
  status?: string;
  createdAt?: string;
}

function asArray(p: unknown): Incident[] {
  if (Array.isArray(p)) return p as Incident[];
  return [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the incident queue. Please try again.";
}

const TRIAGE_TONE: Record<string, string> = {
  RED: "bg-red-100 text-red-700",
  ORANGE: "bg-orange-100 text-orange-700",
  YELLOW: "bg-amber-100 text-amber-700",
  GREEN: "bg-emerald-100 text-emerald-700",
  BLACK: "bg-slate-800 text-white",
};

export default function DaidzaiCommandPage() {
  const [data, setData] = useState<Incident[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/daidzai/incidents");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
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
        title="Emergency command"
        subtitle="Live incident queue — Daidzai coordinates Nhume, Ndila, PCT and Rito"
        serviceSlug="daidzai"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-2 text-sm">
            <Link href="/work/daidzai/dispatch" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Dispatch
            </Link>
            <Link href="/work/daidzai/missions" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Missions
            </Link>
            <Link href="/work/daidzai/disasters" className="rounded-lg border border-border px-3 py-1.5 hover:bg-background">
              Disasters
            </Link>
          </div>
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
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading incidents…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load incidents
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
            <Siren className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No active emergency incidents.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Triage</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Opened</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((inc) => (
                  <tr key={inc.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-mono text-xs">
                      <Link
                        href={`/work/daidzai/missions?incident=${encodeURIComponent(inc.id)}`}
                        className="font-semibold text-teal-700 hover:underline"
                      >
                        {inc.incidentReference ?? inc.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{inc.incidentType ?? "—"}</td>
                    <td className="px-4 py-3 text-foreground">
                      {inc.emergencyCategory?.replace(/_/g, " ") ?? "—"}
                    </td>
                    <td className="px-4 py-3">
                      {inc.triageCategory ? (
                        <span
                          className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${TRIAGE_TONE[inc.triageCategory] ?? "bg-slate-100 text-slate-600"}`}
                        >
                          {inc.triageCategory}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{inc.status ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {inc.createdAt ? new Date(inc.createdAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/daidzai" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
