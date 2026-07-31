"use client";

/**
 * Theatre specimen panel (Lane 3). Lists OROS-backed specimens for a theatre case, surfaces pending
 * pathology, lets the team acknowledge a critical result and dispatch a specimen leg via NHUME.
 * Also records the P8 custody chain (collect → confirm-label → receive → adequacy).
 * Read from the service — a critical flag, pathology reference and custody stamps are the service's
 * truth, never invented. List GET failure shows unavailable; never collapses to an empty list.
 * Binds: GET /internal/v1/theatre/cases/{id}/specimens,
 *        POST .../specimens/{specimenId}/acknowledge-critical,
 *        POST .../specimens/{specimenId}/{collect|confirm-label|receive|adequacy},
 *        POST .../transport/specimen
 */

import { useCallback, useEffect, useState } from "react";
import { FlaskConical, Loader2, RefreshCw, AlertTriangle } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface Specimen {
  id?: string;
  seq?: number;
  specimen_label?: string;
  specimen_type?: string;
  body_site?: string;
  status?: string;
  is_critical?: boolean;
  oros_specimen_id?: string | null;
  oros_result_id?: string | null;
  acknowledged_at?: string | null;
  butano_document_ref?: string | null;
  collected_by?: string | null;
  collected_at?: string | null;
  container_type?: string | null;
  fixative?: string | null;
  label_confirmed_by?: string | null;
  label_confirmed_at?: string | null;
  received_by?: string | null;
  received_at?: string | null;
  adequacy?: string | null;
  adequacy_assessed_by?: string | null;
  adequacy_assessed_at?: string | null;
  fhir_specimen_ref?: string | null;
}

function unwrapList<T>(res: unknown): T[] {
  const o = res as { data?: T[] };
  const v = o && o.data !== undefined ? o.data : res;
  return Array.isArray(v) ? (v as T[]) : [];
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string } | string; message?: string; status?: number };
  if (typeof o?.error === "object" && o.error?.message) return o.error.message;
  if (typeof o?.error === "string" && o.message) return o.message;
  if (o?.message) return o.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Specimen action failed. Please try again.";
}

function adequacyUnset(s: Specimen): boolean {
  return !s.adequacy || s.adequacy === "NOT_ASSESSED";
}

