"use client";

/**
 * Theatre surgical-count panel (Lane 3). Discrete swab/instrument/needle counts. A non-zero baseline↔
 * closing discrepancy opens a RITO CRITICAL retained-item case AND blocks the WHO SIGN_OUT — this panel
 * makes that gate visible. Counts and their reconciliation come from the service; nothing is inferred.
 * Binds: GET /internal/v1/theatre/cases/{id}/counts, POST .../counts
 */

import { useCallback, useEffect, useState } from "react";
import { ListChecks, Loader2, RefreshCw, ShieldAlert, ShieldCheck, AlertTriangle } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface CountRow {
  id?: string;
  kind?: string;
  seq?: number;
  baseline_count?: number | null;
  closing_count?: number | null;
  discrepancy?: number | null;
  reconciled?: boolean;
  rito_case_ref?: string | null;
}

function unwrapList<T>(res: unknown): T[] {
  const o = res as { data?: T[] };
  const v = o && o.data !== undefined ? o.data : res;
  return Array.isArray(v) ? (v as T[]) : [];
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Count action failed. Please try again.";
}

export function TheatreCountPanel({ caseId }: { caseId: string }) {
  const [counts, setCounts] = useState<CountRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [listUnavailable, setListUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [kind, setKind] = useState("SWAB");
  const [baseline, setBaseline] = useState("");
  const [closing, setClosing] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get(`/internal/v1/theatre/cases/${caseId}/counts`);
      setCounts(unwrapList<CountRow>(res));
      setListUnavailable(false);
    } catch {
      // Do not collapse unknown → empty: keep last known rows and surface unavailable.
      setListUnavailable(true);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  const record = useCallback(async () => {
    setBusy(true);
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/theatre/cases/${caseId}/counts`, {
        kind,
        ...(baseline !== "" ? { baselineCount: Number(baseline) } : {}),
        ...(closing !== "" ? { closingCount: Number(closing) } : {}),
      });
      setMsg("Count recorded.");
      setBaseline("");
      setClosing("");
      await load();
    } catch (e) {
      setMsg(errMessage(e));
    } finally {
      setBusy(false);
    }
  }, [caseId, kind, baseline, closing, load]);

  // Gate visibility: any count with both sides present and a non-zero discrepancy blocks Sign-Out.
  const discrepant = counts.filter((c) => (c.discrepancy ?? 0) !== 0);
  const anyRecorded = counts.length > 0;
  const signOutClear = anyRecorded && discrepant.length === 0;

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="count-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><ListChecks className="h-4 w-4" /> Surgical counts</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>

      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}
      {listUnavailable && (
        <p className="mb-2 flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800" data-testid="count-list-unavailable">
          <AlertTriangle className="h-3.5 w-3.5" /> Count list unavailable — do not treat this as no counts recorded.
        </p>
      )}

      {/* WHO Sign-Out gate — always honest about the current state (only when list is known) */}
      {!listUnavailable && discrepant.length > 0 ? (
        <p data-testid="signout-gate-blocked" className="mb-2 flex items-center gap-1.5 rounded-lg border border-red-200 bg-red-50 p-2 text-xs font-medium text-red-700">
          <ShieldAlert className="h-4 w-4" /> WHO Sign-Out BLOCKED — retained-item risk: {discrepant.map((c) => `${c.kind} off by ${c.discrepancy}`).join(", ")}. A RITO case is open until reconciled or overridden.
        </p>
      ) : !listUnavailable && signOutClear ? (
        <p data-testid="signout-gate-clear" className="mb-2 flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-emerald-50 p-2 text-xs font-medium text-emerald-700">
          <ShieldCheck className="h-4 w-4" /> Counts reconciled — WHO Sign-Out not blocked by counts.
        </p>
      ) : !listUnavailable ? (
        <p className="mb-2 text-xs text-muted-foreground">Record baseline and closing counts to clear the Sign-Out gate.</p>
      ) : null}

      {loading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading counts…</p>
      ) : counts.length === 0 && !listUnavailable ? (
        <p className="text-sm text-muted-foreground">No counts recorded yet.</p>
      ) : counts.length === 0 && listUnavailable ? (
        <p className="text-sm text-muted-foreground">Could not load counts for this case.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-left text-xs">
            <thead className="bg-background text-muted-foreground"><tr><th className="px-3 py-2">Kind</th><th className="px-3 py-2">Baseline</th><th className="px-3 py-2">Closing</th><th className="px-3 py-2">Discrepancy</th><th className="px-3 py-2">Reconciled</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {counts.map((c) => (
                <tr key={c.id} className={(c.discrepancy ?? 0) !== 0 ? "bg-red-50/50" : undefined}>
                  <td className="px-3 py-2 font-medium">{c.kind} #{c.seq}</td>
                  <td className="px-3 py-2">{c.baseline_count ?? "—"}</td>
                  <td className="px-3 py-2">{c.closing_count ?? "—"}</td>
                  <td className={`px-3 py-2 font-medium ${(c.discrepancy ?? 0) !== 0 ? "text-red-700" : ""}`}>{c.discrepancy ?? "—"}</td>
                  <td className="px-3 py-2">{c.reconciled ? "Yes" : "No"}{c.rito_case_ref ? ` · RITO ${c.rito_case_ref.slice(0, 8)}` : ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Kind</span>
          <select value={kind} onChange={(e) => setKind(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
            {["SWAB", "INSTRUMENT", "NEEDLE"].map((k) => <option key={k}>{k}</option>)}
          </select>
        </label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Baseline</span><input type="number" min={0} value={baseline} onChange={(e) => setBaseline(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Closing</span><input type="number" min={0} value={closing} onChange={(e) => setClosing(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <button type="button" disabled={busy || (baseline === "" && closing === "")} onClick={() => void record()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record count</button>
      </div>
    </section>
  );
}
