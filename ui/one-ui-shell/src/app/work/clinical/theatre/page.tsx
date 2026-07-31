"use client";

/**
 * Theatre & perioperative board — provider queue. Real theatre cases via the experience-BFF
 * (GET /internal/v1/theatre/queue). Cases arrive from an OROS PROCEDURE order; triage drives the
 * queue order; readiness and the WHO checklist gate the start. No fake availability or readiness.
 * Route: /work/clinical/theatre
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Stethoscope, Loader2, RefreshCw, Plus, Siren } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface TheatreCase {
  id?: string;
  patient_id?: string;
  procedure_name?: string;
  status?: string;
  triage_priority?: string;
  scheduled_at?: string;
  theatre_room_id?: string;
  surgeon_id?: string;
  oros_order_id?: string;
}

function asArray(p: unknown): TheatreCase[] {
  return Array.isArray(p) ? (p as TheatreCase[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the theatre list. Please try again.";
}

const TRIAGE_TONE: Record<string, string> = {
  IMMEDIATE: "bg-red-100 text-red-700",
  EMERGENCY: "bg-orange-100 text-orange-700",
  URGENT: "bg-amber-100 text-amber-700",
  ELECTIVE: "bg-slate-100 text-slate-600",
  DAY_CASE: "bg-blue-100 text-blue-700",
};

export default function TheatreBoardPage() {
  const [data, setData] = useState<TheatreCase[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showIntake, setShowIntake] = useState(false);
  const [intaking, setIntaking] = useState(false);
  const [intakeMsg, setIntakeMsg] = useState<string | null>(null);
  const [form, setForm] = useState({ patientId: "", procedureName: "", procedureCode: "", surgeonId: "", triagePriority: "ELECTIVE" });
  const [showEmergency, setShowEmergency] = useState(false);
  const [activating, setActivating] = useState(false);
  const [emergencyMsg, setEmergencyMsg] = useState<string | null>(null);
  const [emForm, setEmForm] = useState({ patientId: "", procedureName: "", surgeonId: "", emergencyReason: "" });
  const router = useRouter();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/theatre/queue");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const intake = useCallback(async () => {
    if (!form.patientId || !form.procedureName) return;
    setIntaking(true);
    setIntakeMsg(null);
    try {
      await apiClient.post<TheatreCase>("/internal/v1/theatre/cases", {
        patientId: form.patientId,
        procedureName: form.procedureName,
        procedureCode: form.procedureCode || undefined,
        surgeonId: form.surgeonId || undefined,
        triagePriority: form.triagePriority,
      });
      setIntakeMsg("Theatre case created from the procedure order. It is now on the board.");
      setShowIntake(false);
      void load();
    } catch (e) {
      setIntakeMsg(errMessage(e));
    } finally {
      setIntaking(false);
    }
  }, [form, load]);

  const activateEmergency = useCallback(async () => {
    if (!emForm.patientId || !emForm.procedureName) return;
    setActivating(true);
    setEmergencyMsg(null);
    try {
      const res = await apiClient.post<unknown>("/internal/v1/theatre/cases/emergency", {
        patientId: emForm.patientId,
        procedureName: emForm.procedureName,
        surgeonId: emForm.surgeonId || undefined,
        emergencyReason: emForm.emergencyReason || undefined,
      });
      const o = res as { data?: { id?: string } };
      const newId = (o?.data?.id) ?? (res as { id?: string })?.id;
      if (newId) {
        router.push(`/work/clinical/theatre/${newId}`);
      } else {
        setEmergencyMsg("Emergency case activated. Refresh the board to see it.");
        setShowEmergency(false);
        void load();
      }
    } catch (e) {
      setEmergencyMsg(errMessage(e));
    } finally {
      setActivating(false);
    }
  }, [emForm, router, load]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell title="Theatre list" subtitle="Triaged perioperative cases — readiness and the WHO checklist gate the start" icon={<Stethoscope className="h-5 w-5" />}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
              <RefreshCw className="h-3.5 w-3.5" /> Refresh
            </button>
            <Link href="/work/clinical/theatre/board" className="rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background" data-testid="theatre-board-link">
              Readiness board
            </Link>
            <Link href="/work/clinical/theatre/referrals" className="rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background" data-testid="theatre-referrals-link">
              Surgical referrals
            </Link>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => setShowEmergency((v) => !v)} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-red-50 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100" data-testid="emergency-activate-toggle">
              <Siren className="h-3.5 w-3.5" /> Emergency activation
            </button>
            <button type="button" onClick={() => setShowIntake((v) => !v)} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90">
              <Plus className="h-3.5 w-3.5" /> New theatre case
            </button>
          </div>
        </div>

        {showEmergency && (
          <div className="mb-5 rounded-xl border border-red-200 bg-red-50/50 p-4" data-testid="emergency-activate-form">
            <h3 className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-red-700"><Siren className="h-4 w-4" /> Emergency surgery — rapid activation</h3>
            <p className="mb-3 text-xs text-muted-foreground">Forces EMERGENCY triage, arms the audited override and records a minimal safe pre-op. Life-saving surgery is never blocked by process.</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Patient (CPID)</span><input type="text" value={emForm.patientId} onChange={(e) => setEmForm({ ...emForm, patientId: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="em-patient" /></label>
              <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Procedure</span><input type="text" value={emForm.procedureName} onChange={(e) => setEmForm({ ...emForm, procedureName: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="em-procedure" /></label>
              <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Surgeon (provider id)</span><input type="text" value={emForm.surgeonId} onChange={(e) => setEmForm({ ...emForm, surgeonId: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
              <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Indication / reason</span><input type="text" value={emForm.emergencyReason} onChange={(e) => setEmForm({ ...emForm, emergencyReason: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
            </div>
            {emergencyMsg && <p className="mt-3 rounded-lg border border-red-200 bg-white p-3 text-sm text-red-700">{emergencyMsg}</p>}
            <button type="button" disabled={activating || !emForm.patientId || !emForm.procedureName} onClick={() => void activateEmergency()} className="mt-3 rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50" data-testid="em-submit">
              {activating ? "Activating…" : "Activate emergency case"}
            </button>
          </div>
        )}

        {showIntake && (
          <div className="mb-5 rounded-xl border border-border bg-card p-4">
            <h3 className="mb-3 text-sm font-semibold">New theatre case (from a PROCEDURE order)</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Patient (CPID)</span>
                <input type="text" value={form.patientId} onChange={(e) => setForm({ ...form, patientId: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Procedure</span>
                <input type="text" value={form.procedureName} onChange={(e) => setForm({ ...form, procedureName: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Procedure code (optional)</span>
                <input type="text" value={form.procedureCode} onChange={(e) => setForm({ ...form, procedureCode: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Surgeon (provider id)</span>
                <input type="text" value={form.surgeonId} onChange={(e) => setForm({ ...form, surgeonId: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Triage priority</span>
                <select value={form.triagePriority} onChange={(e) => setForm({ ...form, triagePriority: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                  {["IMMEDIATE", "EMERGENCY", "URGENT", "ELECTIVE", "DAY_CASE"].map((p) => <option key={p}>{p}</option>)}
                </select>
              </label>
            </div>
            {intakeMsg && <p className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{intakeMsg}</p>}
            <button type="button" disabled={intaking || !form.patientId || !form.procedureName} onClick={() => void intake()} className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">
              {intaking ? "Creating…" : "Create theatre case"}
            </button>
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading the theatre list…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <Stethoscope className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No theatre cases on the board for this facility.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Patient</th>
                  <th className="px-4 py-3">Procedure</th>
                  <th className="px-4 py-3">Triage</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Theatre room</th>
                  <th className="px-4 py-3">Surgeon</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((c) => (
                  <tr key={c.id} className="hover:bg-background/80">
                    <td className="px-4 py-3">
                      <Link href={`/work/clinical/theatre/${c.id}`} className="font-medium text-primary hover:underline">
                        {c.patient_id ?? "—"}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.procedure_name ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TRIAGE_TONE[c.triage_priority ?? "ELECTIVE"] ?? "bg-slate-100 text-slate-600"}`}>
                        {c.triage_priority ?? "ELECTIVE"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.status ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{c.theatre_room_id ? c.theatre_room_id.slice(0, 8) : "Not assigned"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{c.surgeon_id ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/theatre" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
