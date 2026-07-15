"use client";

/**
 * Theatre PACU recovery-depth panel. Surfaces the Wave-4 inpatient PACU depth on the perioperative
 * surface: Aldrete-scored recovery observations, the discharge-readiness gate the score derives, PACU
 * escalation, and the gated PACU discharge decision. Recovery disposition (incl. DEATH → PCT) is
 * retained here too, so disposition and the readiness gate live together.
 * All rows and the readiness band are the service's truth (AnaesthesiaScoringEngine); ≥9 = LOW/ready,
 * ≥7 = MODERATE, else HIGH. No mocks; an unscored patient shows an honest UNSCORED gate.
 * Binds: /internal/v1/theatre/cases/{id}/pacu/{observations|readiness|escalate|discharge|disposition}
 */

import { useCallback, useEffect, useState } from "react";
import { HeartPulse, Loader2, RefreshCw, TriangleAlert, LogOut } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { SCORE_TOOL_LABELS } from "@/lib/anaesthesia-scoring";

interface PacuObservation {
  observation_id?: string;
  seq?: number;
  observed_at?: string;
  aldrete_score?: number | null;
  discharge_readiness_band?: string | null;
  spo2?: number | null;
  systolic_bp?: number | null;
  diastolic_bp?: number | null;
  heart_rate?: number | null;
  consciousness?: string | null;
  pain_score?: number | null;
  recorded_by?: string | null;
}
interface PacuReadiness {
  ready?: boolean;
  band?: string;
  aldrete_score?: number | null;
  interpretation?: string;
}

function unwrap<T>(res: unknown, fallback: T): T {
  const o = res as { data?: T };
  return o && o.data !== undefined ? (o.data as T) : (res as T) ?? fallback;
}
function unwrapList<T>(res: unknown): T[] {
  const o = res as { data?: T[] };
  const v = o && o.data !== undefined ? o.data : res;
  return Array.isArray(v) ? (v as T[]) : [];
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status === 409) return "Blocked: recovery is not complete for discharge (override required).";
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Action failed. Please try again.";
}

const BAND_STYLE: Record<string, string> = {
  LOW: "bg-emerald-100 text-emerald-700",
  MODERATE: "bg-amber-100 text-amber-700",
  HIGH: "bg-red-100 text-red-700",
  UNSCORED: "bg-slate-100 text-slate-600",
};

