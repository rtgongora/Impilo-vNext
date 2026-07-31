"use client";

/**
 * Theatre commodities / traceability panel (Lane 2). Three real registers on one case:
 *  - Implants: UDI / serial / lot → patient, with inventory SoR remove / revise / recall.
 *  - Sterile instrument sets: TUSO CSSD issue / return.
 *  - Controlled drugs: two-person-witness register (inventory register-backed).
 * All rows are read from the service; statuses (IMPLANTED/UNAVAILABLE, running balance, etc.) are the
 * service's truth. No mocks; empty registers show an honest empty state.
 * Implant lifecycle writes go to inventory SoR (/internal/v1/inventory/implants/...), never the
 * theatre recall projection that swallows failures into an empty list.
 * Binds: /internal/v1/theatre/cases/{id}/{implants|instrument-sets|controlled-drugs},
 *        POST /internal/v1/inventory/implants/{id}/{remove|revise},
 *        GET /internal/v1/inventory/implants/recall
 */

import { useCallback, useEffect, useState } from "react";
import { Boxes, Loader2, RefreshCw, ScanLine, Pill, AlertTriangle } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface Implant {
  id?: string;
  udi?: string | null;
  serial_number?: string | null;
  lot_number?: string | null;
  device_type?: string | null;
  body_site?: string | null;
  laterality?: string | null;
  status?: string;
  inventory_patient_implant_id?: string | null;
}
interface InstrumentSet { id?: string; tuso_instrument_set_id?: string; set_name?: string | null; issue_status?: string; sterile_until?: string | null; contaminated?: boolean; incomplete?: boolean; }
interface ControlledDrug { id?: string; item_code?: string; drug_name?: string | null; action?: string; quantity?: number | null; unit?: string | null; running_balance?: number | null; witness_provider?: string | null; status?: string; }
interface RecallHit { patientCpid?: string; patient_cpid?: string; udi?: string; lotNumber?: string; lot_number?: string; status?: string; }

function unwrapList<T>(res: unknown): T[] {
  const o = res as { data?: T[] };
  const v = o && o.data !== undefined ? o.data : res;
  return Array.isArray(v) ? (v as T[]) : [];
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string } | string; message?: string; status?: number };
  if (typeof o?.error === "object" && o.error?.message) return o.error.message;
  if (typeof o?.message === "string" && o.message) return o.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Action failed. Please try again.";
}

