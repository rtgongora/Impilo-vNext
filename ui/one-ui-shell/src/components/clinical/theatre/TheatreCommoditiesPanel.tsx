"use client";

/**
 * Theatre commodities / traceability panel (Lane 2). Three real registers on one case:
 *  - Implants: UDI / serial / lot → patient, with recall trace (inventory-backed).
 *  - Sterile instrument sets: TUSO CSSD issue / return.
 *  - Controlled drugs: two-person-witness register (inventory register-backed).
 * All rows are read from the service; statuses (IMPLANTED/UNAVAILABLE, running balance, etc.) are the
 * service's truth. No mocks; empty registers show an honest empty state.
 * Binds: /internal/v1/theatre/cases/{id}/{implants|instrument-sets|controlled-drugs}, /theatre/implants/recall
 */

import { useCallback, useEffect, useState } from "react";
import { Boxes, Loader2, RefreshCw, ScanLine, Pill } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface Implant { id?: string; udi?: string | null; serial_number?: string | null; lot_number?: string | null; device_type?: string | null; body_site?: string | null; laterality?: string | null; status?: string; }
interface InstrumentSet { id?: string; tuso_instrument_set_id?: string; set_name?: string | null; issue_status?: string; sterile_until?: string | null; contaminated?: boolean; incomplete?: boolean; }
interface ControlledDrug { id?: string; item_code?: string; drug_name?: string | null; action?: string; quantity?: number | null; unit?: string | null; running_balance?: number | null; witness_provider?: string | null; status?: string; }

function unwrapList<T>(res: unknown): T[] {
  const o = res as { data?: T[] };
  const v = o && o.data !== undefined ? o.data : res;
  return Array.isArray(v) ? (v as T[]) : [];
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Action failed. Please try again.";
}

export function TheatreCommoditiesPanel({ caseId }: { caseId: string }) {
  const [implants, setImplants] = useState<Implant[]>([]);
  const [sets, setSets] = useState<InstrumentSet[]>([]);
  const [drugs, setDrugs] = useState<ControlledDrug[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  // implant form
  const [udi, setUdi] = useState("");
  const [serial, setSerial] = useState("");
  const [lot, setLot] = useState("");
  const [bodySite, setBodySite] = useState("");
  // instrument set form
  const [setId, setSetId] = useState("");
  // controlled drug form
  const [cdAction, setCdAction] = useState("ADMINISTER");
  const [cdItem, setCdItem] = useState("");
  const [cdWitness, setCdWitness] = useState("");
  const [cdQty, setCdQty] = useState("1");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [i, s, d] = await Promise.all([
        apiClient.get(`/internal/v1/theatre/cases/${caseId}/implants`).catch(() => []),
        apiClient.get(`/internal/v1/theatre/cases/${caseId}/instrument-sets`).catch(() => []),
        apiClient.get(`/internal/v1/theatre/cases/${caseId}/controlled-drugs`).catch(() => []),
      ]);
      setImplants(unwrapList<Implant>(i));
      setSets(unwrapList<InstrumentSet>(s));
      setDrugs(unwrapList<ControlledDrug>(d));
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

  const recordImplant = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/implants`, { ...(udi ? { udi } : {}), ...(serial ? { serialNumber: serial } : {}), ...(lot ? { lotNumber: lot } : {}), ...(bodySite ? { bodySite } : {}) }), "Implant recorded (UDI/serial → patient).").then(() => { setUdi(""); setSerial(""); setLot(""); setBodySite(""); });
  const issueSet = () => act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/instrument-sets/issue`, { instrumentSetId: setId }), "Instrument set issued (TUSO CSSD).").then(() => setSetId(""));
  const returnSet = (id: string) => act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/instrument-sets/return`, { instrumentSetId: id }), "Instrument set returned to CSSD.");
  const recordDrug = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/controlled-drugs`, { action: cdAction, itemCode: cdItem, quantity: Number(cdQty), ...(cdWitness ? { witnessProvider: cdWitness } : {}) }), "Controlled-drug register entry recorded.").then(() => { setCdItem(""); setCdWitness(""); });

  const witnessRequired = ["ADMINISTER", "WASTE", "DISCARD"].includes(cdAction);

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="commodities-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><Boxes className="h-4 w-4" /> Commodities &amp; traceability</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>
      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}
      {loading && <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading registers…</p>}

      {/* Implants */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><ScanLine className="h-3.5 w-3.5" /> Implants (UDI / serial / lot)</p>
        {implants.length === 0 ? (
          <p className="text-xs text-muted-foreground">No implants recorded.</p>
        ) : (
          <ul className="space-y-1">
            {implants.map((im) => (
              <li key={im.id} className="flex flex-wrap items-center gap-2 text-xs">
                <span className="font-medium">{im.udi ?? im.serial_number}</span>
                {im.device_type && <span className="text-muted-foreground">{im.device_type}</span>}
                {im.body_site && <span className="text-muted-foreground">{im.body_site}{im.laterality ? ` (${im.laterality})` : ""}</span>}
                {im.lot_number && <span className="text-muted-foreground">lot {im.lot_number}</span>}
                <span className={`rounded-full px-2 py-0.5 font-medium ${im.status === "IMPLANTED" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{im.status}</span>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">UDI</span><input type="text" value={udi} onChange={(e) => setUdi(e.target.value)} className="w-40 rounded-lg border border-border bg-background px-2 py-1" data-testid="implant-udi" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Serial</span><input type="text" value={serial} onChange={(e) => setSerial(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Lot</span><input type="text" value={lot} onChange={(e) => setLot(e.target.value)} className="w-24 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Body site</span><input type="text" value={bodySite} onChange={(e) => setBodySite(e.target.value)} className="w-32 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <button type="button" disabled={busy || (!udi && !serial)} onClick={() => void recordImplant()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record implant</button>
        </div>
        <p className="mt-1 text-[11px] text-muted-foreground">A UDI or serial is required. Recall trace is available service-side via /theatre/implants/recall.</p>
      </div>

      {/* Instrument sets */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sterile instrument sets (TUSO CSSD)</p>
        {sets.length === 0 ? (
          <p className="text-xs text-muted-foreground">No instrument sets issued.</p>
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
        {drugs.length === 0 ? (
          <p className="text-xs text-muted-foreground">No controlled-drug entries.</p>
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
