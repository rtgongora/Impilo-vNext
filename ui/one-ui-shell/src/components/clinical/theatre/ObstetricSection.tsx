"use client";

/**
 * Obstetric emergency C-section section (Lane 1). One procedure episode for the MOTHER; maternal+fetal
 * context, a neonatal-team page, the delivery, and the neonatal handover. The baby carries a PROVISIONAL
 * identity (CPID) minted UPSTREAM in VITO — this UI never mints identity; it accepts the provisional CPID
 * and DISPLAYS the mother↔baby linkage the service returns. Shown only for obstetric cases.
 * Binds: POST /internal/v1/theatre/cases/{id}/obstetric/{context|neonatal-alert|delivery|neonatal-handover}
 */

import { useState } from "react";
import { Baby } from "lucide-react";
import { apiClient } from "@/lib/api-client";

function unwrap<T>(res: unknown): T {
  const o = res as { data?: T };
  return (o && o.data !== undefined ? o.data : res) as T;
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Obstetric action failed. Please try again.";
}

interface DeliveryResult {
  mother_cpid?: string;
  baby_cpid?: string;
  delivery?: { outcome?: string; apgar_5?: number | null; sex?: string | null; birth_order?: number };
}

export function ObstetricSection({ caseId }: { caseId: string }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  // context
  const [gestationWeeks, setGestationWeeks] = useState("38");
  const [urgency, setUrgency] = useState("CATEGORY_1");
  const [indication, setIndication] = useState("");
  const [fetalHeartRate, setFetalHeartRate] = useState("");
  // delivery
  const [babyCpid, setBabyCpid] = useState("");
  const [sex, setSex] = useState("");
  const [apgar5, setApgar5] = useState("");
  const [destination, setDestination] = useState("POSTNATAL");
  const [delivery, setDelivery] = useState<DeliveryResult | null>(null);
  const [neonatalTeam, setNeonatalTeam] = useState<string | null>(null);
  const [handover, setHandover] = useState<string | null>(null);

  const post = async (action: string, body: Record<string, unknown>, ok: string, after?: (data: unknown) => void) => {
    setBusy(true);
    setMsg(null);
    try {
      const res = await apiClient.post(`/internal/v1/theatre/cases/${caseId}/obstetric/${action}`, body);
      after?.(unwrap(res));
      setMsg(ok);
    } catch (e) {
      setMsg(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="rounded-xl border border-pink-200 bg-pink-50/30 p-4" data-testid="obstetric-section">
      <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-pink-800"><Baby className="h-4 w-4" /> Obstetric emergency caesarean</h3>
      {msg && <p className="mb-2 rounded-lg border border-border bg-card p-2 text-xs">{msg}</p>}

      {/* Maternal + fetal context */}
      <div className="mb-3 rounded-lg border border-border bg-card p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Maternal &amp; fetal context</p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Gestation (wks)</span><input type="number" value={gestationWeeks} onChange={(e) => setGestationWeeks(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Urgency</span>
            <select value={urgency} onChange={(e) => setUrgency(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
              {["CATEGORY_1", "CATEGORY_2", "CATEGORY_3", "CATEGORY_4"].map((u) => <option key={u}>{u}</option>)}
            </select>
          </label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">FHR</span><input type="number" value={fetalHeartRate} onChange={(e) => setFetalHeartRate(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Indication</span><input type="text" value={indication} onChange={(e) => setIndication(e.target.value)} className="w-40 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <button type="button" disabled={busy} onClick={() => void post("context", { gestationWeeks: Number(gestationWeeks), urgency, indication, ...(fetalHeartRate ? { fetalHeartRate: Number(fetalHeartRate) } : {}) }, "Maternal & fetal context recorded.")} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record context</button>
        </div>
      </div>

      {/* Neonatal team page */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <button type="button" disabled={busy} onClick={() => void post("neonatal-alert", { urgency, expectedNeonates: 1 }, "Neonatal team paged.", (d) => setNeonatalTeam((d as { neonatal_team?: string }).neonatal_team ?? "PAGED"))} className="rounded-lg border border-pink-300 bg-pink-100 px-3 py-1.5 text-xs font-medium text-pink-900 hover:bg-pink-200 disabled:opacity-50">Page neonatal team</button>
        {neonatalTeam && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700" data-testid="neonatal-team-status">Neonatal team: {neonatalTeam}</span>}
      </div>

      {/* Delivery */}
      <div className="mb-3 rounded-lg border border-border bg-card p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Delivery</p>
        <p className="mb-2 text-[11px] text-muted-foreground">The neonate&apos;s provisional identity (CPID) is minted upstream in VITO — enter it here; this screen never mints identity.</p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Baby provisional CPID (required)</span><input type="text" value={babyCpid} onChange={(e) => setBabyCpid(e.target.value)} className="w-52 rounded-lg border border-border bg-background px-2 py-1" data-testid="baby-cpid" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Sex</span>
            <select value={sex} onChange={(e) => setSex(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
              {["", "MALE", "FEMALE", "INDETERMINATE"].map((s) => <option key={s} value={s}>{s || "—"}</option>)}
            </select>
          </label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Apgar 5</span><input type="number" min={0} max={10} value={apgar5} onChange={(e) => setApgar5(e.target.value)} className="w-16 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <button type="button" disabled={busy || !babyCpid} onClick={() => void post("delivery", { babyCpid, ...(sex ? { sex } : {}), ...(apgar5 ? { apgar5: Number(apgar5) } : {}) }, "Delivery recorded.", (d) => setDelivery(d as DeliveryResult))} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Record delivery</button>
        </div>
        {delivery && (
          <p className="mt-2 rounded-lg border border-emerald-200 bg-emerald-50 p-2 text-xs text-emerald-800" data-testid="delivery-linkage">
            Linked: mother {delivery.mother_cpid?.slice(0, 12)} ↔ baby {delivery.baby_cpid?.slice(0, 12)}
            {delivery.delivery?.outcome ? ` · ${delivery.delivery.outcome}` : ""}
            {delivery.delivery?.apgar_5 != null ? ` · Apgar-5 ${delivery.delivery.apgar_5}` : ""}
          </p>
        )}
      </div>

      {/* Neonatal handover */}
      <div className="flex flex-wrap items-end gap-2">
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Neonatal destination</span>
          <select value={destination} onChange={(e) => setDestination(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
            {["POSTNATAL", "NEONATAL", "NICU", "SCBU", "MORTUARY"].map((d) => <option key={d}>{d}</option>)}
          </select>
        </label>
        <button type="button" disabled={busy || !babyCpid} onClick={() => void post("neonatal-handover", { babyCpid, destination }, "Neonatal handover recorded.", (d) => setHandover((d as { destination?: string }).destination ?? destination))} className="rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-card disabled:opacity-50">Handover neonate</button>
        {handover && <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600" data-testid="handover-status">Handed over → {handover}</span>}
      </div>
    </section>
  );
}
