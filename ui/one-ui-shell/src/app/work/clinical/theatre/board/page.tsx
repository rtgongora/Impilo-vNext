"use client";

/**
 * Theatre-day readiness board. Real board rows via the experience-BFF
 * (GET /internal/v1/theatre/readiness-board) composing the inpatient queue with
 * per-case readiness (TUSO space, VASHANDI team, MVUMO consent bundle + theatre-day
 * domains). Resolve-blocker routes to the owning service. No fake readiness.
 * Route: /work/clinical/theatre/board
 */

import { useCallback, useEffect, useState } from "react";
import { ClipboardCheck, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface BoardDomain { domain?: string; status?: string; owner_service?: string }
interface BoardRow {
  case?: { episode_id?: string; id?: string; patient_id?: string; procedure_name?: string };
  board?: { board_state?: string; domains?: BoardDomain[]; blockers?: { code?: string; message?: string }[] };
}

function rowsOf(p: unknown): BoardRow[] {
  const data = (p && typeof p === "object" && "data" in p) ? (p as { data?: unknown }).data : p;
  if (data && typeof data === "object" && "rows" in data) {
    const r = (data as { rows?: unknown }).rows;
    return Array.isArray(r) ? (r as BoardRow[]) : [];
  }
  return Array.isArray(data) ? (data as BoardRow[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the readiness board. Please try again.";
}

const STATE_TONE: Record<string, string> = {
  Ready: "bg-emerald-100 text-emerald-700",
  "Ready-with-warnings": "bg-amber-100 text-amber-700",
  Blocked: "bg-red-100 text-red-700",
  Deferred: "bg-slate-100 text-slate-600",
  "In-progress": "bg-blue-100 text-blue-700",
  Completed: "bg-slate-100 text-slate-600",
  Cancelled: "bg-slate-100 text-slate-500",
};

export default function TheatreReadinessBoardPage() {
  const [rows, setRows] = useState<BoardRow[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/theatre/readiness-board");
      setRows(rowsOf(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const resolve = useCallback(async (caseId: string, domain: string) => {
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/theatre/cases/${caseId}/resolve-blocker`, { domain, action: "ROUTE_TO_OWNER" });
      setMsg(`Routed ${domain} blocker to its owning service.`);
      void load();
    } catch (e) {
      setMsg(errMessage(e));
    }
  }, [load]);

  useEffect(() => { void load(); }, [load]);

  return (
    <AppLayout>
      <PageShell title="Theatre-day readiness board" subtitle="Per-case readiness across all domains — blockers route to their owning service" icon={<ClipboardCheck className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>
        {msg && <p className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{msg}</p>}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading the readiness board…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !rows || rows.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ClipboardCheck className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No theatre cases on the board for this facility today.
          </div>
        ) : (
          <div className="space-y-3">
            {rows.map((r) => {
              const caseId = r.case?.episode_id ?? r.case?.id ?? "";
              const state = r.board?.board_state ?? "Unknown";
              const blockers = r.board?.blockers ?? [];
              return (
                <div key={caseId} className="rounded-xl border border-border bg-card p-4 shadow-sm">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">{r.case?.patient_id ?? "—"}</p>
                      <p className="text-sm text-muted-foreground">{r.case?.procedure_name ?? "Procedure"}</p>
                    </div>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATE_TONE[state] ?? "bg-slate-100 text-slate-600"}`}>
                      {state}
                    </span>
                  </div>
                  {blockers.length > 0 && (
                    <ul className="mt-3 space-y-1.5">
                      {blockers.map((b, i) => (
                        <li key={`${b.code}-${i}`} className="flex items-center justify-between rounded-lg bg-red-50 px-3 py-1.5 text-sm text-red-700">
                          <span>{b.message ?? b.code}</span>
                          <button type="button" onClick={() => void resolve(caseId, (b.code ?? "").replace(/_NOT_READY$/, ""))} className="rounded-md border border-red-300 bg-white px-2 py-0.5 text-xs font-medium hover:bg-red-100">
                            Resolve
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/theatre/board" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