export function TheatreSpecimenPanel({ caseId }: { caseId: string }) {
  const [specimens, setSpecimens] = useState<Specimen[]>([]);
  const [loading, setLoading] = useState(true);
  const [listUnavailable, setListUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [specimenRef, setSpecimenRef] = useState("");
  const [collectDraft, setCollectDraft] = useState<Record<string, { containerType: string; fixative: string }>>({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get(`/internal/v1/theatre/cases/${caseId}/specimens`);
      setSpecimens(unwrapList<Specimen>(res));
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

  const acknowledge = (specimenId?: string) => {
    if (!specimenId) return;
    return act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/specimens/${specimenId}/acknowledge-critical`, { summary: "Critical pathology acknowledged intra-op" }), "Critical result acknowledged and attached to the record.");
  };
  const dispatch = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/transport/specimen`, specimenRef ? { specimenRef } : {}), "Specimen leg dispatched to the lab (NHUME).");

  const collect = (specimenId?: string) => {
    if (!specimenId) return;
    const draft = collectDraft[specimenId] ?? { containerType: "", fixative: "" };
    return act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${caseId}/specimens/${specimenId}/collect`, {
          containerType: draft.containerType || undefined,
          fixative: draft.fixative || undefined,
        }),
      "Collection recorded.",
    );
  };
  const confirmLabel = (specimenId?: string) => {
    if (!specimenId) return;
    return act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/specimens/${specimenId}/confirm-label`, {}), "Label confirmed.");
  };
  const receive = (specimenId?: string) => {
    if (!specimenId) return;
    return act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/specimens/${specimenId}/receive`, {}), "Receipt recorded.");
  };
  const assessAdequacy = (specimenId: string | undefined, adequacy: "ADEQUATE" | "INADEQUATE") => {
    if (!specimenId) return;
    return act(
      () => apiClient.post(`/internal/v1/theatre/cases/${caseId}/specimens/${specimenId}/adequacy`, { adequacy }),
      `Adequacy assessed: ${adequacy}.`,
    );
  };

  const pendingPath = specimens.filter((s) => !s.oros_result_id && s.status !== "ACKNOWLEDGED");

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="specimen-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><FlaskConical className="h-4 w-4" /> Specimens (OROS / pathology)</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>

      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}
      {listUnavailable && (
        <p className="mb-2 flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800" data-testid="specimen-list-unavailable">
          <AlertTriangle className="h-3.5 w-3.5" /> Specimen list unavailable — do not treat this as no specimens.
        </p>
      )}
      {pendingPath.length > 0 && (
        <p className="mb-2 flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800"><AlertTriangle className="h-3.5 w-3.5" /> {pendingPath.length} specimen(s) awaiting pathology.</p>
      )}

      {loading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading specimens…</p>
      ) : specimens.length === 0 && !listUnavailable ? (
        <p className="text-sm text-muted-foreground">No specimens for this case. Specimens are opened from the operative note, or dispatch one below.</p>
      ) : specimens.length === 0 && listUnavailable ? (
        <p className="text-sm text-muted-foreground">Could not load specimens for this case.</p>
      ) : (
        <ul className="space-y-2">
          {specimens.map((s) => {
            const canCollect = !s.collected_at;
            const canConfirmLabel = Boolean(s.collected_at) && !s.label_confirmed_at;
            const canReceive = Boolean(s.label_confirmed_at) && !s.received_at;
            const canAdequacy = Boolean(s.received_at) && adequacyUnset(s);
            const draft = collectDraft[s.id ?? ""] ?? { containerType: "", fixative: "" };
            return (
              <li key={s.id} className="rounded-lg border border-border p-2 text-xs" data-testid={`specimen-row-${s.id}`}>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{s.specimen_label ?? s.body_site ?? "Specimen"}</span>
                  {s.specimen_type && <span className="text-muted-foreground">{s.specimen_type}</span>}
                  {s.is_critical && <span className="rounded-full bg-red-100 px-2 py-0.5 font-medium text-red-700">CRITICAL</span>}
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-600">{s.status ?? "—"}</span>
                  {s.butano_document_ref && <span className="text-muted-foreground">record: {s.butano_document_ref.slice(0, 8)}</span>}
                  {s.collected_at && <span className="text-muted-foreground">collected</span>}
                  {s.label_confirmed_at && <span className="text-muted-foreground">label ok</span>}
                  {s.received_at && <span className="text-muted-foreground">received</span>}
                  {s.adequacy && s.adequacy !== "NOT_ASSESSED" && (
                    <span className={`rounded-full px-2 py-0.5 font-medium ${s.adequacy === "ADEQUATE" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{s.adequacy}</span>
                  )}
                  {s.is_critical && s.status !== "ACKNOWLEDGED" && (
                    <button type="button" disabled={busy} onClick={() => void acknowledge(s.id)} className="ml-auto rounded-lg border border-red-300 bg-red-50 px-2 py-1 font-medium text-red-700 hover:bg-red-100 disabled:opacity-50">Acknowledge critical</button>
                  )}
                </div>
                <div className="mt-2 flex flex-wrap items-end gap-2">
                  {canCollect && (
                    <>
                      <label className="block text-xs">
                        <span className="mb-1 block font-medium text-muted-foreground">Container</span>
                        <input
                          type="text"
                          value={draft.containerType}
                          onChange={(e) => setCollectDraft((prev) => ({ ...prev, [s.id!]: { ...draft, containerType: e.target.value } }))}
                          className="w-28 rounded-lg border border-border bg-background px-2 py-1"
                          data-testid={`collect-container-${s.id}`}
                        />
                      </label>
                      <label className="block text-xs">
                        <span className="mb-1 block font-medium text-muted-foreground">Fixative</span>
                        <input
                          type="text"
                          value={draft.fixative}
                          onChange={(e) => setCollectDraft((prev) => ({ ...prev, [s.id!]: { ...draft, fixative: e.target.value } }))}
                          className="w-28 rounded-lg border border-border bg-background px-2 py-1"
                          data-testid={`collect-fixative-${s.id}`}
                        />
                      </label>
                      <button type="button" disabled={busy} onClick={() => void collect(s.id)} className="rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50" data-testid={`collect-${s.id}`}>
                        Collect
                      </button>
                    </>
                  )}
                  {canConfirmLabel && (
                    <button type="button" disabled={busy} onClick={() => void confirmLabel(s.id)} className="rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50" data-testid={`confirm-label-${s.id}`}>
                      Confirm label
                    </button>
                  )}
                  {canReceive && (
                    <button type="button" disabled={busy} onClick={() => void receive(s.id)} className="rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50" data-testid={`receive-${s.id}`}>
                      Receive
                    </button>
                  )}
                  {canAdequacy && (
                    <>
                      <button type="button" disabled={busy} onClick={() => void assessAdequacy(s.id, "ADEQUATE")} className="rounded-lg border border-emerald-300 bg-emerald-50 px-2 py-1 font-medium text-emerald-800 hover:bg-emerald-100 disabled:opacity-50" data-testid={`adequacy-adequate-${s.id}`}>
                        Adequate
                      </button>
                      <button type="button" disabled={busy} onClick={() => void assessAdequacy(s.id, "INADEQUATE")} className="rounded-lg border border-amber-300 bg-amber-50 px-2 py-1 font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-50" data-testid={`adequacy-inadequate-${s.id}`}>
                        Inadequate
                      </button>
                    </>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Specimen ref (optional)</span><input type="text" value={specimenRef} onChange={(e) => setSpecimenRef(e.target.value)} className="w-48 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <button type="button" disabled={busy} onClick={() => void dispatch()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Dispatch specimen leg</button>
      </div>
    </section>
  );
}