export function TheatreCommoditiesPanel({ caseId, patientCpid }: { caseId: string; patientCpid?: string }) {
  const [implants, setImplants] = useState<Implant[]>([]);
  const [sets, setSets] = useState<InstrumentSet[]>([]);
  const [drugs, setDrugs] = useState<ControlledDrug[]>([]);
  const [loading, setLoading] = useState(true);
  const [implantsUnavailable, setImplantsUnavailable] = useState(false);
  const [setsUnavailable, setSetsUnavailable] = useState(false);
  const [drugsUnavailable, setDrugsUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  // implant form
  const [udi, setUdi] = useState("");
  const [serial, setSerial] = useState("");
  const [lot, setLot] = useState("");
  const [bodySite, setBodySite] = useState("");
  // per-row lifecycle drafts
  const [removalReason, setRemovalReason] = useState<Record<string, string>>({});
  const [reviseDraft, setReviseDraft] = useState<Record<string, { udi: string; serial: string; lot: string }>>({});
  // recall
  const [recallUdi, setRecallUdi] = useState("");
  const [recallLot, setRecallLot] = useState("");
  const [recallHits, setRecallHits] = useState<RecallHit[] | null>(null);
  const [recallUnavailable, setRecallUnavailable] = useState(false);
  // instrument set form
  const [setId, setSetId] = useState("");
  // controlled drug form
  const [cdAction, setCdAction] = useState("ADMINISTER");
  const [cdItem, setCdItem] = useState("");
  const [cdWitness, setCdWitness] = useState("");
  const [cdQty, setCdQty] = useState("1");

  const load = useCallback(async () => {
    setLoading(true);
    const results = await Promise.allSettled([
      apiClient.get(`/internal/v1/theatre/cases/${caseId}/implants`),
      apiClient.get(`/internal/v1/theatre/cases/${caseId}/instrument-sets`),
      apiClient.get(`/internal/v1/theatre/cases/${caseId}/controlled-drugs`),
    ]);
    // Preserve last good rows for failed GETs — never invent empty registers on transport failure.
    if (results[0].status === "fulfilled") {
      setImplants(unwrapList<Implant>(results[0].value));
      setImplantsUnavailable(false);
    } else {
      setImplantsUnavailable(true);
    }
    if (results[1].status === "fulfilled") {
      setSets(unwrapList<InstrumentSet>(results[1].value));
      setSetsUnavailable(false);
    } else {
      setSetsUnavailable(true);
    }
    if (results[2].status === "fulfilled") {
      setDrugs(unwrapList<ControlledDrug>(results[2].value));
      setDrugsUnavailable(false);
    } else {
      setDrugsUnavailable(true);
    }
    setLoading(false);
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

  const recordImplant = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/implants`, { ...(udi ? { udi } : {}), ...(serial ? { serialNumber: serial } : {}), ...(lot ? { lotNumber: lot } : {}), ...(bodySite ? { bodySite } : {}) }), "Implant recorded (UDI/serial → patient).").then(() => { setUdi(""); setSerial(""); setLot(""); setBodySite(""); });
  const issueSet = () => act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/instrument-sets/issue`, { instrumentSetId: setId }), "Instrument set issued (TUSO CSSD).").then(() => setSetId(""));
  const returnSet = (id: string) => act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/instrument-sets/return`, { instrumentSetId: id }), "Instrument set returned to CSSD.");
  const recordDrug = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/controlled-drugs`, { action: cdAction, itemCode: cdItem, quantity: Number(cdQty), ...(cdWitness ? { witnessProvider: cdWitness } : {}) }), "Controlled-drug register entry recorded.").then(() => { setCdItem(""); setCdWitness(""); });

  const removeImplant = (inventoryId: string) => {
    const reason = (removalReason[inventoryId] ?? "").trim();
    return act(
      () => apiClient.post(`/internal/v1/inventory/implants/${inventoryId}/remove`, { removalReason: reason || undefined }),
      "Implant removal recorded against the inventory register.",
    );
  };

  const reviseImplant = (inventoryId: string) => {
    if (!patientCpid) {
      setMsg("Patient CPID is required to revise an implant.");
      return;
    }
    const draft = reviseDraft[inventoryId] ?? { udi: "", serial: "", lot: "" };
    if (!draft.udi && !draft.serial) {
      setMsg("Revision requires a UDI or serial for the replacement implant.");
      return;
    }
    return act(
      () =>
        apiClient.post(`/internal/v1/inventory/implants/${inventoryId}/revise`, {
          newImplant: {
            patientCpid,
            ...(draft.udi ? { udi: draft.udi } : {}),
            ...(draft.serial ? { serialNumber: draft.serial } : {}),
            ...(draft.lot ? { lotNumber: draft.lot } : {}),
            episodeRef: caseId,
          },
          revisionReason: "Theatre revision",
        }),
      "Implant revision recorded against the inventory register.",
    );
  };

  const runRecall = async () => {
    setBusy(true);
    setMsg(null);
    setRecallUnavailable(false);
    setRecallHits(null);
    try {
      const params = new URLSearchParams();
      if (recallUdi) params.set("udi", recallUdi);
      if (recallLot) params.set("lot", recallLot);
      const qs = params.toString();
      // Inventory SoR — never /theatre/implants/recall (that swallows failures to []).
      const res = await apiClient.get(`/internal/v1/inventory/implants/recall${qs ? `?${qs}` : ""}`);
      setRecallHits(unwrapList<RecallHit>(res));
      setMsg("Recall trace complete (inventory register).");
    } catch (e) {
      const status = (e as { status?: number }).status;
      setRecallUnavailable(true);
      setRecallHits(null);
      if (status === 502 || status === 503) {
        setMsg("Recall trace unavailable — do not treat as zero affected patients.");
      } else {
        setMsg(errMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  const witnessRequired = ["ADMINISTER", "WASTE", "DISCARD"].includes(cdAction);

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="commodities-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><Boxes className="h-4 w-4" /> Commodities &amp; traceability</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>
      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs" data-testid="commodities-msg">{msg}</p>}
      {(implantsUnavailable || setsUnavailable || drugsUnavailable) && (
        <p className="mb-2 flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800" data-testid="commodities-list-unavailable">
          <AlertTriangle className="h-3.5 w-3.5" /> Commodity registers unavailable — do not treat this as empty registers.
        </p>
      )}
      {loading && <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading registers…</p>}

      {/* Implants */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><ScanLine className="h-3.5 w-3.5" /> Implants (UDI / serial / lot)</p>
        {implants.length === 0 && !implantsUnavailable ? (
          <p className="text-xs text-muted-foreground">No implants recorded.</p>
        ) : implants.length === 0 && implantsUnavailable ? (
          <p className="text-xs text-muted-foreground">Could not load implants for this case.</p>
        ) : (
          <ul className="space-y-2">
            {implants.map((im) => {
              const invId = im.inventory_patient_implant_id ?? null;
              const linked = Boolean(invId);
              const rev = reviseDraft[invId ?? ""] ?? { udi: "", serial: "", lot: "" };
              return (
                <li key={im.id} className="rounded-lg border border-border/60 p-2 text-xs" data-testid={`implant-row-${im.id}`}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{im.udi ?? im.serial_number}</span>
                    {im.device_type && <span className="text-muted-foreground">{im.device_type}</span>}
                    {im.body_site && <span className="text-muted-foreground">{im.body_site}{im.laterality ? ` (${im.laterality})` : ""}</span>}
                    {im.lot_number && <span className="text-muted-foreground">lot {im.lot_number}</span>}
                    <span className={`rounded-full px-2 py-0.5 font-medium ${im.status === "IMPLANTED" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{im.status}</span>
                    {invId ? (
                      <span className="text-muted-foreground" data-testid={`inventory-link-${im.id}`}>inv {invId.slice(0, 8)}</span>
                    ) : (
                      <span className="text-muted-foreground">no inventory link</span>
                    )}
                  </div>
                  <div className="mt-2 flex flex-wrap items-end gap-2">
                    <label className="block text-xs">
                      <span className="mb-1 block font-medium text-muted-foreground">Removal reason</span>
                      <input
                        type="text"
                        disabled={!linked || busy}
                        value={removalReason[invId ?? ""] ?? ""}
                        onChange={(e) => invId && setRemovalReason((prev) => ({ ...prev, [invId]: e.target.value }))}
                        className="w-40 rounded-lg border border-border bg-background px-2 py-1 disabled:opacity-50"
                        data-testid={`removal-reason-${im.id}`}
                      />
                    </label>
                    <button
                      type="button"
                      disabled={!linked || busy}
                      onClick={() => invId && void removeImplant(invId)}
                      className="rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50"
                      data-testid={`remove-implant-${im.id}`}
                    >
                      Remove
                    </button>
                    <label className="block text-xs">
                      <span className="mb-1 block font-medium text-muted-foreground">Revise UDI</span>
                      <input
                        type="text"
                        disabled={!linked || busy}
                        value={rev.udi}
                        onChange={(e) => invId && setReviseDraft((prev) => ({ ...prev, [invId]: { ...rev, udi: e.target.value } }))}
                        className="w-32 rounded-lg border border-border bg-background px-2 py-1 disabled:opacity-50"
                        data-testid={`revise-udi-${im.id}`}
                      />
                    </label>
                    <label className="block text-xs">
                      <span className="mb-1 block font-medium text-muted-foreground">Serial</span>
                      <input
                        type="text"
                        disabled={!linked || busy}
                        value={rev.serial}
                        onChange={(e) => invId && setReviseDraft((prev) => ({ ...prev, [invId]: { ...rev, serial: e.target.value } }))}
                        className="w-28 rounded-lg border border-border bg-background px-2 py-1 disabled:opacity-50"
                        data-testid={`revise-serial-${im.id}`}
                      />
                    </label>
                    <label className="block text-xs">
                      <span className="mb-1 block font-medium text-muted-foreground">Lot</span>
                      <input
                        type="text"
                        disabled={!linked || busy}
                        value={rev.lot}
                        onChange={(e) => invId && setReviseDraft((prev) => ({ ...prev, [invId]: { ...rev, lot: e.target.value } }))}
                        className="w-24 rounded-lg border border-border bg-background px-2 py-1 disabled:opacity-50"
                        data-testid={`revise-lot-${im.id}`}
                      />
                    </label>
                    <button
                      type="button"
                      disabled={!linked || busy || !patientCpid}
                      onClick={() => invId && void reviseImplant(invId)}
                      className="rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50"
                      data-testid={`revise-implant-${im.id}`}
                    >
                      Revise
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">UDI</span><input type="text" value={udi} onChange={(e) => setUdi(e.target.value)} className="w-40 rounded-lg border border-border bg-background px-2 py-1" data-testid="implant-udi" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Serial</span><input type="text" value={serial} onChange={(e) => setSerial(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Lot</span><input type="text" value={lot} onChange={(e) => setLot(e.target.value)} className="w-24 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Body site</span><input type="text" value={bodySite} onChange={(e) => setBodySite(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <button type="button" disabled={busy || (!udi && !serial)} onClick={() => void recordImplant()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record implant</button>
        </div>
        <div className="mt-3 border-t border-border pt-2">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Recall trace (inventory SoR)</p>
          <div className="flex flex-wrap items-end gap-2">
            <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">UDI</span><input type="text" value={recallUdi} onChange={(e) => setRecallUdi(e.target.value)} className="w-40 rounded-lg border border-border bg-background px-2 py-1" data-testid="recall-udi" /></label>
            <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Lot</span><input type="text" value={recallLot} onChange={(e) => setRecallLot(e.target.value)} className="w-24 rounded-lg border border-border bg-background px-2 py-1" data-testid="recall-lot" /></label>
            <button type="button" disabled={busy || (!recallUdi && !recallLot)} onClick={() => void runRecall()} className="rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-card disabled:opacity-50" data-testid="recall-run">Run recall</button>
          </div>
          {recallUnavailable && (
            <p className="mt-2 flex items-center gap-1 text-xs text-amber-800" data-testid="recall-unavailable">
              <AlertTriangle className="h-3.5 w-3.5" /> Recall unavailable — do not treat as zero affected patients.
            </p>
          )}
          {recallHits && !recallUnavailable && (
            recallHits.length === 0 ? (
              <p className="mt-2 text-xs text-muted-foreground" data-testid="recall-empty">No affected patients for this UDI/lot.</p>
            ) : (
              <ul className="mt-2 space-y-1" data-testid="recall-hits">
                {recallHits.map((h, idx) => (
                  <li key={idx} className="text-xs">
                    {h.patientCpid ?? h.patient_cpid ?? "patient"} · {h.udi ?? "—"} · lot {h.lotNumber ?? h.lot_number ?? "—"} · {h.status ?? "—"}
                  </li>
                ))}
              </ul>
            )
          )}
        </div>
      </div>

      {/* Instrument sets */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sterile instrument sets (TUSO CSSD)</p>
        {sets.length === 0 && !setsUnavailable ? (
          <p className="text-xs text-muted-foreground">No instrument sets issued.</p>
        ) : sets.length === 0 && setsUnavailable ? (
          <p className="text-xs text-muted-foreground">Could not load instrument sets for this case.</p>
        ) : (
          <ul className="space-y-1">
            {sets.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-2 text-xs">
                <span className="font-medium">{s.set_name ?? s.tuso_instrument_set_id}</span>
                <span className={`rounded-full px-2 py-0.5 font-medium ${s.issue_status === "ISSUED" ? "bg-blue-100 text-blue-700" : s.issue_status === "RETURNED" ? "bg-slate-100 text-slate-600" : "bg-amber-100 text-amber-700"}`}>{s.issue_status}</span>
                {s.contaminated && <span className="rounded-full bg-red-100 px-2 py-0.5 font-medium text-red-700">contaminated</span>}
                {s.incomplete && <span className="rounded-full bg-red-100 px-2 py-0.5 font-medium text-red-700">incomplete</span>}
                {s.issue_status === "ISSUED" && s.tuso_instrument_set_id && (
                  <button type="button" disabled={busy} onClick={() => void returnSet(s.tuso_instrument_set_id!)} className="ml-auto rounded-lg border border-border bg-background px-2 py-1 font-medium hover:bg-card disabled:opacity-50">Return</button>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Instrument set id</span><input type="text" value={setId} onChange={(e) => setSetId(e.target.value)} className="w-48 rounded-lg border border-border bg-background px-2 py-1" data-testid="set-id" /></label>
          <button type="button" disabled={busy || !setId} onClick={() => void issueSet()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Issue set</button>
        </div>
      </div>

      {/* Controlled drugs */}
      <div className="rounded-lg border border-border p-3">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><Pill className="h-3.5 w-3.5" /> Controlled-drug register (two-person witness)</p>
        {drugs.length === 0 && !drugsUnavailable ? (
          <p className="text-xs text-muted-foreground">No controlled-drug entries.</p>
        ) : drugs.length === 0 && drugsUnavailable ? (
          <p className="text-xs text-muted-foreground">Could not load controlled-drug entries for this case.</p>
        ) : (
          <ul className="space-y-1">
            {drugs.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center gap-2 text-xs">
                <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-600">{d.action}</span>
                <span className="font-medium">{d.drug_name ?? d.item_code}</span>
                {d.quantity != null && <span>{d.quantity}{d.unit ? ` ${d.unit}` : ""}</span>}
                {d.running_balance != null && <span className="text-muted-foreground">bal {d.running_balance}</span>}
                {d.witness_provider && <span className="text-muted-foreground">witness: {d.witness_provider}</span>}
                <span className={`rounded-full px-2 py-0.5 font-medium ${d.status === "RECORDED" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{d.status}</span>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Action</span>
            <select value={cdAction} onChange={(e) => setCdAction(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
              {["ISSUE", "ADMINISTER", "WASTE", "DISCARD"].map((a) => <option key={a}>{a}</option>)}
            </select>
          </label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Item code</span><input type="text" value={cdItem} onChange={(e) => setCdItem(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" data-testid="cd-item" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Qty</span><input type="number" min={0} value={cdQty} onChange={(e) => setCdQty(e.target.value)} className="w-16 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Witness {witnessRequired ? "(required)" : ""}</span><input type="text" value={cdWitness} onChange={(e) => setCdWitness(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" data-testid="cd-witness" /></label>
          <button type="button" disabled={busy || !cdItem || (witnessRequired && !cdWitness)} onClick={() => void recordDrug()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record</button>
        </div>
        {witnessRequired && <p className="mt-1 text-[11px] text-muted-foreground">Administering, wasting or discarding a controlled drug requires a second-person witness.</p>}
      </div>
    </section>
  );
}
