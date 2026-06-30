"use client";

/**
 * Civil registration of death (CRVS) — Ubomi owns registration. Lists real death notifications
 * (GET /internal/v1/death/crvs/notifications) and lets a clerk stage the package
 * (POST .../package-ready) once required fields are complete. Registration itself is completed by the
 * civil registry with a real certificate number — never synthesised here. Route: /work/crvs/death-registration
 */

import { useCallback, useEffect, useState } from "react";
import { ClipboardList, Loader2, RefreshCw, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface Notification {
  id?: number;
  notificationNumber?: string;
  deceasedCpid?: string;
  status?: string;
  packageReady?: boolean;
  civilRegNumber?: string;
}
function asArray(p: unknown): Notification[] {
  if (Array.isArray(p)) return p as Notification[];
  if (p && typeof p === "object" && Array.isArray((p as { content?: unknown[] }).content)) {
    return (p as { content: Notification[] }).content;
  }
  return [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not complete the request. Please try again.";
}

export default function DeathRegistrationPage() {
  const [items, setItems] = useState<Notification[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(asArray(await apiClient.get<unknown>("/internal/v1/death/crvs/notifications")));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const stage = async (id: number) => {
    setBusy(id); setMsg(null); setError(null);
    try {
      await apiClient.post(`/internal/v1/death/crvs/notifications/${id}/package-ready`, {});
      setMsg("Registration package marked ready for the Registrar General.");
      void load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppLayout>
      <PageShell title="Death registration" subtitle="Prepare the civil-registration package for the Registrar General" icon={<ClipboardList className="h-5 w-5" />}>
        <div className="mb-4">
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background"><RefreshCw className="h-3.5 w-3.5" /> Refresh</button>
        </div>

        {msg && <div className="mb-4 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800"><CheckCircle2 className="h-4 w-4" /><span>{msg}</span></div>}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading…</div>
        ) : error && !items ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"><RefreshCw className="h-3.5 w-3.5" /> Retry</button>
          </div>
        ) : !items || items.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ClipboardList className="mx-auto mb-2 h-6 w-6 opacity-50" /> No death notifications awaiting registration.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Notification</th>
                  <th className="px-4 py-3">Person</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Certificate no.</th>
                  <th className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((n) => (
                  <tr key={n.id} className="hover:bg-background/80">
                    <td className="px-4 py-3 font-mono text-xs">{n.notificationNumber ?? n.id}</td>
                    <td className="px-4 py-3 text-muted-foreground">{n.deceasedCpid ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{n.status ?? "—"}{n.packageReady ? " · package ready" : ""}</td>
                    <td className="px-4 py-3 text-muted-foreground">{n.civilRegNumber ?? "—"}</td>
                    <td className="px-4 py-3">
                      {n.status === "REGISTERED" ? (
                        <span className="text-emerald-700">Registered</span>
                      ) : n.packageReady ? (
                        <span className="text-muted-foreground">Awaiting registry</span>
                      ) : (
                        <button type="button" disabled={busy === n.id} onClick={() => n.id != null && void stage(n.id)} className="rounded-lg border border-border bg-card px-3 py-1 text-xs font-medium hover:bg-background disabled:opacity-50">
                          {busy === n.id ? "Working…" : "Mark package ready"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/crvs/death-registration" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