export function TheatrePacuPanel({ caseId }: { caseId: string }) {
  const [obs, setObs] = useState<PacuObservation[]>([]);
  const [readiness, setReadiness] = useState<PacuReadiness | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  // observation form
  const [aldrete, setAldrete] = useState("");
  const [painScore, setPainScore] = useState("");
  const [spo2, setSpo2] = useState("");
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [consciousness, setConsciousness] = useState("");
  const [notes, setNotes] = useState("");
  // escalate form
  const [escalateReason, setEscalateReason] = useState("");
  // discharge form
  const [destination, setDestination] = useState("WARD");
  const [override, setOverride] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [o, r] = await Promise.all([
        apiClient.get(`/internal/v1/theatre/cases/${caseId}/pacu/observations`).catch(() => []),
        apiClient.get(`/internal/v1/theatre/cases/${caseId}/pacu/readiness`).catch(() => null),
      ]);
      setObs(unwrapList<PacuObservation>(o));
      setReadiness(r ? unwrap<PacuReadiness>(r, {}) : null);
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

  const num = (v: string) => (v.trim() === "" ? undefined : Number(v));

  const recordObservation = () =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${caseId}/pacu/observations`, {
          ...(aldrete.trim() !== "" ? { aldreteScore: Number(aldrete) } : {}),
          ...(painScore.trim() !== "" ? { painScore: Number(painScore) } : {}),
          ...(num(spo2) !== undefined ? { spo2: num(spo2) } : {}),
          ...(num(systolic) !== undefined ? { systolicBp: num(systolic) } : {}),
          ...(num(diastolic) !== undefined ? { diastolicBp: num(diastolic) } : {}),
          ...(num(heartRate) !== undefined ? { heartRate: num(heartRate) } : {}),
          ...(consciousness ? { consciousness } : {}),
          ...(notes ? { notes } : {}),
        }),
      "PACU observation recorded (Aldrete scored).",
    ).then(() => {
      setAldrete("");
      setPainScore("");
      setSpo2("");
      setSystolic("");
      setDiastolic("");
      setHeartRate("");
      setConsciousness("");
      setNotes("");
    });

  const escalate = () =>
    act(
      () => apiClient.post(`/internal/v1/theatre/cases/${caseId}/pacu/escalate`, { reason: escalateReason }),
      "PACU escalation raised.",
    ).then(() => setEscalateReason(""));

  const discharge = () =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${caseId}/pacu/discharge`, {
          destination,
          ...(override ? { readinessOverride: true } : {}),
        }),
      `PACU discharge recorded (${destination}).`,
    );

  const disposition = (d: string) =>
    act(
      () => apiClient.post(`/internal/v1/theatre/cases/${caseId}/pacu/disposition`, { disposition: d }),
      `Recovery disposition recorded (${d}).`,
    );

  const band = readiness?.band ?? "UNSCORED";
  const ready = readiness?.ready === true;

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="pacu-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><HeartPulse className="h-4 w-4" /> PACU recovery ({SCORE_TOOL_LABELS.ALDRETE})</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>
      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}
      {loading && <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading recovery record…</p>}

      {/* Discharge-readiness gate (Aldrete-derived) */}
      <div
        className={`mb-4 flex flex-wrap items-center gap-2 rounded-lg border p-3 text-xs ${ready ? "border-emerald-200 bg-emerald-50/50" : "border-amber-200 bg-amber-50/40"}`}
        data-testid={ready ? "pacu-readiness-ready" : "pacu-readiness-blocked"}
      >
        <span className={`rounded-full px-2 py-0.5 font-medium ${BAND_STYLE[band] ?? BAND_STYLE.UNSCORED}`}>{band}</span>
        {readiness?.aldrete_score != null && <span className="font-medium">Aldrete {readiness.aldrete_score}</span>}
        <span className="text-muted-foreground">{readiness?.interpretation ?? "No recovery score recorded yet."}</span>
      </div>

      {/* Observation timeline */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Recovery observations</p>
        {obs.length === 0 ? (
          <p className="text-xs text-muted-foreground">No PACU observations recorded.</p>
        ) : (
          <ul className="space-y-1">
            {obs.map((o) => (
              <li key={o.observation_id ?? o.seq} className="flex flex-wrap items-center gap-2 text-xs" data-testid="pacu-observation-row">
                {o.seq != null && <span className="text-muted-foreground">#{o.seq}</span>}
                {o.aldrete_score != null && <span className="font-medium">Aldrete {o.aldrete_score}</span>}
                {o.discharge_readiness_band && <span className={`rounded-full px-2 py-0.5 font-medium ${BAND_STYLE[o.discharge_readiness_band] ?? BAND_STYLE.UNSCORED}`}>{o.discharge_readiness_band}</span>}
                {o.spo2 != null && <span className="text-muted-foreground">SpO₂ {o.spo2}%</span>}
                {o.systolic_bp != null && <span className="text-muted-foreground">BP {o.systolic_bp}/{o.diastolic_bp ?? "—"}</span>}
                {o.heart_rate != null && <span className="text-muted-foreground">HR {o.heart_rate}</span>}
                {o.pain_score != null && <span className="text-muted-foreground">pain {o.pain_score}</span>}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Aldrete (0–10)</span><input type="number" min={0} max={10} value={aldrete} onChange={(e) => setAldrete(e.target.value)} className="w-24 rounded-lg border border-border bg-background px-2 py-1" data-testid="pacu-aldrete" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Pain (0–10)</span><input type="number" min={0} max={10} value={painScore} onChange={(e) => setPainScore(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">SpO₂ %</span><input type="number" min={0} max={100} value={spo2} onChange={(e) => setSpo2(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Systolic</span><input type="number" value={systolic} onChange={(e) => setSystolic(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Diastolic</span><input type="number" value={diastolic} onChange={(e) => setDiastolic(e.target.value)} className="w-20 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">HR</span><input type="number" value={heartRate} onChange={(e) => setHeartRate(e.target.value)} className="w-16 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Consciousness</span><input type="text" value={consciousness} onChange={(e) => setConsciousness(e.target.value)} className="w-28 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Recovery notes</span><input type="text" value={notes} onChange={(e) => setNotes(e.target.value)} className="w-40 rounded-lg border border-border bg-background px-2 py-1" /></label>
          <button type="button" disabled={busy || aldrete.trim() === ""} onClick={() => void recordObservation()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50" data-testid="pacu-record">Record observation</button>
        </div>
        <p className="mt-1 text-[11px] text-muted-foreground">An Aldrete score is required. The readiness band above is derived server-side from the latest score.</p>
      </div>

      {/* Gated PACU discharge */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><LogOut className="h-3.5 w-3.5" /> PACU discharge (Aldrete-gated)</p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Destination</span>
            <select value={destination} onChange={(e) => setDestination(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
              {["WARD", "ICU", "HDU", "HOME"].map((d) => <option key={d}>{d}</option>)}
            </select>
          </label>
          <label className="flex items-center gap-1.5 text-xs"><input type="checkbox" checked={override} onChange={(e) => setOverride(e.target.checked)} data-testid="pacu-override" /> Override readiness</label>
          <button type="button" disabled={busy} onClick={() => void discharge()} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50" data-testid="pacu-discharge">Discharge from PACU</button>
        </div>
        {!ready && !override && <p className="mt-1 text-[11px] text-amber-700">Recovery is not yet complete — discharge will be blocked unless readiness is overridden with a reason recorded.</p>}
      </div>

      {/* Escalate */}
      <div className="mb-4 rounded-lg border border-border p-3">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><TriangleAlert className="h-3.5 w-3.5" /> Escalate recovery concern</p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Reason</span><input type="text" value={escalateReason} onChange={(e) => setEscalateReason(e.target.value)} className="w-64 rounded-lg border border-border bg-background px-2 py-1" data-testid="pacu-escalate-reason" /></label>
          <button type="button" disabled={busy || !escalateReason} onClick={() => void escalate()} className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-50">Escalate to anaesthetist</button>
        </div>
      </div>

      {/* Recovery disposition (incl. death → PCT) */}
      <div className="rounded-lg border border-border p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Recovery disposition</p>
        <div className="flex flex-wrap gap-2">
          {["WARD", "ICU", "DISCHARGE", "TRANSFER"].map((d) => (
            <button key={d} type="button" disabled={busy} onClick={() => void disposition(d)} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium hover:bg-card disabled:opacity-50">{d}</button>
          ))}
          <button type="button" disabled={busy} onClick={() => void disposition("DEATH")} className="rounded-lg border border-slate-800 bg-slate-800 px-3 py-1.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50">Death (routes to PCT)</button>
        </div>
      </div>
    </section>
  );
}
