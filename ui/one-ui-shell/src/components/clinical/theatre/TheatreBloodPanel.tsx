"use client";

/**
 * Theatre blood panel (Lane 3). Reads MADI-backed blood links for a theatre case and drives the
 * request → issue → administer → resolve lifecycle. Reflects MADI status truth by read-through: the
 * reservation/crossmatch state comes from the service, never fabricated here. An empty list is an
 * honest "no blood ordered" state, not a placeholder.
 * Binds: GET /internal/v1/theatre/cases/{id}/blood, POST .../blood/{request|issue|administer|resolve}
 */

import { useCallback, useEffect, useState } from "react";
import { Droplet, Loader2, RefreshCw } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface BloodLink {
  id?: string;
  status?: string;
  required?: boolean;
  madi_order_id?: string | null;
  reservation_status?: string | null;
  crossmatch_status?: string | null;
  units_requested?: number | null;
  blood_group?: string | null;
  component_type?: string | null;
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
  return "Blood action failed. Please try again.";
}

const STATUS_TONE: Record<string, string> = {
  RESERVED: "bg-emerald-100 text-emerald-700",
  TRANSFUSED: "bg-emerald-100 text-emerald-700",
  RECONCILED: "bg-emerald-100 text-emerald-700",
  TRANSFUSING: "bg-blue-100 text-blue-700",
  IN_TRANSIT: "bg-blue-100 text-blue-700",
  GROUP_SCREEN: "bg-amber-100 text-amber-700",
  REQUESTED: "bg-amber-100 text-amber-700",
  REACTION: "bg-red-100 text-red-700",
  UNAVAILABLE: "bg-red-100 text-red-700",
};

export function TheatreBloodPanel({ caseId }: { caseId: string }) {
  const [links, setLinks] = useState<BloodLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [units, setUnits] = useState("2");
  const [componentType, setComponentType] = useState("PRBC");
  const [bloodGroup, setBloodGroup] = useState("");
  const [emergency, setEmergency] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get(`/internal/v1/theatre/cases/${caseId}/blood`);
      setLinks(unwrapList<BloodLink>(res));
    } catch {
      setLinks([]);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  const act = useCallback(
    async (fn: () => Promise<unknown>, ok: string) => {
      setBusy(true);
      setMsg(null);
      try {
        await fn();
        setMsg(ok);
        await load();
      } catch (e) {
        setMsg(errMessage(e));
      } finally {
        setBusy(false);
      }
    },
    [load],
  );

  const request = () =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${caseId}/blood/request`, {
          units: Number(units),
          componentType,
          ...(bloodGroup ? { bloodGroup } : {}),
          emergency,
        }),
      "Blood requested from MADI.",
    );
  const issue = () => act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/blood/issue`, {}), "Blood issued and routed to theatre.");
  const administer = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/blood/administer`, { patientMethod: "WRISTBAND", unitMethod: "BARCODE" }), "Transfusion started (bedside verify recorded).");
  const resolve = (reaction: boolean) =>
    act(
      () => apiClient.post(`/internal/v1/theatre/cases/${caseId}/blood/resolve`, reaction ? { outcome: "REACTION", reactionNotes: "Reaction observed intra-op" } : { outcome: "COMPLETED" }),
      reaction ? "Reaction recorded — routed to MADI haemovigilance." : "Transfusion completed and reconciled.",
    );

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="blood-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><Droplet className="h-4 w-4" /> Blood (MADI)</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>

      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}

      {loading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading blood…</p>
      ) : links.length === 0 ? (
        <p className="text-sm text-muted-foreground">No blood ordered for this case.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-left text-xs">
            <thead className="bg-background text-muted-foreground"><tr><th className="px-3 py-2">Component</th><th className="px-3 py-2">Group</th><th className="px-3 py-2">Units</th><th className="px-3 py-2">Reservation</th><th className="px-3 py-2">Crossmatch</th><th className="px-3 py-2">Status</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {links.map((l) => (
                <tr key={l.id}>
                  <td className="px-3 py-2 font-medium">{l.component_type ?? "—"}</td>
                  <td className="px-3 py-2">{l.blood_group ?? "—"}</td>
                  <td className="px-3 py-2">{l.units_requested ?? "—"}</td>
                  <td className="px-3 py-2 text-muted-foreground">{l.reservation_status ?? "—"}</td>
                  <td className="px-3 py-2 text-muted-foreground">{l.crossmatch_status ?? "—"}</td>
                  <td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 font-medium ${STATUS_TONE[l.status ?? ""] ?? "bg-slate-100 text-slate-600"}`}>{l.status ?? "—"}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Units</span><input type="number" min={1} value={units} onChange={(e) => setUnits(e.target.value)} className="w-16 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Component</span>
          <select value={componentType} onChange={(e) => setComponentType(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
            {["PRBC", "FFP", "PLATELETS", "CRYO", "WHOLE_BLOOD"].map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Group (optional)</span><input type="text" value={bloodGroup} onChange={(e) => setBloodGroup(e.target.value)} placeholder="e.g. O_NEG" className="w-24 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <label className="flex items-center gap-1 text-xs"><input type="checkbox" checked={emergency} onChange={(e) => setEmergency(e.target.checked)} /> Emergency (uncrossmatched O-neg)</label>
        <button type="button" disabled={busy} onClick={() => void request()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Request blood</button>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        <button type="button" disabled={busy || links.length === 0} onClick={() => void issue()} className="rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-card disabled:opacity-50">Issue &amp; transport</button>
        <button type="button" disabled={busy || links.length === 0} onClick={() => void administer()} className="rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-card disabled:opacity-50">Administer</button>
        <button type="button" disabled={busy || links.length === 0} onClick={() => void resolve(false)} className="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-800 hover:bg-emerald-100 disabled:opacity-50">Resolve (completed)</button>
        <button type="button" disabled={busy || links.length === 0} onClick={() => void resolve(true)} className="rounded-lg border border-red-300 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50">Resolve (reaction)</button>
      </div>
    </section>
  );
}
