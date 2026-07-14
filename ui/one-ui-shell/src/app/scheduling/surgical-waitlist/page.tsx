"use client";

/**
 * Surgical waitlist. Real entries via the experience-BFF
 * (GET /internal/v1/scheduling/surgical-waitlist). Escalation state + wait days
 * are computed server-side at read (never stored), so nothing is stale.
 * Route: /scheduling/surgical-waitlist
 */

import { useCallback, useEffect, useState } from "react";
import { ListChecks, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface WaitlistEntry {
  id?: string;
  patientId?: string;
  zibo?: string;
  clinicalPriority?: string;
  waitDays?: number;
  targetDate?: string;
  escalationState?: string;
  status?: string;
}

function asArray(p: unknown): WaitlistEntry[] {
  const data = (p && typeof p === "object" && "data" in p) ? (p as { data?: unknown }).data : p;
  return Array.isArray(data) ? (data as WaitlistEntry[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the surgical waitlist. Please try again.";
}

const ESCALATION_TONE: Record<string, string> = {
  ON_TARGET: "bg-emerald-100 text-emerald-700",
  APPROACHING: "bg-amber-100 text-amber-700",
  BREACHED: "bg-red-100 text-red-700",
};

export default function SurgicalWaitlistPage() {
  const [data, setData] = useState<WaitlistEntry[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/scheduling/surgical-waitlist?status=WAITING&sort=wait_days");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <AppLayout>
      <PageShell title="Surgical waitlist" subtitle="Patients waiting for surgery — priority, wait days and escalation against target" icon={<ListChecks className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading the waitlist…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ListChecks className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No patients are currently waiting for surgery.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Patient</th>
                  <th className="px-4 py-3">Procedure</th>
                  <th className="px-4 py-3">Priority</th>
                  <th className="px-4 py-3">Wait days</th>
                  <th className="px-4 py-3">Target date</th>
                  <th className="px-4 py-3">Escalation</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((e) => (
                  <tr key={e.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-medium">{e.patientId ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{e.zibo ?? "—"}</td>
                    <td className="px-4 py-3">{e.clinicalPriority ?? "P4"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{e.waitDays ?? 0}</td>
                    <td className="px-4 py-3 text-muted-foreground">{e.targetDate ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ESCALATION_TONE[e.escalationState ?? "ON_TARGET"] ?? "bg-slate-100 text-slate-600"}`}>
                        {e.escalationState ?? "ON_TARGET"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/scheduling/surgical-waitlist" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
