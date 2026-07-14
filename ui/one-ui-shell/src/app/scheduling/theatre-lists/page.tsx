"use client";

/**
 * OR sessions / theatre lists. Real sessions via the experience-BFF
 * (GET /internal/v1/scheduling/theatre-sessions). Create a DRAFT session; open a
 * session to build its list and publish (which funnels each item through booking
 * to the ONE case source of truth).
 * Route: /scheduling/theatre-lists
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CalendarRange, Loader2, RefreshCw, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface Session {
  id?: string;
  facilityId?: string;
  sessionDate?: string;
  slot?: string;
  leadSurgeonRef?: string;
  status?: string;
}

function asArray(p: unknown): Session[] {
  const data = (p && typeof p === "object" && "data" in p) ? (p as { data?: unknown }).data : p;
  return Array.isArray(data) ? (data as Session[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load theatre lists. Please try again.";
}

const STATUS_TONE: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-600",
  PUBLISHED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function TheatreListsPage() {
  const [data, setData] = useState<Session[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [form, setForm] = useState({ facilityId: "", sessionDate: "", leadSurgeonRef: "" });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/scheduling/theatre-sessions");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const create = useCallback(async () => {
    if (!form.facilityId || !form.sessionDate) return;
    try {
      await apiClient.post("/internal/v1/scheduling/theatre-sessions", {
        facilityId: form.facilityId,
        sessionDate: form.sessionDate,
        leadSurgeonRef: form.leadSurgeonRef || undefined,
      });
      setShowNew(false);
      void load();
    } catch (e) {
      setError(errMessage(e));
    }
  }, [form, load]);

  useEffect(() => { void load(); }, [load]);

  return (
    <AppLayout>
      <PageShell title="Theatre lists" subtitle="OR sessions — build the list, then publish to funnel each case through booking" icon={<CalendarRange className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
          <button type="button" onClick={() => setShowNew((v) => !v)} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90">
            <Plus className="h-3.5 w-3.5" /> New session
          </button>
        </div>

        {showNew && (
          <div className="mb-5 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-3">
            <input aria-label="Facility" placeholder="Facility id" value={form.facilityId} onChange={(e) => setForm({ ...form, facilityId: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
            <input aria-label="Session date" type="date" value={form.sessionDate} onChange={(e) => setForm({ ...form, sessionDate: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
            <input aria-label="Lead surgeon" placeholder="Lead surgeon ref" value={form.leadSurgeonRef} onChange={(e) => setForm({ ...form, leadSurgeonRef: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
            <button type="button" disabled={!form.facilityId || !form.sessionDate} onClick={() => void create()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50 sm:col-span-3">Create session</button>
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading theatre lists…
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
            <CalendarRange className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No theatre sessions scheduled.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3">Facility</th>
                  <th className="px-4 py-3">Lead surgeon</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((s) => (
                  <tr key={s.id} className="hover:bg-background/80">
                    <td className="px-4 py-3">
                      <Link href={`/scheduling/theatre-lists/${s.id}`} className="font-medium text-primary hover:underline">{s.sessionDate ?? "—"}</Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{s.facilityId ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{s.leadSurgeonRef ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[s.status ?? "DRAFT"] ?? "bg-slate-100 text-slate-600"}`}>{s.status ?? "DRAFT"}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/scheduling/theatre-lists" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
