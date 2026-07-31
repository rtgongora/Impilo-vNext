"use client";

/**
 * OR session detail. Real session + its list via the experience-BFF. Add cases,
 * then publish — publish funnels each item through booking-service to the ONE
 * case source of truth (inpatient.procedure_episode). Conflicts refuse publish.
 * Route: /scheduling/theatre-lists/[sessionId]
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { CalendarRange, Loader2, Plus, PlayCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface OrItem { id?: string; patientId?: string; zibo?: string; status?: string; procedureEpisodeRef?: string }
interface SessionDetail { id?: string; sessionDate?: string; facilityId?: string; status?: string; items?: OrItem[] }

function unwrap(p: unknown): SessionDetail {
  const data = (p && typeof p === "object" && "data" in p) ? (p as { data?: unknown }).data : p;
  return (data ?? {}) as SessionDetail;
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; blockers?: unknown[]; status?: number };
    if (Array.isArray(obj.blockers) && obj.blockers.length) return "Publish blocked by conflicts — resolve or override.";
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Something went wrong. Please try again.";
}

export default function TheatreSessionDetailPage() {
  const params = useParams<{ sessionId: string }>();
  const sessionId = params?.sessionId ?? "";
  const [session, setSession] = useState<SessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [item, setItem] = useState({ patientId: "", zibo: "", surgeonRef: "" });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(`/internal/v1/scheduling/theatre-sessions/${sessionId}`);
      setSession(unwrap(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  const addItem = useCallback(async () => {
    if (!item.patientId) return;
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/scheduling/theatre-sessions/${sessionId}/items`, {
        patientId: item.patientId, zibo: item.zibo || undefined, surgeonRef: item.surgeonRef || undefined,
      });
      setItem({ patientId: "", zibo: "", surgeonRef: "" });
      void load();
    } catch (e) {
      setMsg(errMessage(e));
    }
  }, [item, sessionId, load]);

  const publish = useCallback(async () => {
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/scheduling/theatre-sessions/${sessionId}/publish`, {});
      setMsg("Session published — each case funnelled through booking to its procedure episode.");
      void load();
    } catch (e) {
      setMsg(errMessage(e));
    }
  }, [sessionId, load]);

  useEffect(() => { void load(); }, [load]);

  return (
    <AppLayout>
      <PageShell title="Theatre list" subtitle="Build the OR list and publish to the one case source of truth" icon={<CalendarRange className="h-5 w-5" />}>
        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading session…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              Retry
            </button>
          </div>
        ) : (
          <>
            <div className="mb-4 flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground">{session?.facilityId} · {session?.sessionDate}</p>
                <p className="text-sm font-medium">{session?.status}</p>
              </div>
              <button type="button" onClick={() => void publish()} disabled={session?.status !== "DRAFT"} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">
                <PlayCircle className="h-3.5 w-3.5" /> Publish
              </button>
            </div>
            {msg && <p className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{msg}</p>}

            <div className="mb-5 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-4">
              <input aria-label="Patient" placeholder="Patient (CPID)" value={item.patientId} onChange={(e) => setItem({ ...item, patientId: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
              <input aria-label="Procedure" placeholder="Procedure (ZIBO)" value={item.zibo} onChange={(e) => setItem({ ...item, zibo: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
              <input aria-label="Surgeon" placeholder="Surgeon ref" value={item.surgeonRef} onChange={(e) => setItem({ ...item, surgeonRef: e.target.value })} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm" />
              <button type="button" disabled={!item.patientId} onClick={() => void addItem()} className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">
                <Plus className="h-3.5 w-3.5" /> Add case
              </button>
            </div>

            {!session?.items || session.items.length === 0 ? (
              <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">No cases on this list yet.</div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
                <table className="w-full min-w-[640px] text-left text-sm">
                  <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3">Patient</th>
                      <th className="px-4 py-3">Procedure</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Episode</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {session.items.map((it) => (
                      <tr key={it.id}>
                        <td className="px-4 py-3 font-medium">{it.patientId ?? "—"}</td>
                        <td className="px-4 py-3 text-muted-foreground">{it.zibo ?? "—"}</td>
                        <td className="px-4 py-3 text-muted-foreground">{it.status ?? "PLANNED"}</td>
                        <td className="px-4 py-3 text-muted-foreground">{it.procedureEpisodeRef ? it.procedureEpisodeRef.slice(0, 8) : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/scheduling/theatre-lists" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
